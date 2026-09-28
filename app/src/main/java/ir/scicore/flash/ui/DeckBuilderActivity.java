package ir.scicore.flash.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import ir.scicore.flash.R;
import ir.scicore.flash.db.Store;
import ir.scicore.flash.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * سازهٔ مستقل ساخت دک — بدون نیاز به فایل apkg:
 * نام دک + کارت‌های دستی + درون‌ریزی گروهی از متن (خروجی هوش مصنوعی).
 */
public class DeckBuilderActivity extends Base {

    /** draft: [front, back] */
    private final List<String[]> drafts = new ArrayList<>();

    private EditText etDeckName, etFront, etBack;
    private LinearLayout llDrafts;
    private TextView tvDraftCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deck_builder);

        etDeckName = findViewById(R.id.etDeckName);
        etFront = findViewById(R.id.etFront);
        etBack = findViewById(R.id.etBack);
        llDrafts = findViewById(R.id.llDrafts);
        tvDraftCount = findViewById(R.id.tvDraftCount);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAiGuide).setOnClickListener(v -> openGuide());
        findViewById(R.id.btnOpenAiGuide).setOnClickListener(v -> openGuide());
        findViewById(R.id.btnAddCard).setOnClickListener(v -> addDraftFromInputs());
        findViewById(R.id.btnImportText).setOnClickListener(v -> showPasteDialog());
        findViewById(R.id.btnSaveDeck).setOnClickListener(v -> saveDeck());

        refreshDrafts();
    }

    private void openGuide() {
        startActivity(new Intent(this, AiGuideActivity.class));
    }

    // ------------------------------------------------------------ کارت‌های دستی

    private void addDraftFromInputs() {
        String f = etFront.getText().toString().trim();
        String b = etBack.getText().toString().trim();
        if (f.isEmpty() && b.isEmpty()) {
            Toast.makeText(this, R.string.need_field, Toast.LENGTH_SHORT).show();
            return;
        }
        if (f.isEmpty()) f = b;
        drafts.add(new String[]{f, b});
        etFront.setText("");
        etBack.setText("");
        etFront.requestFocus();
        refreshDrafts();
    }

    private void refreshDrafts() {
        llDrafts.removeAllViews();
        int n = drafts.size();
        tvDraftCount.setText(n == 0
                ? getString(R.string.cards_list_title)
                : getString(R.string.cards_count_fmt, Util.fa(n)));
        for (int i = 0; i < n; i++) {
            llDrafts.addView(draftRow(i));
        }
    }

    /** ردیف پیش‌نمایش کارت با دکمهٔ حذف */
    private View draftRow(final int index) {
        String[] d = drafts.get(index);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E2226);
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, 0xFF141719);
        row.setBackground(bg);
        int p = dp(12);
        row.setPadding(p, p / 2 + dp(2), p, p / 2 + dp(2));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView num = new TextView(this);
        num.setText(Util.fa(index + 1));
        num.setTextSize(12);
        num.setTextColor(0xFF6B7683);
        top.addView(num);

        TextView front = new TextView(this);
        front.setText(d[0]);
        front.setTextSize(14);
        front.setTextColor(0xFFE7EAED);
        front.setTypeface(null, android.graphics.Typeface.BOLD);
        front.setMaxLines(2);
        front.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        flp.setMargins(dp(10), 0, dp(10), 0);
        top.addView(front, flp);

        TextView del = new TextView(this);
        del.setText(R.string.remove_draft);
        del.setTextSize(12);
        del.setTextColor(0xFFF87171);
        del.setPadding(dp(8), dp(4), dp(8), dp(4));
        del.setOnClickListener(v -> {
            drafts.remove(index);
            refreshDrafts();
        });
        top.addView(del);
        row.addView(top);

        if (!d[1].isEmpty()) {
            TextView back = new TextView(this);
            back.setText(d[1]);
            back.setTextSize(13);
            back.setTextColor(0xFF98A2AC);
            back.setMaxLines(3);
            back.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.setMargins(0, dp(4), 0, 0);
            row.addView(back, blp);
        }

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rlp);
        return row;
    }

    // ------------------------------------------------------------ درون‌ریزی از متن

    private void showPasteDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, 0, p, 0);

        final EditText et = new EditText(this);
        et.setTextSize(14);
        et.setTextColor(0xFFE7EAED);
        et.setHintTextColor(0xFF6B7683);
        et.setHint(R.string.paste_hint);
        et.setBackground(new GradientDrawable() {{
            setColor(0xFF1E2226);
            setCornerRadius(dp(10));
            setStroke(1, 0xFF141719);
        }});
        et.setMinLines(6);
        et.setGravity(Gravity.TOP);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220));
        box.addView(et, lp);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.paste_title)
                .setView(box)
                .setPositiveButton(R.string.paste_import_btn, (dlg, w) -> {
                    int added = parseAndAdd(et.getText().toString());
                    if (added == 0) {
                        Toast.makeText(this, R.string.paste_none, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                getString(R.string.paste_added_fmt, Util.fa(added)),
                                Toast.LENGTH_SHORT).show();
                        refreshDrafts();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * تجزیهٔ متن پیست‌شده: هر خط یک کارت؛ جداکننده به ترتیب
     * Tab → ، → , → ؛ → ؛   و حذف کوتیشن دور هر بخش.
     */
    int parseAndAdd(String text) {
        if (text == null) return 0;
        int added = 0;
        String[] lines = text.split("\n");
        for (String raw : lines) {
            String line = raw.replace('\u000B', ' ').trim();
            if (line.isEmpty()) continue;
            String[] pair = splitPair(line);
            if (pair == null) continue;
            if (isHeader(pair[0])) continue;
            if (pair[0].isEmpty() && pair[1].isEmpty()) continue;
            if (pair[0].isEmpty()) pair[0] = pair[1];
            drafts.add(pair);
            added++;
        }
        return added;
    }

    private static String[] splitPair(String line) {
        int idx = line.indexOf('\t');
        if (idx < 0) {
            int a = indexOfAny(line, "،,", "؛;");
            idx = a;
        }
        if (idx < 0) {
            // یک خط بدون جداکننده: اگر کوتاه است، خودش سؤال با پاسخ خالی می‌شود
            return new String[]{stripQuotes(line), ""};
        }
        String f = stripQuotes(line.substring(0, idx).trim());
        String b = stripQuotes(line.substring(idx + 1).replace('\t', ' ').trim());
        return new String[]{f, b};
    }

    private static int indexOfAny(String s, String primary, String secondary) {
        int best = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (primary.indexOf(c) >= 0) return i;
            if (best < 0 && secondary.indexOf(c) >= 0) best = i;
        }
        return best;
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2) {
            char a = s.charAt(0), b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '«' && b == '»')) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

    private static boolean isHeader(String front) {
        String f = front.toLowerCase().replace(" ", "").replace("_", "");
        return f.equals("سؤال") || f.equals("سوال") || f.equals("question")
                || f.equals("front") || f.equals("q") || f.equals("پرسش");
    }

    // ------------------------------------------------------------ ذخیرهٔ دک

    private void saveDeck() {
        String name = etDeckName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.need_deck_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (drafts.isEmpty()) {
            Toast.makeText(this, R.string.need_cards, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean existed = false;
        for (ir.scicore.flash.db.Deck d : Store.decks(this)) {
            if (d.name.equals(name)) {
                existed = true;
                break;
            }
        }
        long deckId = Store.addDeck(this, name);
        long ntId = Store.ensureBaseNotetype(this);
        for (String[] d : drafts) {
            Store.addNote(this, ntId, deckId, new String[]{d[0], d[1]}, "");
        }
        Toast.makeText(this, existed
                        ? getString(R.string.deck_appended_fmt, Util.fa(drafts.size()), name)
                        : getString(R.string.deck_created_fmt, name, Util.fa(drafts.size())),
                Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
