package com.neuanki.app.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** لایهٔ دسترسی به داده — همهٔ کوئری‌ها اینجاست */
public class Store {

    public static final String DONE_NEW = "doneNew";
    public static final String DONE_REV = "doneRev";

    private static final Object ID_LOCK = new Object();
    private static long idCounter = 0;

    public static long newId() {
        synchronized (ID_LOCK) {
            return System.currentTimeMillis() * 1000L + (idCounter++ % 1000);
        }
    }

    // ------------------------------------------------------------- decks

    public static List<Deck> decks(Context c) {
        List<Deck> out = new ArrayList<>();
        Cursor cur = Db.get(c).r().rawQuery("SELECT id, name FROM decks ORDER BY name COLLATE NOCASE", null);
        while (cur.moveToNext()) {
            Deck d = new Deck();
            d.id = cur.getLong(0);
            d.name = cur.getString(1);
            out.add(d);
        }
        cur.close();
        return out;
    }

    public static Deck deck(Context c, long id) {
        Cursor cur = Db.get(c).r().rawQuery("SELECT id, name FROM decks WHERE id=?", new String[]{String.valueOf(id)});
        Deck d = null;
        if (cur.moveToFirst()) {
            d = new Deck();
            d.id = cur.getLong(0);
            d.name = cur.getString(1);
        }
        cur.close();
        return d;
    }

    public static long addDeck(Context c, String name) {
        SQLiteDatabase db = Db.get(c).w();
        Cursor cur = db.rawQuery("SELECT id FROM decks WHERE name=?", new String[]{name});
        long id;
        if (cur.moveToFirst()) {
            id = cur.getLong(0);
        } else {
            id = newId();
            ContentValues cv = new ContentValues();
            cv.put("id", id);
            cv.put("name", name);
            db.insert("decks", null, cv);
        }
        cur.close();
        return id;
    }

    public static void renameDeck(Context c, long id, String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        Db.get(c).w().update("decks", cv, "id=?", new String[]{String.valueOf(id)});
    }

    /** خود دک + همهٔ زیردک‌ها (بر اساس سلسله‌مراتب نام) */
    public static Set<Long> deckAndChildren(Context c, long rootId) {
        Set<Long> out = new HashSet<>();
        Deck root = deck(c, rootId);
        if (root == null) return out;
        String prefix = root.name + "::";
        Cursor cur = Db.get(c).r().rawQuery("SELECT id, name FROM decks", null);
        while (cur.moveToNext()) {
            String nm = cur.getString(1);
            if (nm.equals(root.name) || nm.startsWith(prefix)) out.add(cur.getLong(0));
        }
        cur.close();
        return out;
    }

