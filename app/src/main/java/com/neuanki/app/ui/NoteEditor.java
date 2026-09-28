package com.neuanki.app.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.neuanki.app.R;
import com.neuanki.app.db.Card;
import com.neuanki.app.db.Deck;
import com.neuanki.app.db.Note;
import com.neuanki.app.db.Notetype;
import com.neuanki.app.db.Store;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** ساخت و ویرایش یادداشت (دیالوگ برنامه‌نویسی‌شده برای پشتیبانی از فیلدهای داینامیک) */
public final class NoteEditor {

    public interface Callback {
        void onSaved();
    }

    private NoteEditor() {}

    // ------------------------------------------------------------------ ساخت یادداشت جدید

    public static void create(Context ctx, long presetDeckId, Callback cb) {
        List<Notetype> nts = Store.notetypes(ctx);
        List<Deck> decks = Store.decks(ctx);
        if (nts.isEmpty() || decks.isEmpty()) {
            Toast.makeText(ctx, R.string.empty_title, Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout root = column(ctx);
        TextView lblType = label(ctx, ctx.getString(R.string.note_type));
        root.addView(lblType);

        Spinner spType = new Spinner(ctx);
        root.addView(spType);

        final LinearLayout fieldsBox = new LinearLayout(ctx);
        fieldsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(fieldsBox);

        TextView lblTags = label(ctx, ctx.getString(R.string.tags_hint));
        root.addView(lblTags);
        final EditText etTags = input(ctx);
        root.addView(etTags);

        TextView lblDeck = label(ctx, ctx.getString(R.string.choose_deck));
        root.addView(lblDeck);
        Spinner spDeck = new Spinner(ctx);
        root.addView(spDeck);

        List<String> typeNames = new ArrayList<>();
        for (Notetype nt : nts) typeNames.add(nt.name);
        spType.setAdapter(spinnerAdapter(ctx, typeNames));

        List<String> deckNames = new ArrayList<>();
        int deckSel = 0;
        for (int i = 0; i < decks.size(); i++) {
            deckNames.add(decks.get(i).name);
            if (decks.get(i).id == presetDeckId) deckSel = i;
        }
        spDeck.setAdapter(spinnerAdapter(ctx, deckNames));
        spDeck.setSelection(deckSel);

        Runnable rebuild = () -> {
            Notetype sel = nts.get(spType.getSelectedItemPosition());
            fieldsBox.removeAllViews();
            for (String fname : fieldNames(sel)) {
                TextView l = label(ctx, fname);
                fieldsBox.addView(l);
                fieldsBox.addView(input(ctx));
            }
        };
        rebuild.run();
        spType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { rebuild.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.menu_add_card)
                .setView(scroll(ctx, root))
                .setPositiveButton(R.string.save, (dlg, w) -> {
                    Notetype sel = nts.get(spType.getSelectedItemPosition());
                    String[] vals = new String[fieldsBox.getChildCount() > 0 ? countInputs(fieldsBox) : 0];
                    collect(fieldsBox, vals);
                    boolean any = false;
                    for (String v : vals) if (v != null && !v.trim().isEmpty()) { any = true; break; }
                    if (!any) {
                        Toast.makeText(ctx, R.string.need_field, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Deck d = decks.get(spDeck.getSelectedItemPosition());
                    Store.addNote(ctx, sel.id, d.id, vals, etTags.getText().toString().trim());
                    Toast.makeText(ctx, R.string.card_added, Toast.LENGTH_SHORT).show();
                    if (cb != null) cb.onSaved();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ ویرایش یادداشت

    public static void edit(Context ctx, long noteId, Callback cb) {
        Note n = Store.note(ctx, noteId);
        if (n == null) return;
        Notetype nt = Store.notetype(ctx, n.mid);
        if (nt == null) return;

        List<Deck> decks = Store.decks(ctx);
        List<Card> cards = Store.cardsOfNote(ctx, noteId);
        long curDeck = cards.isEmpty() ? 0 : cards.get(0).did;

        LinearLayout root = column(ctx);
        TextView lblType = label(ctx, ctx.getString(R.string.note_type) + ": " + nt.name);
        root.addView(lblType);

        final LinearLayout fieldsBox = new LinearLayout(ctx);
        fieldsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(fieldsBox);

        String[] names = fieldNames(nt);
        String[] values = n.fieldsArray();
        for (int i = 0; i < names.length; i++) {
            fieldsBox.addView(label(ctx, names[i]));
            EditText e = input(ctx);
            e.setText(i < values.length ? values[i] : "");
            fieldsBox.addView(e);
        }

        TextView lblTags = label(ctx, ctx.getString(R.string.tags_hint));
        root.addView(lblTags);
        final EditText etTags = input(ctx);
        etTags.setText(n.tags);
        root.addView(etTags);

        TextView lblDeck = label(ctx, ctx.getString(R.string.choose_deck));
        root.addView(lblDeck);
        Spinner spDeck = new Spinner(ctx);
        List<String> deckNames = new ArrayList<>();
        int deckSel = 0;
        for (int i = 0; i < decks.size(); i++) {
            deckNames.add(decks.get(i).name);
            if (decks.get(i).id == curDeck) deckSel = i;
        }
        spDeck.setAdapter(spinnerAdapter(ctx, deckNames));
        spDeck.setSelection(deckSel);
        root.addView(spDeck);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.edit_card)
                .setView(scroll(ctx, root))
                .setPositiveButton(R.string.save, (dlg, w) -> {
                    String[] vals = new String[names.length];
                    for (int i = 0; i < vals.length; i++) {
                        EditText e = inputAt(fieldsBox, i);
                        vals[i] = e == null ? "" : e.getText().toString();
                    }
                    n.flds = n.joinedFields(vals);
                    n.tags = etTags.getText().toString().trim();
                    n.mod = System.currentTimeMillis() / 1000L;
                    Store.updateNote(ctx, n);
                    if (spDeck.getSelectedItemPosition() >= 0) {
                        Deck d = decks.get(spDeck.getSelectedItemPosition());
                        for (Card k : cards) {
                            k.did = d.id;
                            Store.updateCard(ctx, k);
                        }
                    }
                    Toast.makeText(ctx, R.string.saved, Toast.LENGTH_SHORT).show();
                    if (cb != null) cb.onSaved();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ ابزارها

    private static String[] fieldNames(Notetype nt) {
        List<String> out = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(nt.json);
            JSONArray flds = o.optJSONArray("flds");
            if (flds != null) {
                // مرتب‌سازی بر اساس ord
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < flds.length(); i++) list.add(flds.getJSONObject(i));
                java.util.Collections.sort(list, (a, b) -> Integer.compare(a.optInt("ord", 0), b.optInt("ord", 0)));
                for (JSONObject f : list) out.add(f.optString("name", "فیلد"));
            }
        } catch (Exception ignored) {}
        if (out.isEmpty()) out.add("اصلی");
        return out.toArray(new String[0]);
    }

    private static LinearLayout column(Context ctx) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(ctx, 20);
        l.setPadding(p, p / 2, p, 0);
        return l;
    }

    private static ScrollView scroll(Context ctx, View child) {
        ScrollView s = new ScrollView(ctx);
        s.addView(child);
        return s;
    }

    private static TextView label(Context ctx, String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(0xFF98A2AC);
        int tp = dp(ctx, 10);
        t.setPadding(0, tp, 0, tp / 2);
        return t;
    }

    private static EditText input(Context ctx) {
        EditText e = new EditText(ctx);
        e.setTextSize(15);
        e.setTextColor(0xFFE7EAED);
        e.setHintTextColor(0xFF6B7683);
        e.setBackground(new android.graphics.drawable.GradientDrawable() {{
            setColor(0xFF1E2226);
            setCornerRadius(dp(ctx, 10));
            setStroke(1, 0xFF141719);
        }});
        int p = dp(ctx, 10);
        e.setPadding(p, p / 2, p, p / 2);
        return e;
    }

    private static ArrayAdapter<String> spinnerAdapter(Context ctx, List<String> items) {
        ArrayAdapter<String> ad = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return ad;
    }

    private static int countInputs(LinearLayout box) {
        int n = 0;
        for (int i = 0; i < box.getChildCount(); i++) {
            if (box.getChildAt(i) instanceof EditText) n++;
        }
        return n;
    }

    private static void collect(LinearLayout box, String[] out) {
        int idx = 0;
        for (int i = 0; i < box.getChildCount() && idx < out.length; i++) {
            View v = box.getChildAt(i);
            if (v instanceof EditText) out[idx++] = ((EditText) v).getText().toString();
        }
    }

    private static EditText inputAt(LinearLayout box, int index) {
        int idx = 0;
        for (int i = 0; i < box.getChildCount(); i++) {
            View v = box.getChildAt(i);
            if (v instanceof EditText) {
                if (idx == index) return (EditText) v;
                idx++;
            }
        }
        return null;
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
