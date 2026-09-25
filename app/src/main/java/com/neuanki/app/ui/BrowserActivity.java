package com.neuanki.app.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.neuanki.app.R;
import com.neuanki.app.db.Db;
import com.neuanki.app.db.Deck;
import com.neuanki.app.db.Store;
import com.neuanki.app.util.Util;
import com.neuanki.app.widget.NeumorphIconButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** مرورگر کارت‌ها — جستجو، ویرایش، تعلیق، دفن و حذف */
public class BrowserActivity extends Base {

    public static final String EXTRA_DECK = "deck";

    private static class Row {
        long cardId, noteId;
        String front = "", deck = "";
        int state;
        boolean suspended;
    }

    private EditText etSearch;
    private TextView tvBrCount, tvNoResults;
    private RecyclerView rvCards;
    private NeumorphIconButton btnDeckFilter;
    private final CardAdapter adapter = new CardAdapter();
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private long deckId = -1;
    private final Runnable searchRunnable = this::reload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        deckId = getIntent().getLongExtra(EXTRA_DECK, -1L);

        NeumorphIconButton btnBack = findViewById(R.id.btnBackBr);
        etSearch = findViewById(R.id.etSearch);
        btnDeckFilter = findViewById(R.id.btnDeckFilter);
        tvBrCount = findViewById(R.id.tvBrCount);
        tvNoResults = findViewById(R.id.tvNoResults);
        rvCards = findViewById(R.id.rvCards);

        rvCards.setLayoutManager(new LinearLayoutManager(this));
        rvCards.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnDeckFilter.setOnClickListener(v -> showDeckFilter());

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                ui.removeCallbacks(searchRunnable);
                ui.postDelayed(searchRunnable, 250);
            }
        });

        reload();
    }

    private void showDeckFilter() {
        PopupMenu pm = new PopupMenu(this, btnDeckFilter);
        List<Deck> decks = Store.decks(this);
        pm.getMenu().add(0, 0, 0, R.string.all_decks);
        for (int i = 0; i < decks.size(); i++) {
            pm.getMenu().add(0, i + 1, i + 1, decks.get(i).name);
        }
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                deckId = -1;
            } else {
                int idx = item.getItemId() - 1;
                if (idx >= 0 && idx < decks.size()) deckId = decks.get(idx).id;
            }
            reload();
            return true;
        });
        pm.show();
    }

    private void reload() {
        final String q = etSearch.getText().toString().trim().replace("'", "''");
        final long fid = deckId;
        exec.execute(() -> {
            List<Row> rows = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "SELECT c.id, c.nid, c.state, c.suspended, n.flds, d.name FROM cards c "
                            + "JOIN notes n ON c.nid=n.id JOIN decks d ON c.did=d.id WHERE 1=1");
            if (fid > 0) {
                Set<Long> ids = Store.deckAndChildren(this, fid);
                if (ids.isEmpty()) ids = new HashSet<>();
                ids.add(fid);
                sql.append(" AND c.did IN (").append(Store.joinIds(ids)).append(")");
            }
            if (!q.isEmpty()) {
                sql.append(" AND n.flds LIKE '%").append(q).append("%'");
            }
            sql.append(" ORDER BY n.mod DESC LIMIT 500");
            Cursor cur = Db.get(this).r().rawQuery(sql.toString(), null);
            while (cur.moveToNext()) {
                Row r = new Row();
                r.cardId = cur.getLong(0);
                r.noteId = cur.getLong(1);
                r.state = cur.getInt(2);
                r.suspended = cur.getInt(3) != 0;
                String flds = cur.getString(4);
                String[] arr = flds == null ? new String[0] : flds.split("\u001f", -1);
                r.front = Util.stripHtml(arr.length > 0 ? arr[0] : "");
                r.deck = cur.getString(5);
                rows.add(r);
            }
            cur.close();
            ui.post(() -> {
                adapter.setData(rows);
                tvBrCount.setText(getString(R.string.browser) + " — " + Util.fa(rows.size()));
                boolean empty = rows.isEmpty();
                tvNoResults.setVisibility(empty ? View.VISIBLE : View.GONE);
                rvCards.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private String stateLabel(Row r) {
        if (r.suspended) return getString(R.string.dist_susp);
        switch (r.state) {
            case 0: return getString(R.string.deck_new);
            case 1:
            case 3: return getString(R.string.deck_learn);
            default: return getString(R.string.deck_due);
        }
    }

    private int stateColor(Row r) {
        if (r.suspended) return 0xFF98A2AC;
        switch (r.state) {
            case 0: return 0xFF4FC3F7;
            case 1:
            case 3: return 0xFFEF5350;
            default: return 0xFF66BB6A;
        }
    }

    private class CardAdapter extends RecyclerView.Adapter<CardAdapter.VH> {

        private final List<Row> items = new ArrayList<>();

        void setData(List<Row> rows) {
            items.clear();
            items.addAll(rows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Row r = items.get(pos);
            h.front.setText(r.front.isEmpty() ? "—" : r.front);
            h.deck.setText(r.deck);
            h.state.setText(stateLabel(r));
            h.state.setTextColor(stateColor(r));
            h.root.setOnClickListener(v -> NoteEditor.edit(BrowserActivity.this, r.noteId, BrowserActivity.this::reload));
            h.root.setOnLongClickListener(v -> {
                showItemMenu(v, r);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            View root;
            TextView front, deck, state;

            VH(View v) {
                super(v);
                root = v;
                front = v.findViewById(R.id.tvCardFront);
                deck = v.findViewById(R.id.tvCardDeck);
                state = v.findViewById(R.id.tvCardState);
            }
        }
    }

    private void showItemMenu(View anchor, Row r) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, r.suspended ? R.string.unsuspend_card : R.string.suspend_card);
        pm.getMenu().add(0, 2, 1, R.string.bury_card);
        pm.getMenu().add(0, 3, 2, R.string.delete_card);
        pm.setOnMenuItemClickListener((MenuItem item) -> {
            int id = item.getItemId();
            if (id == 1) {
                Store.setSuspended(this, r.cardId, !r.suspended);
                Toast.makeText(this, r.suspended ? R.string.unsuspended_msg : R.string.suspended_msg, Toast.LENGTH_SHORT).show();
            } else if (id == 2) {
                Store.setBuried(this, r.cardId, Util.dayNum(System.currentTimeMillis()));
                Toast.makeText(this, R.string.buried_msg, Toast.LENGTH_SHORT).show();
            } else if (id == 3) {
                new android.app.AlertDialog.Builder(this)
                        .setMessage(R.string.confirm_delete_note)
                        .setPositiveButton(R.string.delete, (dlg, w) -> {
                            Store.deleteNote(this, r.noteId);
                            Toast.makeText(this, R.string.note_deleted, Toast.LENGTH_SHORT).show();
                            reload();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return true;
            }
            reload();
            return true;
        });
        pm.show();
    }
}
