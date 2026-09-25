package com.neuanki.app.db;

/** مدل کارت — بدون وابستگی به اندروید */
public class Card {
    public static final int STATE_NEW = 0;
    public static final int STATE_LEARN = 1;
    public static final int STATE_REVIEW = 2;
    public static final int STATE_RELEARN = 3;

    public long id;
    public long nid;
    public long did;
    public int ord;
    /** 0 جدید، 1 در حال یادگیری، 2 مرور، 3 یادگیری مجدد */
    public int state = STATE_NEW;
    public boolean suspended;
    public boolean buried;
    /** شمارهٔ روزِ دفن — بعد از آن روز به‌طور خودکار از دفن خارج می‌شود */
    public long buriedDay;
    /**
     * برای کارت‌های یادگیری: زمان دقیق (میلی‌ثانیه).
     * برای مرور: شمارهٔ روز. برای جدید: ترتیب.
     */
    public long due;
    /** فاصلهٔ مرور به روز */
    public int ivl;
    /** ضریب آسانی در هزارم (2500 = 2.5) */
    public int ease = 2500;
    public int step;
    public int reps;
    public int lapses;
    public long mod;

    public Card copy() {
        Card c = new Card();
        c.id = id; c.nid = nid; c.did = did; c.ord = ord;
        c.state = state; c.suspended = suspended; c.buried = buried; c.buriedDay = buriedDay;
        c.due = due; c.ivl = ivl; c.ease = ease;
        c.step = step; c.reps = reps; c.lapses = lapses; c.mod = mod;
        return c;
    }
}
