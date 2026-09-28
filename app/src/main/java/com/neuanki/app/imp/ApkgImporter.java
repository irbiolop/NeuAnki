package com.neuanki.app.imp;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.neuanki.app.db.Card;
import com.neuanki.app.db.Note;
import com.neuanki.app.db.Store;
import com.neuanki.app.util.Util;

import org.json.JSONObject;

import io.airlift.compress.zstd.ZstdInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * واردکنندهٔ فایل‌های apkg انکی:
 * باز کردن زیپ → خواندن collection.anki2/anki21/anki21b (SQLite — نسخهٔ جدید zstd فشرده است)
 * → جدول‌های col/notes/cards (+ پشتیبانی از اسکیمای ۱۸ با جدول‌های notetypes/decks)
 * → تبدیل به اسکیمای خودمان + استخراج فایل‌های مدیا (عکس/صدا).
 */
public class ApkgImporter {

    public static class Result {
        public int decks, notes, cards, media;
    }

    /** پوشهٔ مدیا که فایل‌های استخراج‌شده آنجا ذخیره می‌شوند */
    public static File mediaDir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "media");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static Result importApkg(Context ctx, Uri uri) throws Exception {
        File tmp = new File(ctx.getCacheDir(), "import_" + System.currentTimeMillis() + ".apkg");
        try {
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("NO_COLLECTION");
            copy(in, tmp);
            return doImport(ctx, tmp);
        } finally {
            tmp.delete();
        }
    }

    private static Result doImport(Context ctx, File apkg) throws Exception {
        ZipFile zip = new ZipFile(apkg);
        try {
            // فرمت‌ها: anki21 (اسکیما ۱۵/۱۶) — anki21b/anki21z (جدید، فشرده با zstd) — anki2 (قدیمی)
            ZipEntry col = zip.getEntry("collection.anki21");
            boolean zstd = false;
            if (col == null) {
                col = zip.getEntry("collection.anki21b");
                zstd = col != null;
            }
            if (col == null) {
                col = zip.getEntry("collection.anki21z");
                zstd = col != null;
            }
            if (col == null) {
                col = zip.getEntry("collection.anki2");
                zstd = false;
            }
            if (col == null) throw new IOException("NO_COLLECTION");
            File colFile = new File(ctx.getCacheDir(), "collection_tmp.db");
            try {
                extract(zip, col, colFile, zstd);
                SQLiteDatabase sdb;
                try {
                    sdb = SQLiteDatabase.openDatabase(colFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                } catch (Exception e) {
                    throw new IOException("NO_COLLECTION");
                }
                Result res = new Result();
                try {
                    importCollection(ctx, zip, sdb, res);
                } finally {
                    sdb.close();
                }
                return res;
            } finally {
                colFile.delete();
            }
        } finally {
            zip.close();
        }
    }

    private static void importCollection(Context ctx, ZipFile zip, SQLiteDatabase sdb, Result res) throws Exception {
        long now = System.currentTimeMillis();
        long today = Util.dayNum(now);

        // ---------- مدل‌ها و دک‌ها: ابتدا از JSON داخل col؛ اگر نبود از جدول‌ها (اسکیمای ۱۸+)
        JSONObject models = new JSONObject();
        JSONObject decksJsonO = new JSONObject();
        try {
            Cursor cur = sdb.rawQuery("SELECT models, decks FROM col LIMIT 1", null);
            if (cur.moveToFirst()) {
                String mj = cur.getString(0), dj = cur.getString(1);
                if (mj != null && mj.length() > 2) models = new JSONObject(mj);
                if (dj != null && dj.length() > 2) decksJsonO = new JSONObject(dj);
            }
            cur.close();
        } catch (Exception ignored) {
            // ستون‌های models/decks در اسکیمای خیلی جدید ممکن است موجود نباشند
        }
        if (models.length() == 0) models = readNotetypesFromTables(sdb);
        if (decksJsonO.length() == 0) decksJsonO = readDecksFromTables(sdb);
        if (models.length() == 0 || decksJsonO.length() == 0) throw new IOException("NO_COLLECTION");

        // نام دک‌های موجود قبل از ایمپورت
        Set<String> existingDecks = new HashSet<>();
        for (com.neuanki.app.db.Deck d : Store.decks(ctx)) existingDecks.add(d.name);

        SQLiteDatabase db = Store.raw(ctx);
        db.beginTransaction();
        try {
            Store.unburyOld(ctx, today);

            // ---------- نوع یادداشت‌ها
            Map<Long, Long> midMap = new HashMap<>();
            List<Long> ntIds = jsonKeys(models);
            long ntShift = shiftFor(ctx, "notetypes", ntIds);
            for (long orig : ntIds) {
                JSONObject mo = models.getJSONObject(String.valueOf(orig));
                long nid = orig + ntShift;
                Store.insertNotetypeRaw(ctx, nid, mo.optString("name", "بدون نام"), mo.toString());
                midMap.put(orig, nid);
            }

            // ---------- دک‌ها
            Map<Long, Long> didMap = new HashMap<>();
            List<Long> dIds = jsonKeys(decksJsonO);
            int newDecks = 0;
            for (long orig : dIds) {
                JSONObject dO = decksJsonO.getJSONObject(String.valueOf(orig));
                String name = dO.optString("name", "").trim();
                if (name.isEmpty()) continue;
                long ourId = Store.addDeck(ctx, name);
                if (!existingDecks.contains(name)) newDecks++;
                didMap.put(orig, ourId);
            }
            if (didMap.isEmpty()) {
                didMap.put(0L, Store.addDeck(ctx, "واردات‌شده"));
            }
            res.decks = newDecks;

            // ---------- یادداشت‌ها
            long shiftNotes = shiftForTable(ctx, "notes", sdb, "notes");
            Cursor ncur = sdb.rawQuery("SELECT id, guid, mid, flds, tags FROM notes", null);
            while (ncur.moveToNext()) {
                long origId = ncur.getLong(0);
                Note n = new Note();
                n.guid = ncur.getString(1) == null ? "" : ncur.getString(1);
                long origMid = ncur.getLong(2);
                Long mappedMid = midMap.get(origMid);
                if (mappedMid == null) {
                    if (!midMap.isEmpty()) mappedMid = midMap.values().iterator().next();
                    else continue;
                }
                n.mid = mappedMid;
                n.flds = ncur.getString(3) == null ? "" : ncur.getString(3);
                n.tags = ncur.getString(4) == null ? "" : ncur.getString(4);
                n.mod = now / 1000L;
                Store.insertNoteRaw(ctx, n, origId + shiftNotes);
                res.notes++;
            }
            ncur.close();

            // ---------- کارت‌ها
            long shiftCards = shiftForTable(ctx, "cards", sdb, "cards");
            Long fallbackDeck = didMap.values().iterator().next();
            Cursor ccur = sdb.rawQuery(
                    "SELECT id, nid, did, ord, type, queue, due, ivl, factor, reps, lapses FROM cards", null);
            while (ccur.moveToNext()) {
                Card k = new Card();
                k.id = ccur.getLong(0) + shiftCards;
                k.nid = ccur.getLong(1) + shiftNotes;
                Long dId = didMap.get(ccur.getLong(2));
                k.did = dId == null ? fallbackDeck : dId;
                k.ord = ccur.getInt(3);
                int type = ccur.getInt(4);
                int queue = ccur.getInt(5);
                long due = ccur.getLong(6);
                k.suspended = (queue == -1);
                k.buried = (queue == -2 || queue == -3);
                int state = queue >= 0 ? queue : Math.max(0, Math.min(type, 3));
                k.state = state;
                if (state == Card.STATE_LEARN || state == Card.STATE_RELEARN) {
                    k.due = due * 1000L; // در اسکیمای قدیمی انکی ثانیه است
                } else {
                    k.due = due;
                }
                k.ivl = Math.max(0, ccur.getInt(7));
                int factor = ccur.getInt(8);
                k.ease = factor <= 0 ? 2500 : factor;
                k.reps = Math.max(0, ccur.getInt(9));
                k.lapses = Math.max(0, ccur.getInt(10));
                k.mod = now / 1000L;
                Store.insertCardRaw(ctx, k, k.id);
                res.cards++;
            }
            ccur.close();

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        // ---------- مدیا (در فرمت جدید، مانیفست هم zstd است)
        ZipEntry mediaEntry = zip.getEntry("media");
        if (mediaEntry != null) {
            try {
                byte[] raw = readBytes(zip.getInputStream(mediaEntry));
                JSONObject media;
                try {
                    media = new JSONObject(new String(raw, "UTF-8"));
                } catch (Exception jsonFail) {
                    // فرمت جدید: مانیفست فشردهٔ zstd
                    InputStream dz = new ZstdInputStream(new ByteArrayInputStream(raw));
                    media = new JSONObject(readAll(dz));
                }
                File mediaDir = mediaDir(ctx);
                Iterator<String> it = media.keys();
                while (it.hasNext()) {
                    String idx = it.next();
                    String realName = sanitize(media.optString(idx, ""));
                    if (realName.isEmpty()) continue;
                    ZipEntry me = zip.getEntry(idx);
                    if (me == null) continue;
                    extract(zip, me, new File(mediaDir, realName));
                    res.media++;
                }
            } catch (Exception ignored) {
                // مدیا حیاتی نیست؛ کارت‌ها بدون عکس/صدا وارد شده‌اند
            }
        }
    }

    /** اگر اسکیمای پایگاه دادهٔ جدید باشد و col.models خالی، نوع یادداشت‌ها از جدول‌ها خوانده می‌شود */
    private static JSONObject readNotetypesFromTables(SQLiteDatabase sdb) {
        JSONObject out = new JSONObject();
        try {
            Map<Long, JSONObject> nts = new HashMap<>();
            Cursor c1 = sdb.rawQuery("SELECT id, name, type, config FROM notetypes", null);
            while (c1.moveToNext()) {
                try {
                    long id = c1.getLong(0);
                    JSONObject o = new JSONObject();
                    o.put("name", c1.getString(1) == null ? "بدون نام" : c1.getString(1));
                    o.put("type", c1.getInt(2));
                    String cfg = c1.getString(3);
                    if (cfg != null && cfg.length() > 2) {
                        JSONObject co = new JSONObject(cfg);
                        if (co.has("css")) o.put("css", co.optString("css"));
                        if (co.has("latexPre")) o.put("latexPre", co.optString("latexPre"));
                        if (co.has("latexPost")) o.put("latexPost", co.optString("latexPost"));
                    }
                    nts.put(id, o);
                } catch (Exception ignored) {}
            }
            c1.close();

            Cursor c2 = sdb.rawQuery("SELECT ntid, ord, name, config FROM fields", null);
            while (c2.moveToNext()) {
                try {
                    JSONObject o = nts.get(c2.getLong(0));
                    if (o == null) continue;
                    if (!o.has("flds")) o.put("flds", new org.json.JSONArray());
                    JSONObject f = new JSONObject();
                    f.put("ord", c2.getInt(1));
                    f.put("name", c2.getString(2) == null ? "فیلد" : c2.getString(2));
                    String cfg = c2.getString(3);
                    if (cfg != null && cfg.length() > 2) {
                        JSONObject co = new JSONObject(cfg);
                        f.put("rtl", co.optBoolean("rtl", false));
                        f.put("size", co.optInt("size", 20));
                        f.put("font", co.optString("font", "Arial"));
                    }
                    o.getJSONArray("flds").put(f);
                } catch (Exception ignored) {}
            }
            c2.close();

            Cursor c3 = sdb.rawQuery("SELECT ntid, ord, name, config FROM templates", null);
            while (c3.moveToNext()) {
                try {
                    JSONObject o = nts.get(c3.getLong(0));
                    if (o == null) continue;
                    if (!o.has("tmpls")) o.put("tmpls", new org.json.JSONArray());
                    JSONObject t = new JSONObject();
                    t.put("ord", c3.getInt(1));
                    t.put("name", c3.getString(2) == null ? "کارت" : c3.getString(2));
                    String cfg = c3.getString(3);
                    if (cfg != null && cfg.length() > 2) {
                        JSONObject co = new JSONObject(cfg);
                        t.put("qfmt", co.optString("qfmt", "{{Front}}"));
                        t.put("afmt", co.optString("afmt", "{{Back}}"));
                        if (co.has("bqfmt")) t.put("bqfmt", co.optString("bqfmt"));
                        if (co.has("bafmt")) t.put("bafmt", co.optString("bafmt"));
                    }
                    o.getJSONArray("tmpls").put(t);
                } catch (Exception ignored) {}
            }
            c3.close();

            for (Map.Entry<Long, JSONObject> e : nts.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** اگر col.decks خالی/ناموجود بود، دک‌ها از جدول خوانده می‌شوند */
    private static JSONObject readDecksFromTables(SQLiteDatabase sdb) {
        JSONObject out = new JSONObject();
        try {
            Cursor cur = sdb.rawQuery("SELECT id, name FROM decks", null);
            while (cur.moveToNext()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("name", cur.getString(1) == null ? "" : cur.getString(1));
                    out.put(String.valueOf(cur.getLong(0)), o);
                } catch (Exception ignored) {}
            }
            cur.close();
        } catch (Exception ignored) {
        }
        return out;
    }

    /** اگر شناسه‌های وارداتی با شناسه‌های فعلی تداخل دارند، آفست برمی‌گرداند */
    private static long shiftForTable(Context ctx, String table, SQLiteDatabase src, String srcTable) {
        long maxOurs = Store.maxId(ctx, table);
        Cursor cur = src.rawQuery("SELECT COALESCE(MIN(id),0), COALESCE(MAX(id),0) FROM " + srcTable, null);
        long min = 0, max = 0;
        if (cur.moveToFirst()) {
            min = cur.getLong(0);
            max = cur.getLong(1);
        }
        cur.close();
        if (min <= 0 || max < min) return 0;
        if (min > maxOurs) return 0;
        return (maxOurs + 1) - min;
    }

    private static long shiftFor(Context ctx, String table, List<Long> ids) {
        if (ids.isEmpty()) return 0;
        long maxOurs = Store.maxId(ctx, table);
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        boolean collide = false;
        for (long id : ids) {
            min = Math.min(min, id);
            max = Math.max(max, id);
            if (Store.idExists(ctx, table, id)) collide = true;
        }
        if (!collide) return 0;
        return (maxOurs + 1) - min;
    }

    private static List<Long> jsonKeys(JSONObject o) {
        List<Long> out = new ArrayList<>();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            try {
                out.add(Long.parseLong(it.next()));
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[/\\\\]", "_").replaceAll("^\\.+", "_");
    }

    private static void copy(InputStream in, File out) throws IOException {
        FileOutputStream fo = new FileOutputStream(out);
        byte[] buf = new byte[65536];
        int n;
        try {
            while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
        } finally {
            try { in.close(); } catch (Exception ignored) {}
            try { fo.close(); } catch (Exception ignored) {}
        }
    }

    private static void extract(ZipFile zip, ZipEntry entry, File out) throws IOException {
        extract(zip, entry, out, false);
    }

    private static void extract(ZipFile zip, ZipEntry entry, File out, boolean zstd) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        InputStream in = zip.getInputStream(entry);
        if (zstd) in = new ZstdInputStream(in);
        copy(in, out);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        try { in.close(); } catch (Exception ignored) {}
        return new String(bo.toByteArray(), "UTF-8");
    }

    private static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        try { in.close(); } catch (Exception ignored) {}
        return bo.toByteArray();
    }
}
