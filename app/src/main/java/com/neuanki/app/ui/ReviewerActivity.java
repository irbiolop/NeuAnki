package com.neuanki.app.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.GestureDetectorCompat;

import com.neuanki.app.R;
import com.neuanki.app.db.Card;
import com.neuanki.app.db.Note;
import com.neuanki.app.db.Notetype;
import com.neuanki.app.db.Store;
import com.neuanki.app.imp.ApkgImporter;
import com.neuanki.app.sched.Scheduler;
import com.neuanki.app.tpl.TemplateEngine;
import com.neuanki.app.util.Util;
import com.neuanki.app.widget.NeumorphButton;
import com.neuanki.app.widget.NeumorphIconButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** صفحهٔ مرور کارت — تایپ، سوایپ، دکمه‌های چهارگانه، بازگردانی */
public class ReviewerActivity extends Base {

    public static final String EXTRA_DECK = "deck";

    private long deckId = -1;
    private Set<Long> dids = new HashSet<>();
    private String deckName = "";

    private WebView web;
    private EditText etType;
    private NeumorphButton btnShow, bAgain, bHard, bGood, bEasy, btnRecheck;
    private LinearLayout answerRow, finishedOverlay;
    private TextView tvNew, tvLearn, tvDue, tvDeckTitle, tvFinishedSub;
    private NeumorphIconButton btnUndo, btnMore, btnBack;

    private final List<Long> queue = new ArrayList<>();
    private Card current;
    private Note note;
    private Notetype nt;
    private boolean answered;
    private String typeTarget = "";
    private String typeField = null;
    private int sessionCount = 0;
    private long cardShownAt;
    private MediaPlayer player;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private GestureDetectorCompat detector;
    private String fontCss = "";
    private String baseUrl;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deckId = getIntent().getLongExtra(EXTRA_DECK, -1L);
        if (deckId == -1L) {
            for (com.neuanki.app.db.Deck d : Store.decks(this)) dids.add(d.id);
            deckName = getString(R.string.review_all);
        } else {
            dids = Store.deckAndChildren(this, deckId);
            com.neuanki.app.db.Deck d = Store.deck(this, deckId);
            deckName = d == null ? getString(R.string.review) : d.name;
        }

        // تنظیم زمان‌بند از پریف‌ها
        int[] steps = parseSteps(Store.pref(this, "steps", "1,10"));
        Scheduler.configure(steps, 1, 4);

        baseUrl = "file://" + ApkgImporter.mediaDir(this).getAbsolutePath() + "/";
        buildFontCss();

        setContentView(R.layout.activity_reviewer);
        web = findViewById(R.id.webCard);
        etType = findViewById(R.id.etType);
        btnShow = findViewById(R.id.btnShowAnswer);
        answerRow = findViewById(R.id.answerRow);
        finishedOverlay = findViewById(R.id.finishedOverlay);
        bAgain = findViewById(R.id.btnAgain);
        bHard = findViewById(R.id.btnHard);
        bGood = findViewById(R.id.btnGood);
        bEasy = findViewById(R.id.btnEasy);
        btnRecheck = findViewById(R.id.btnRecheck);
        tvNew = findViewById(R.id.tvCountNew);
        tvLearn = findViewById(R.id.tvCountLearn);
        tvDue = findViewById(R.id.tvCountDue);
        tvDeckTitle = findViewById(R.id.tvDeckTitle);
        tvFinishedSub = findViewById(R.id.tvFinishedSub);
        btnUndo = findViewById(R.id.btnUndo);
        btnMore = findViewById(R.id.btnMore);
        btnBack = findViewById(R.id.btnBack);

        tvDeckTitle.setText(deckName);

