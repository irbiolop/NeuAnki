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
 * باز کردن زیپ → خواندن collection.anki2/anki21 (SQLite) → جدول‌های col/notes/cards
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
            ZipEntry col = zip.getEntry("collection.anki21");
            if (col == null) col = zip.getEntry("collection.anki2");
            if (col == null) {
                if (zip.getEntry("collection.anki21b") != null) throw new IOException("NEW_FORMAT");
                throw new IOException("NO_COLLECTION");
            }
            File colFile = new File(ctx.getCacheDir(), "collection_tmp.db");
            try {
                extract(zip, col, colFile);
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

        String modelsJson = null, decksJson = null;
        Cursor cur = sdb.rawQuery("SELECT models, decks FROM col LIMIT 1", null);
        if (cur.moveToFirst()) {
            modelsJson = cur.getString(0);
            decksJson = cur.getString(1);
        }
        cur.close();
        if (modelsJson == null || decksJson == null) throw new IOException("NO_COLLECTION");

        JSONObject models = new JSONObject(modelsJson);
        JSONObject decksJsonO = new JSONObject(decksJson);

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

        // ---------- مدیا
        ZipEntry mediaEntry = zip.getEntry("media");
        if (mediaEntry != null) {
            try {
                String mj = readAll(zip.getInputStream(mediaEntry));
                JSONObject media = new JSONObject(mj);
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
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        InputStream in = zip.getInputStream(entry);
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
}
