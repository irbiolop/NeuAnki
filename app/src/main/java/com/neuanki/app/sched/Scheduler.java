package com.neuanki.app.sched;

import com.neuanki.app.db.Card;
import com.neuanki.app.util.Util;

import java.util.Random;

/**
 * زمان‌بند SM-2 (هم‌خانوادهٔ الگوریتم انکی):
 * مراحل یادگیری، فارغ‌التحصیلی، لغزش، ضریب آسانی و fuzz.
 * خالص و بدون وابستگی به اندروید تا قابل تست باشد.
 */
public class Scheduler {

    public static final int AGAIN = 1, HARD = 2, GOOD = 3, EASY = 4;

    /** مراحل یادگیری به دقیقه */
    public static int[] steps = {1, 10};
    /** فاصلهٔ فارغ‌التحصیلی بعد از مراحل یادگیری (روز) */
    public static int graduatingIvl = 1;
    /** فاصلهٔ پاسخ آسان در حالت یادگیری (روز) */
    public static int easyIvl = 4;
    /** ضریب فاصلهٔ جدید بعد از لغزش (0 تا 1) */
    public static double lapseMult = 0.0;

    private static final Random RND = new Random();

    public static void configure(int[] stepsMin, int graduatingDays, int easyDays) {
        if (stepsMin != null && stepsMin.length > 0) steps = stepsMin;
        if (graduatingDays >= 1) graduatingIvl = graduatingDays;
        if (easyDays >= 1) easyIvl = easyDays;
    }

    /** اعمال پاسخ روی کارت (mutate) */
    public static void answer(Card c, int rating, long now) {
        long today = Util.dayNum(now);
        c.reps++;
        c.mod = now / 1000L;

        if (c.state == Card.STATE_NEW || c.state == Card.STATE_LEARN) {
            if (c.state == Card.STATE_NEW) {
                c.state = Card.STATE_LEARN;
                c.step = 0;
            }
            if (rating == AGAIN) {
                c.step = 0;
                c.due = now + steps[0] * 60000L;
            } else if (rating == HARD) {
                int s = Math.min(c.step, steps.length - 1);
                c.due = now + steps[s] * 60000L;
            } else if (rating == GOOD) {
                if (c.step + 1 < steps.length) {
                    c.step++;
                    c.due = now + steps[c.step] * 60000L;
                } else {
                    graduate(c, today, graduatingIvl);
                }
            } else { // EASY
                graduate(c, today, easyIvl);
            }

        } else if (c.state == Card.STATE_REVIEW) {
            if (rating == AGAIN) {
                c.ease = Math.max(1300, c.ease - 200);
                c.lapses++;
                c.state = Card.STATE_RELEARN;
                c.step = 0;
                c.due = now + steps[0] * 60000L;
            } else {
                int last = Math.max(1, c.ivl);
                int nivl;
                if (rating == HARD) {
                    c.ease = Math.max(1300, c.ease - 150);
                    nivl = (int) Math.round(last * 1.2);
                } else if (rating == GOOD) {
                    nivl = (int) Math.round(last * (c.ease / 1000.0));
                } else {
                    c.ease = Math.min(3500, c.ease + 150);
                    nivl = (int) Math.round(last * (c.ease / 1000.0) * 1.3);
                }
                nivl = fuzz(nivl);
                nivl = Math.max(1, Math.min(nivl, 36500));
                c.ivl = nivl;
                c.state = Card.STATE_REVIEW;
                c.due = today + nivl;
                c.step = 0;
            }

        } else { // RELEARN
            if (rating == AGAIN) {
                c.step = 0;
                c.due = now + steps[0] * 60000L;
            } else if (rating == HARD) {
                int s = Math.min(c.step, steps.length - 1);
                c.due = now + steps[s] * 60000L;
            } else if (rating == GOOD) {
                int nivl = Math.max(1, (int) Math.round(Math.max(1, c.ivl) * lapseMult));
                c.state = Card.STATE_REVIEW;
                c.ivl = nivl;
                c.due = today + nivl;
                c.step = 0;
            } else { // EASY
                c.state = Card.STATE_REVIEW;
                c.ivl = easyIvl;
                c.due = today + easyIvl;
                c.step = 0;
            }
        }
    }

    private static void graduate(Card c, long today, int ivlDays) {
        c.state = Card.STATE_REVIEW;
        c.ivl = Math.max(1, ivlDays);
        c.due = today + c.ivl;
        c.step = 0;
    }

    /** نویز ±۵٪ برای پخش‌شدن مرورها */
    private static int fuzz(int ivl) {
        if (ivl < 3) return ivl;
        int spread = Math.max(1, ivl / 20);
        return ivl + RND.nextInt(2 * spread + 1) - spread;
    }

    /** پیش‌نمایش فاصلهٔ هر پاسخ بدون تغییر کارت */
    public static String preview(Card c, int rating, long now) {
        Card t = c.copy();
        answer(t, rating, now);
        return Util.fmtDue(t, now);
    }
}