        web.setBackgroundColor(Color.TRANSPARENT);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith("sound://")) {
                    String name = Uri.decode(url.substring("sound://".length()));
                    playSound(name);
                    return true;
                }
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });

        detector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!answered && current != null) reveal();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (!answered || current == null) return false;
                float density = getResources().getDisplayMetrics().density;
                float min = 350 * density;
                if (Math.abs(vx) > Math.abs(vy) * 1.4f && Math.abs(vx) > min) {
                    answer(vx > 0 ? Scheduler.GOOD : Scheduler.AGAIN);
                    return true;
                }
                if (Math.abs(vy) > Math.abs(vx) * 1.4f && Math.abs(vy) > min) {
                    answer(vy < 0 ? Scheduler.EASY : Scheduler.HARD);
                    return true;
                }
                return false;
            }
        });
        web.setOnTouchListener((v, ev) -> {
            detector.onTouchEvent(ev);
            return false;
        });

        btnBack.setOnClickListener(v -> finish());
        btnShow.setOnClickListener(v -> reveal());
        bAgain.setOnClickListener(v -> answer(Scheduler.AGAIN));
        bHard.setOnClickListener(v -> answer(Scheduler.HARD));
        bGood.setOnClickListener(v -> answer(Scheduler.GOOD));
        bEasy.setOnClickListener(v -> answer(Scheduler.EASY));
        btnRecheck.setOnClickListener(v -> {
            finishedOverlay.setVisibility(View.GONE);
            rebuildQueue();
        });
        btnUndo.setOnClickListener(v -> undo());
        btnMore.setOnClickListener(v -> showCardMenu(v));

        etType.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                reveal();
                return true;
            }
            return false;
        });

        bAgain.setText(getString(R.string.again));
        bHard.setText(getString(R.string.hard));
        bGood.setText(getString(R.string.good));
        bEasy.setText(getString(R.string.easy));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Store.unburyOld(this, Util.dayNum(System.currentTimeMillis()));
        rebuildQueue();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSound();
    }

    private int[] parseSteps(String s) {
        try {
            String[] parts = s.split(",");
            List<Integer> vals = new ArrayList<>();
            for (String p : parts) {
                p = p.trim();
                if (!p.isEmpty()) vals.add(Math.max(1, Integer.parseInt(p)));
            }
            if (vals.isEmpty()) return new int[]{1, 10};
            int[] out = new int[vals.size()];
            for (int i = 0; i < vals.size(); i++) out[i] = vals.get(i);
            return out;
        } catch (Exception e) {
            return new int[]{1, 10};
        }
    }

    private void buildFontCss() {
        try {
            File reg = new File(getFilesDir(), "fonts/Vazirmatn-Regular.ttf");
            File bold = new File(getFilesDir(), "fonts/Vazirmatn-Bold.ttf");
            if (reg.exists()) {
                fontCss = "@font-face{font-family:'Vazirmatn';font-weight:400;src:url('file://"
                        + reg.getAbsolutePath() + "') format('truetype');}";
                if (bold.exists()) {
                    fontCss += "@font-face{font-family:'Vazirmatn';font-weight:700;src:url('file://"
                            + bold.getAbsolutePath() + "') format('truetype');}";
                }
            }
        } catch (Exception ignored) {}
    }

    // --------------------------------------------------------------- صف مرور

    private void rebuildQueue() {
        exec.execute(() -> {
            long now = System.currentTimeMillis();
            long today = Util.dayNum(now);
            int newLimit = Store.prefInt(this, "newPerDay", 20);
            int revLimit = Store.prefInt(this, "revPerDay", 200);
            List<Long> q = Store.buildQueue(this, dids, now, today, newLimit, revLimit);
            ui.post(() -> {
                queue.clear();
                queue.addAll(q);
                if (queue.isEmpty()) showFinished();
                else nextCard();
            });
        });
    }

    private void nextCard() {
        if (queue.isEmpty()) {
            showFinished();
            return;
        }
        Long id = queue.remove(0);
        Card k = Store.card(this, id);
        if (k == null) {
            nextCard();
            return;
        }
        current = k;
        // شمارندهٔ سقف روزانه
        if (k.state == Card.STATE_NEW) Store.incDone(this, Store.DONE_NEW, Util.dayNum(System.currentTimeMillis()), 1);
        else if (k.state == Card.STATE_REVIEW && k.due <= Util.dayNum(System.currentTimeMillis())) {
            Store.incDone(this, Store.DONE_REV, Util.dayNum(System.currentTimeMillis()), 1);
        }
        showFront();
        updateCounts();
    }

    private void showFinished() {
        current = null;
        finishedOverlay.setVisibility(View.VISIBLE);
        tvFinishedSub.setText(getString(R.string.session_reviewed_fmt, Util.fa(sessionCount)));
        stopSound();
    }

    // --------------------------------------------------------------- نمایش کارت

    private boolean loadData() {
        note = Store.note(this, current.nid);
        nt = Store.notetype(this, note == null ? 0 : note.mid);
        return note != null && nt != null;
    }

    private TemplateEngine.Data buildData() {
        TemplateEngine.Data d = new TemplateEngine.Data();
        try {
            JSONObject o = new JSONObject(nt.json);
            JSONArray tmpls = o.optJSONArray("tmpls");
            JSONObject t = null;
            if (tmpls != null) {
                for (int i = 0; i < tmpls.length(); i++) {
                    JSONObject ti = tmpls.getJSONObject(i);
                    if (ti.optInt("ord", i) == current.ord) { t = ti; break; }
                }
                if (t == null && tmpls.length() > 0) t = tmpls.getJSONObject(0);
            }
            if (t != null) {
                d.qfmt = t.optString("qfmt", "");
                d.afmt = t.optString("afmt", "");
                d.cardName = t.optString("name", "");
            }
            d.css = o.optString("css", "");
            d.isCloze = o.optInt("type", 0) == 1;
            JSONArray flds = o.optJSONArray("flds");
            if (flds != null) {
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < flds.length(); i++) list.add(flds.getJSONObject(i));
                java.util.Collections.sort(list, (a, b) -> Integer.compare(a.optInt("ord", 0), b.optInt("ord", 0)));
                d.fieldNames = new String[list.size()];
                for (int i = 0; i < list.size(); i++) d.fieldNames[i] = list.get(i).optString("name", "f" + i);
            }
        } catch (Exception ignored) {
        }
        String[] vals = note.fieldsArray();
        if (d.fieldNames.length == 0) {
            d.fieldNames = new String[]{"اصلی"};
            d.fieldValues = vals;
        } else {
            d.fieldValues = new String[d.fieldNames.length];
            for (int i = 0; i < d.fieldNames.length; i++) {
                d.fieldValues[i] = i < vals.length ? vals[i] : "";
            }
        }
        d.ord = current.ord;
        d.deckName = deckName;
        d.tags = note.tags;
        d.fontCss = fontCss;
        return d;
    }

    private void showFront() {
        answered = false;
        if (!loadData()) {
            nextCard();
            return;
        }
        cardShownAt = System.currentTimeMillis();
        TemplateEngine.Data d = buildData();
        String html = TemplateEngine.front(d);
        typeField = TemplateEngine.lastTypeField;
        typeTarget = "";
        if (typeField != null) {
            String[] names = d.fieldNames;
            for (int i = 0; i < names.length; i++) {
                if (names[i].equalsIgnoreCase(typeField)) {
                    typeTarget = Util.stripHtml(d.fieldValues[i]).trim();
                    break;
                }
            }
        }
        if (typeTarget.isEmpty()) typeField = null;

        etType.setText("");
        etType.setVisibility(typeField == null ? View.GONE : View.VISIBLE);
        btnShow.setVisibility(typeField == null ? View.VISIBLE : View.GONE);
        answerRow.setVisibility(View.GONE);
        finishedOverlay.setVisibility(View.GONE);
        web.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null);
    }

    private void reveal() {
        if (answered || current == null) return;
        answered = true;
        TemplateEngine.Data d = buildData();
        String typed = etType.getVisibility() == View.VISIBLE ? etType.getText().toString() : "";
        if (typeField != null) {
            d.typeHtml = Util.typeCompareHtml(typeTarget, typed);
        }
        String html = TemplateEngine.back(d);
        web.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null);
        btnShow.setVisibility(View.GONE);
        etType.setVisibility(View.GONE);
        answerRow.setVisibility(View.VISIBLE);
        long now = System.currentTimeMillis();
        bAgain.setText(getString(R.string.again) + "\n" + Scheduler.preview(current, Scheduler.AGAIN, now));
        bHard.setText(getString(R.string.hard) + "\n" + Scheduler.preview(current, Scheduler.HARD, now));
        bGood.setText(getString(R.string.good) + "\n" + Scheduler.preview(current, Scheduler.GOOD, now));
        bEasy.setText(getString(R.string.easy) + "\n" + Scheduler.preview(current, Scheduler.EASY, now));
    }

    private void answer(int rating) {
        if (current == null) return;
        long now = System.currentTimeMillis();
        Card prev = current.copy();
        Scheduler.answer(current, rating, now);
        Store.updateCard(this, current);
        Store.logRev(this, prev, current, rating, now, now - cardShownAt);
        sessionCount++;
        View root = findViewById(R.id.rootReview);
        if (root != null) {
            root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        stopSound();
        // کارت‌های یادگیری که همین حالا سررسید شدند را دوباره بررسی کن
        if (current.state == Card.STATE_LEARN || current.state == Card.STATE_RELEARN) {
            exec.execute(() -> {
                long n2 = System.currentTimeMillis();
                List<Long> learn = Store.buildQueue(this, dids, n2, Util.dayNum(n2), 0, 0);
                ui.post(() -> {
                    queue.addAll(0, learn);
                    nextCard();
                });
            });
        } else {
            nextCard();
        }
    }

    private void undo() {
        stopSound();
        Card k = Store.undoLast(this);
        if (k == null) {
            Toast.makeText(this, R.string.nothing_to_undo, Toast.LENGTH_SHORT).show();
            return;
        }
        sessionCount = Math.max(0, sessionCount - 1);
        queue.add(0, k.id);
        finishedOverlay.setVisibility(View.GONE);
        current = k;
        if (!loadData()) {
            rebuildQueue();
            return;
        }
        answered = false;
        showFront();
        updateCounts();
    }

    private void updateCounts() {
        exec.execute(() -> {
            long now = System.currentTimeMillis();
            long today = Util.dayNum(now);
            int newLimit = Store.prefInt(this, "newPerDay", 20);
            int revLimit = Store.prefInt(this, "revPerDay", 200);
            final int[] c = Store.counts(this, dids, now, today, newLimit, revLimit);
            ui.post(() -> {
                tvNew.setText(Util.fa(c[0]));
                tvLearn.setText(Util.fa(c[1]));
                tvDue.setText(Util.fa(c[2]));
            });
        });
    }

    // --------------------------------------------------------------- منوی کارت

    private void showCardMenu(View anchor) {
        if (current == null) return;
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, current.suspended ? R.string.unsuspend_card : R.string.suspend_card);
        pm.getMenu().add(0, 2, 1, R.string.bury_card);
        pm.getMenu().add(0, 3, 2, R.string.edit_card);
        pm.getMenu().add(0, 4, 3, R.string.delete_card);
        pm.getMenu().add(0, 5, 4, R.string.end_session);
        pm.setOnMenuItemClickListener(this::onMenuItem);
        pm.show();
    }

    private boolean onMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (current == null) return true;
        if (id == 1) {
            Store.setSuspended(this, current.id, !current.suspended);
            Toast.makeText(this, current.suspended ? R.string.unsuspended_msg : R.string.suspended_msg, Toast.LENGTH_SHORT).show();
            current.suspended = !current.suspended;
            nextCard();
        } else if (id == 2) {
            Store.setBuried(this, current.id, Util.dayNum(System.currentTimeMillis()));
            Toast.makeText(this, R.string.buried_msg, Toast.LENGTH_SHORT).show();
            nextCard();
        } else if (id == 3) {
            NoteEditor.edit(this, current.nid, this::showFront);
        } else if (id == 4) {
            Store.deleteCard(this, current.id);
            Toast.makeText(this, R.string.card_deleted, Toast.LENGTH_SHORT).show();
            nextCard();
        } else if (id == 5) {
            finish();
        }
        return true;
    }

    // --------------------------------------------------------------- صدا

    private void playSound(String name) {
        try {
            stopSound();
            String safe = name.replaceAll("[/\\\\]", "_");
            File f = new File(ApkgImporter.mediaDir(this), safe);
            if (!f.exists()) return;
            player = MediaPlayer.create(this, Uri.fromFile(f));
            if (player != null) {
                player.start();
                player.setOnCompletionListener(mp -> {
                    mp.release();
                    if (player == mp) player = null;
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void stopSound() {
        try {
            if (player != null) {
                player.release();
                player = null;
            }
        } catch (Exception ignored) {
        }
    }
}
