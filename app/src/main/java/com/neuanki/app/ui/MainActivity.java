package com.neuanki.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.neuanki.app.R;
import com.neuanki.app.db.Deck;
import com.neuanki.app.db.Store;
import com.neuanki.app.imp.ApkgImporter;
import com.neuanki.app.util.Util;
import com.neuanki.app.widget.NeumorphButton;
import com.neuanki.app.widget.NeumorphIconButton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** صفحهٔ اصلی — لیست دک‌ها با شمارنده‌ها */
public class MainActivity extends Base implements DeckAdapter.Listener {

    private DeckAdapter adapter;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private FrameLayout rootMain;
    private LinearLayout emptyState;
    private NeumorphIconButton fab;
    private RecyclerView rvDecks;

    private ActivityResultLauncher<Intent> importPicker;
    private android.app.AlertDialog progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootMain = findViewById(R.id.rootMain);
        emptyState = findViewById(R.id.emptyState);
        rvDecks = findViewById(R.id.rvDecks);
        fab = findViewById(R.id.fab);
        NeumorphIconButton btnBrowser = findViewById(R.id.btnBrowser);
        NeumorphIconButton btnStats = findViewById(R.id.btnStats);
        NeumorphIconButton btnSettings = findViewById(R.id.btnSettings);
        NeumorphButton btnEmptyImport = findViewById(R.id.btnEmptyImport);

        adapter = new DeckAdapter(this);
        rvDecks.setLayoutManager(new LinearLayoutManager(this));
        rvDecks.setAdapter(adapter);

        btnBrowser.setOnClickListener(v -> startActivity(new Intent(this, BrowserActivity.class)));
        btnStats.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnEmptyImport.setOnClickListener(v -> openPicker());
        fab.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, v);
            pm.getMenu().add(0, 1, 0, R.string.review_all);
            pm.getMenu().add(0, 2, 1, R.string.menu_import);
            pm.getMenu().add(0, 3, 2, R.string.menu_add_card);
            pm.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) {
                    Intent i = new Intent(this, ReviewerActivity.class);
                    i.putExtra(ReviewerActivity.EXTRA_DECK, -1L);
                    startActivity(i);
                } else if (id == 2) {
                    openPicker();
                } else {
                    NoteEditor.create(this, -1, this::refresh);
                }
                return true;
            });
            pm.show();
        });

        importPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            if (r.getResultCode() == RESULT_OK && r.getData() != null && r.getData().getData() != null) {
                runImport(r.getData().getData());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        exec.execute(() -> {
            List<Deck> decks = Store.decks(this);
            Map<Long, int[]> counts = new HashMap<>();
            long now = System.currentTimeMillis();
            long today = Util.dayNum(now);
            int newLimit = Store.prefInt(this, "newPerDay", 20);
            int revLimit = Store.prefInt(this, "revPerDay", 200);
            for (Deck d : decks) {
                Set<Long> ids = Store.deckAndChildren(this, d.id);
                counts.put(d.id, Store.counts(this, ids, now, today, newLimit, revLimit));
            }
            runOnUiThread(() -> {
                adapter.setData(decks, counts);
                boolean empty = decks.isEmpty();
                emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                rvDecks.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip", "application/octet-stream", "application/x-zip-compressed"});
        try {
            importPicker.launch(Intent.createChooser(i, getString(R.string.choose_file)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.import_err, Toast.LENGTH_SHORT).show();
        }
    }

    private void runImport(Uri uri) {
        if (progress == null) {
            progress = new android.app.AlertDialog.Builder(this)
                    .setMessage(R.string.importing)
                    .setCancelable(false)
                    .create();
        }
        progress.show();
        exec.execute(() -> {
            String resultMsg;
            try {
                ApkgImporter.Result res = ApkgImporter.importApkg(this, uri);
                resultMsg = getString(R.string.import_done_fmt,
                        Util.fa(res.decks), Util.fa(res.notes), Util.fa(res.cards), Util.fa(res.media));
            } catch (Exception e) {
                String m = e.getMessage() == null ? "" : e.getMessage();
                resultMsg = getString(m.contains("NEW_FORMAT") ? R.string.import_err_newformat : R.string.import_err);
            }
            final String msg = resultMsg;
            runOnUiThread(() -> {
                if (progress.isShowing()) progress.dismiss();
                new android.app.AlertDialog.Builder(this)
                        .setMessage(msg)
                        .setPositiveButton(R.string.ok, null)
                        .show();
                refresh();
            });
        });
    }

    // ------------------------------------------------- رویدادهای آداپتور

    @Override
    public void onOpen(Deck d) {
        Intent i = new Intent(this, ReviewerActivity.class);
        i.putExtra(ReviewerActivity.EXTRA_DECK, d.id);
        startActivity(i);
    }

    @Override
    public void onMenu(Deck d, View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, R.string.menu_add_card);
        pm.getMenu().add(0, 2, 1, R.string.browser);
        pm.getMenu().add(0, 3, 2, R.string.rename_deck);
        pm.getMenu().add(0, 4, 3, R.string.delete);
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                NoteEditor.create(this, d.id, this::refresh);
            } else if (id == 2) {
                Intent i = new Intent(this, BrowserActivity.class);
                i.putExtra(BrowserActivity.EXTRA_DECK, d.id);
                startActivity(i);
            } else if (id == 3) {
                renameDeck(d);
            } else if (id == 4) {
                deleteDeck(d);
            }
            return true;
        });
        pm.show();
    }

    private void renameDeck(Deck d) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(d.name);
        et.setTextColor(0xFFE7EAED);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        et.setPadding(p, p / 2, p, p / 2);
        et.setBackground(getDrawable(R.drawable.bg_inset));
        FrameLayout wrap = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(p, 0, p, 0);
        wrap.addView(et, lp);
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.rename_deck)
                .setView(wrap)
                .setPositiveButton(R.string.save, (dlg, w) -> {
                    String nm = et.getText().toString().trim();
                    if (!nm.isEmpty()) {
                        Store.renameDeck(this, d.id, nm);
                        Toast.makeText(this, R.string.deck_renamed, Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteDeck(Deck d) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_deck_title)
                .setMessage(getString(R.string.confirm_delete_deck_msg, d.name))
                .setPositiveButton(R.string.delete, (dlg, w) -> {
                    Store.deleteDeck(this, d.id);
                    Toast.makeText(this, R.string.deck_deleted, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