    public static void deleteDeck(Context c, long deckId) {
        Set<Long> ids = deckAndChildren(c, deckId);
        if (ids.isEmpty()) return;
        String in = joinIds(ids);
        SQLiteDatabase db = Db.get(c).w();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM revlog WHERE cid IN (SELECT id FROM cards WHERE did IN (" + in + "))");
            db.execSQL("DELETE FROM notes WHERE id IN (SELECT DISTINCT nid FROM cards WHERE did IN (" + in + "))");
            db.execSQL("DELETE FROM cards WHERE did IN (" + in + ")");
            db.execSQL("DELETE FROM decks WHERE id IN (" + in + ")");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ------------------------------------------------------------- notetypes

    public static List<Notetype> notetypes(Context c) {
        List<Notetype> out = new ArrayList<>();
        Cursor cur = Db.get(c).r().rawQuery("SELECT id, name, json FROM notetypes ORDER BY name", null);
        while (cur.moveToNext()) {
            Notetype nt = new Notetype();
            nt.id = cur.getLong(0);
            nt.name = cur.getString(1);
            nt.json = cur.getString(2);
            try { nt.type = new JSONObject(nt.json).optInt("type", 0); } catch (Exception ignored) {}
            out.add(nt);
        }
        cur.close();
        return out;
    }

    public static Notetype notetype(Context c, long id) {
        Cursor cur = Db.get(c).r().rawQuery("SELECT id, name, json FROM notetypes WHERE id=?", new String[]{String.valueOf(id)});
        Notetype nt = null;
        if (cur.moveToFirst()) {
            nt = new Notetype();
            nt.id = cur.getLong(0);
            nt.name = cur.getString(1);
            nt.json = cur.getString(2);
            try { nt.type = new JSONObject(nt.json).optInt("type", 0); } catch (Exception ignored) {}
        }
        cur.close();
        return nt;
    }

    // ------------------------------------------------------------- cards/notes

    private static Card cardFromCursor(Cursor cur) {
        Card k = new Card();
        k.id = cur.getLong(0);
        k.nid = cur.getLong(1);
        k.did = cur.getLong(2);
        k.ord = cur.getInt(3);
        k.state = cur.getInt(4);
        k.suspended = cur.getInt(5) != 0;
        k.buried = cur.getInt(6) != 0;
        k.buriedDay = cur.getLong(7);
        k.due = cur.getLong(8);
        k.ivl = cur.getInt(9);
        k.ease = cur.getInt(10);
        k.step = cur.getInt(11);
        k.reps = cur.getInt(12);
        k.lapses = cur.getInt(13);
        k.mod = cur.getLong(14);
        return k;
    }

    private static final String CARD_COLS =
            "id, nid, did, ord, state, suspended, buried, buried_day, due, ivl, ease, step, reps, lapses, mod";

    public static Card card(Context c, long id) {
        Cursor cur = Db.get(c).r().rawQuery("SELECT " + CARD_COLS + " FROM cards WHERE id=?",
                new String[]{String.valueOf(id)});
        Card k = null;
        if (cur.moveToFirst()) k = cardFromCursor(cur);
        cur.close();
        return k;
    }

    public static Note note(Context c, long id) {
        Cursor cur = Db.get(c).r().rawQuery("SELECT id, guid, mid, mod, flds, tags FROM notes WHERE id=?",
                new String[]{String.valueOf(id)});
        Note n = null;
        if (cur.moveToFirst()) {
            n = new Note();
            n.id = cur.getLong(0);
            n.guid = cur.getString(1);
            n.mid = cur.getLong(2);
            n.mod = cur.getLong(3);
            n.flds = cur.getString(4);
            n.tags = cur.getString(5) == null ? "" : cur.getString(5);
        }
        cur.close();
        return n;
    }

    public static List<Card> cardsOfNote(Context c, long nid) {
        List<Card> out = new ArrayList<>();
        Cursor cur = Db.get(c).r().rawQuery("SELECT " + CARD_COLS + " FROM cards WHERE nid=?", new String[]{String.valueOf(nid)});
        while (cur.moveToNext()) out.add(cardFromCursor(cur));
        cur.close();
        return out;
    }

    public static void updateCard(Context c, Card k) {
        ContentValues cv = new ContentValues();
        cv.put("nid", k.nid); cv.put("did", k.did); cv.put("ord", k.ord);
        cv.put("state", k.state); cv.put("suspended", k.suspended ? 1 : 0);
        cv.put("buried", k.buried ? 1 : 0); cv.put("buried_day", k.buriedDay);
        cv.put("due", k.due); cv.put("ivl", k.ivl); cv.put("ease", k.ease);
        cv.put("step", k.step); cv.put("reps", k.reps); cv.put("lapses", k.lapses);
        cv.put("mod", k.mod);
        Db.get(c).w().update("cards", cv, "id=?", new String[]{String.valueOf(k.id)});
    }

    public static void updateNote(Context c, Note n) {
        ContentValues cv = new ContentValues();
        cv.put("flds", n.flds);
        cv.put("tags", n.tags);
        cv.put("mid", n.mid);
        cv.put("mod", n.mod);
        Db.get(c).w().update("notes", cv, "id=?", new String[]{String.valueOf(n.id)});
    }

    public static void deleteNote(Context c, long nid) {
        SQLiteDatabase db = Db.get(c).w();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM revlog WHERE cid IN (SELECT id FROM cards WHERE nid=?)", new Object[]{nid});
            db.execSQL("DELETE FROM cards WHERE nid=?", new Object[]{nid});
            db.execSQL("DELETE FROM notes WHERE id=?", new Object[]{nid});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static void deleteCard(Context c, long cardId) {
        SQLiteDatabase db = Db.get(c).w();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM revlog WHERE cid=?", new Object[]{cardId});
            db.execSQL("DELETE FROM cards WHERE id=?", new Object[]{cardId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static void setSuspended(Context c, long cardId, boolean susp) {
        ContentValues cv = new ContentValues();
        cv.put("suspended", susp ? 1 : 0);
        Db.get(c).w().update("cards", cv, "id=?", new String[]{String.valueOf(cardId)});
    }

    public static void setBuried(Context c, long cardId, long today) {
        ContentValues cv = new ContentValues();
        cv.put("buried", 1);
        cv.put("buried_day", today);
        Db.get(c).w().update("cards", cv, "id=?", new String[]{String.valueOf(cardId)});
    }

    /** کارت‌های دفن‌شدهٔ روزهای قبل را آزاد می‌کند */
    public static void unburyOld(Context c, long today) {
        Db.get(c).w().execSQL("UPDATE cards SET buried=0 WHERE buried=1 AND buried_day<" + today);
    }

    // ------------------------------------------------------------- صف مرور

    private static String where(Set<Long> dids) {
        return "did IN (" + joinIds(dids) + ") AND suspended=0 AND buried=0";
    }

    private static int q1(SQLiteDatabase db, String sql) {
        Cursor cur = db.rawQuery(sql, null);
        int v = 0;
        if (cur.moveToFirst()) v = cur.getInt(0);
        cur.close();
        return v;
    }

    private static List<Long> qIds(SQLiteDatabase db, String sql) {
        List<Long> out = new ArrayList<>();
        Cursor cur = db.rawQuery(sql, null);
        while (cur.moveToNext()) out.add(cur.getLong(0));
        cur.close();
        return out;
    }

    /** {new, learn, review} با اعمال سقف روزانه */
    public static int[] counts(Context c, Set<Long> dids, long now, long today, int newLimit, int revLimit) {
        if (dids == null || dids.isEmpty()) return new int[]{0, 0, 0};
        String w = where(dids);
        SQLiteDatabase db = Db.get(c).r();
        int learn = q1(db, "SELECT COUNT(*) FROM cards WHERE " + w + " AND state IN(1,3) AND due<=" + now);
        int rev = q1(db, "SELECT COUNT(*) FROM cards WHERE " + w + " AND state=2 AND due<=" + today);
        int nw = q1(db, "SELECT COUNT(*) FROM cards WHERE " + w + " AND state=0");
        int doneN = doneToday(c, DONE_NEW, today);
        int doneR = doneToday(c, DONE_REV, today);
        nw = Math.max(0, Math.min(nw, newLimit - doneN));
        rev = Math.max(0, Math.min(rev, revLimit - doneR));
        return new int[]{nw, learn, rev};
    }

    /** صف مرور: یادگیری‌های سررسید + ترکیب مرور/جدید (هر ۳ مرور یک جدید) */
    public static List<Long> buildQueue(Context c, Set<Long> dids, long now, long today, int newLimit, int revLimit) {
        List<Long> out = new ArrayList<>();
        if (dids == null || dids.isEmpty()) return out;
        String w = where(dids);
        SQLiteDatabase db = Db.get(c).r();

        List<Long> learn = qIds(db, "SELECT id FROM cards WHERE " + w + " AND state IN(1,3) AND due<=" + now
                + " ORDER BY due LIMIT 100");
        int doneR = doneToday(c, DONE_REV, today);
        int doneN = doneToday(c, DONE_NEW, today);
        List<Long> rev = qIds(db, "SELECT id FROM cards WHERE " + w + " AND state=2 AND due<=" + today
                + " ORDER BY due, id LIMIT " + Math.max(0, revLimit - doneR));
        List<Long> nw = qIds(db, "SELECT id FROM cards WHERE " + w + " AND state=0"
                + " ORDER BY due, id LIMIT " + Math.max(0, newLimit - doneN));

        out.addAll(learn);
        int i = 0, j = 0, sinceNew = 0;
        while (i < rev.size() || j < nw.size()) {
            if (j < nw.size() && sinceNew >= 3) {
                out.add(nw.get(j++));
                sinceNew = 0;
            } else if (i < rev.size()) {
                out.add(rev.get(i++));
                sinceNew++;
            } else if (j < nw.size()) {
                out.add(nw.get(j++));
                sinceNew = 0;
            }
        }
        return out;
    }

    public static void incDone(Context c, String base, long today, int delta) {
        String key = base + "_" + today;
        int v = doneToday(c, base, today) + delta;
        setPref(c, key, String.valueOf(v));
    }

    public static int doneToday(Context c, String base, long today) {
        return prefInt(c, base + "_" + today, 0);
    }

    public static int[] allCounts(Context c, long now, long today, int newLimit, int revLimit) {
        Set<Long> ids = new HashSet<>();
        for (Deck d : decks(c)) ids.add(d.id);
        return counts(c, ids, now, today, newLimit, revLimit);
    }

    // ------------------------------------------------------------- revlog

    public static void logRev(Context c, Card prev, Card after, int rating, long now, long timeMs) {
        ContentValues cv = new ContentValues();
        cv.put("id", newId());
        cv.put("cid", after.id);
        cv.put("ts", now);
        cv.put("rating", rating);
        cv.put("ivl", after.ivl);
        cv.put("lastIvl", prev.ivl);
        cv.put("timeMs", timeMs);
        cv.put("prev_state", prev.state);
        cv.put("prev_due", prev.due);
        cv.put("prev_ivl", prev.ivl);
        cv.put("prev_ease", prev.ease);
        cv.put("prev_step", prev.step);
        Db.get(c).w().insert("revlog", null, cv);
    }

    /** بازگردانی آخرین پاسخ — کارتِ قبلی را برمی‌گرداند */
    public static Card undoLast(Context c) {
        SQLiteDatabase db = Db.get(c).w();
        Cursor cur = db.rawQuery("SELECT id, cid, prev_state, prev_due, prev_ivl, prev_ease, prev_step "
                + "FROM revlog ORDER BY id DESC LIMIT 1", null);
        long rowId = -1, cid = -1;
        int st = 0, ivl = 0, ease = 2500, step = 0;
        long due = 0;
        if (cur.moveToFirst()) {
            rowId = cur.getLong(0);
            cid = cur.getLong(1);
            st = cur.getInt(2);
            due = cur.getLong(3);
            ivl = cur.getInt(4);
            ease = cur.getInt(5);
            step = cur.getInt(6);
        }
        cur.close();
        if (rowId < 0) return null;
        Card k = card(c, cid);
        db.execSQL("DELETE FROM revlog WHERE id=?", new Object[]{rowId});
        if (k == null) return null;
        k.state = st;
        k.due = due;
        k.ivl = ivl;
        k.ease = ease;
        k.step = step;
        if (k.reps > 0) k.reps--;
        updateCard(c, k);
        return k;
    }

    // ------------------------------------------------------------- افزودن یادداشت

    public static long addNote(Context c, long mid, long did, String[] fields, String tags) {
        long now = System.currentTimeMillis();
        Note n = new Note();
        n.id = newId();
        n.guid = UUID.randomUUID().toString().substring(0, 10);
        n.mid = mid;
        n.mod = now / 1000L;
        n.flds = n.joinedFields(fields);
        n.tags = tags == null ? "" : tags;
        insertNoteRaw(c, n, n.id);

        Notetype nt = notetype(c, mid);
        List<Integer> ords = new ArrayList<>();
        if (nt != null && nt.type == 1) {
            // کلوز: برای هر شمارهٔ کلوز یک کارت
            Set<Integer> nums = new HashSet<>();
            for (String f : fields) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\{\\{c(\\d+)::").matcher(f == null ? "" : f);
                while (m.find()) {
                    try { nums.add(Integer.parseInt(m.group(1))); } catch (Exception ignored) {}
                }
            }
            List<Integer> sorted = new ArrayList<>(nums);
            java.util.Collections.sort(sorted);
            if (sorted.isEmpty()) ords.add(0);
            else ords.addAll(sorted);
            for (int i = 0; i < ords.size(); i++) ords.set(i, ords.get(i) - 1);
        } else {
            int cnt = nt == null ? 1 : Math.max(1, nt.templateCount());
            for (int i = 0; i < cnt; i++) ords.add(i);
        }

        for (int ord : ords) {
            Card k = new Card();
            k.id = newId();
            k.nid = n.id;
            k.did = did;
            k.ord = ord;
            k.state = Card.STATE_NEW;
            k.due = 0;
            k.ease = 2500;
            k.mod = now / 1000L;
            insertCardRaw(c, k, k.id);
        }
        return n.id;
    }

    // ------------------------------------------------------------- آمار

    public static int totalCards(Context c) {
        return q1(Db.get(c).r(), "SELECT COUNT(*) FROM cards");
    }

    public static int reviewsOnDay(Context c, long day) {
        return q1(Db.get(c).r(), "SELECT COUNT(*) FROM revlog WHERE ts/86400000=" + day);
    }

    public static int streak(Context c, long today) {
        Set<Long> days = new HashSet<>();
        Cursor cur = Db.get(c).r().rawQuery("SELECT DISTINCT ts/86400000 FROM revlog", null);
        while (cur.moveToNext()) days.add(cur.getLong(0));
        cur.close();
        int streak = 0;
        long d = days.contains(today) ? today : today - 1;
        while (days.contains(d)) {
            streak++;
            d--;
        }
        return streak;
    }

    public static int correctPct(Context c) {
        SQLiteDatabase db = Db.get(c).r();
        int total = q1(db, "SELECT COUNT(*) FROM revlog WHERE prev_state=2");
        if (total == 0) return -1;
        int good = q1(db, "SELECT COUNT(*) FROM revlog WHERE prev_state=2 AND rating>1");
        return (int) Math.round(good * 100.0 / total);
    }

    /** روز → تعداد مرور برای n روز گذشته */
    public static Map<Long, Integer> reviewsPerDay(Context c, int days, long today) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (long d = today - days + 1; d <= today; d++) out.put(d, 0);
        Cursor cur = Db.get(c).r().rawQuery(
                "SELECT ts/86400000, COUNT(*) FROM revlog WHERE ts/86400000>=" + (today - days + 1)
                        + " GROUP BY ts/86400000", null);
        while (cur.moveToNext()) {
            long d = cur.getLong(0);
            if (out.containsKey(d)) out.put(d, cur.getInt(1));
        }
        cur.close();
        return out;
    }

    /** روز → تعداد کارت‌های سررسید برای n روز آینده */
    public static Map<Long, Integer> forecast(Context c, long today, int days) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (long d = today; d < today + days; d++) out.put(d, 0);
        SQLiteDatabase db = Db.get(c).r();
        Cursor cur = db.rawQuery("SELECT due, COUNT(*) FROM cards WHERE state=2 AND suspended=0 AND buried=0 "
                + "AND due>=" + today + " AND due<" + (today + days) + " GROUP BY due", null);
        while (cur.moveToNext()) {
            long d = cur.getLong(0);
            if (out.containsKey(d)) out.put(d, out.get(d) + cur.getInt(1));
        }
        cur.close();
        int learn = q1(db, "SELECT COUNT(*) FROM cards WHERE state IN(1,3) AND suspended=0 AND buried=0 "
                + "AND due<" + ((today + 1) * 86400000L));
        if (out.containsKey(today)) out.put(today, out.get(today) + learn);
        return out;
    }

    /** {new, learn, young, mature, suspended} */
    public static int[] stateCounts(Context c) {
        SQLiteDatabase db = Db.get(c).r();
        int nw = q1(db, "SELECT COUNT(*) FROM cards WHERE state=0 AND suspended=0");
        int lr = q1(db, "SELECT COUNT(*) FROM cards WHERE state IN(1,3) AND suspended=0");
        int yg = q1(db, "SELECT COUNT(*) FROM cards WHERE state=2 AND suspended=0 AND ivl>=1 AND ivl<21");
        int mt = q1(db, "SELECT COUNT(*) FROM cards WHERE state=2 AND suspended=0 AND ivl>=21");
        int sp = q1(db, "SELECT COUNT(*) FROM cards WHERE suspended=1");
        return new int[]{nw, lr, yg, mt, sp};
    }

    // ------------------------------------------------------------- prefs

    public static String pref(Context c, String k, String def) {
        Cursor cur = Db.get(c).r().rawQuery("SELECT v FROM prefs WHERE k=?", new String[]{k});
        String v = def;
        if (cur.moveToFirst()) v = cur.getString(0);
        cur.close();
        return v;
    }

    public static int prefInt(Context c, String k, int def) {
        try {
            return Integer.parseInt(pref(c, k, String.valueOf(def)).trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static void setPref(Context c, String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v);
        Db.get(c).w().insertWithOnConflict("prefs", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ------------------------------------------------------------- ابزار ایمپورت

    public static SQLiteDatabase raw(Context c) {
        return Db.get(c).w();
    }

    public static long maxId(Context c, String table) {
        return q1(Db.get(c).r(), "SELECT COALESCE(MAX(id),0) FROM " + table);
    }

    public static boolean idExists(Context c, String table, long id) {
        return q1(Db.get(c).r(), "SELECT COUNT(*) FROM " + table + " WHERE id=" + id) > 0;
    }

    public static void insertNotetypeRaw(Context c, long id, String name, String json) {
        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("name", name);
        cv.put("json", json);
        Db.get(c).w().insertWithOnConflict("notetypes", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static void insertNoteRaw(Context c, Note n, long newId) {
        ContentValues cv = new ContentValues();
        cv.put("id", newId);
        cv.put("guid", n.guid);
        cv.put("mid", n.mid);
        cv.put("mod", n.mod);
        cv.put("flds", n.flds);
        cv.put("tags", n.tags);
        Db.get(c).w().insertWithOnConflict("notes", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static void insertCardRaw(Context c, Card k, long newId) {
        ContentValues cv = new ContentValues();
        cv.put("id", newId);
        cv.put("nid", k.nid);
        cv.put("did", k.did);
        cv.put("ord", k.ord);
        cv.put("state", k.state);
        cv.put("suspended", k.suspended ? 1 : 0);
        cv.put("buried", k.buried ? 1 : 0);
        cv.put("buried_day", k.buriedDay);
        cv.put("due", k.due);
        cv.put("ivl", k.ivl);
        cv.put("ease", k.ease);
        cv.put("step", k.step);
        cv.put("reps", k.reps);
        cv.put("lapses", k.lapses);
        cv.put("mod", k.mod);
        Db.get(c).w().insertWithOnConflict("cards", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static String joinIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return "0";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (long id : ids) {
            if (!first) sb.append(',');
            sb.append(id);
            first = false;
        }
        return sb.toString();
    }
}
