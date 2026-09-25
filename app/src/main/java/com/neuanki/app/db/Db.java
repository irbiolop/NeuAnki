package com.neuanki.app.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** مدیریت دیتابیس SQLite محلی */
public class Db extends SQLiteOpenHelper {

    private static volatile Db INSTANCE;

    public static Db get(Context c) {
        if (INSTANCE == null) {
            synchronized (Db.class) {
                if (INSTANCE == null) INSTANCE = new Db(c.getApplicationContext());
            }
        }
        return INSTANCE;
    }

    private Db(Context c) {
        super(c, "neuanki.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE decks(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)");
        db.execSQL("CREATE TABLE notetypes(id INTEGER PRIMARY KEY, name TEXT NOT NULL, json TEXT NOT NULL)");
        db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY, guid TEXT, mid INTEGER NOT NULL, "
                + "mod INTEGER DEFAULT 0, flds TEXT NOT NULL, tags TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE cards(id INTEGER PRIMARY KEY, nid INTEGER NOT NULL, did INTEGER NOT NULL, "
                + "ord INTEGER NOT NULL DEFAULT 0, state INTEGER NOT NULL DEFAULT 0, "
                + "suspended INTEGER NOT NULL DEFAULT 0, buried INTEGER NOT NULL DEFAULT 0, "
                + "buried_day INTEGER NOT NULL DEFAULT 0, due INTEGER NOT NULL DEFAULT 0, "
                + "ivl INTEGER NOT NULL DEFAULT 0, ease INTEGER NOT NULL DEFAULT 2500, "
                + "step INTEGER NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, "
                + "lapses INTEGER NOT NULL DEFAULT 0, mod INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_cards_did ON cards(did)");
        db.execSQL("CREATE INDEX idx_cards_due ON cards(state, due)");
        db.execSQL("CREATE INDEX idx_cards_nid ON cards(nid)");
        db.execSQL("CREATE TABLE revlog(id INTEGER PRIMARY KEY, cid INTEGER NOT NULL, ts INTEGER NOT NULL, "
                + "rating INTEGER NOT NULL, ivl INTEGER DEFAULT 0, lastIvl INTEGER DEFAULT 0, "
                + "timeMs INTEGER DEFAULT 0, prev_state INTEGER DEFAULT 0, prev_due INTEGER DEFAULT 0, "
                + "prev_ivl INTEGER DEFAULT 0, prev_ease INTEGER DEFAULT 2500, prev_step INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE prefs(k TEXT PRIMARY KEY, v TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // نسخهٔ ۱ — فعلاً مهاجرتی وجود ندارد
    }
}
