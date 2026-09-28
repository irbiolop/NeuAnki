package com.neuanki.app.util;

import com.neuanki.app.db.Card;

/** ابزارهای خالص (بدون وابستگی به اندروید) — اعداد فارسی، تاریخ و رشته */
public class Util {

    private static final String[] FA = {"۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"};

    /** تبدیل ارقام لاتین به فارسی */
    public static String fa(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(FA[ch - '0']);
            else sb.append(ch);
        }
        return sb.toString();
    }

    /** شمارهٔ روز از ۱۹۷۰/۰۱/۰۱ */
    public static long dayNum(long ms) {
        return ms / 86400000L;
    }

    /** متن فاصلهٔ بعدی کارت برای دکمه‌ها */
    public static String fmtDue(Card c, long now) {
        long today = dayNum(now);
        switch (c.state) {
            case Card.STATE_LEARN:
            case Card.STATE_RELEARN: {
                long mins = (c.due - now + 59999) / 60000L;
                if (mins < 1) mins = 1;
                if (mins < 60) return fa(mins) + " دقیقه";
                long hrs = (mins + 59) / 60;
                if (hrs < 24) return fa(hrs) + " ساعت";
                return fa((hrs + 23) / 24) + " روز";
            }
            case Card.STATE_REVIEW: {
                long d = c.due - today;
                if (d <= 0) return "امروز";
                if (d < 31) return fa(d) + " روز";
                return fa((d + 29) / 30) + " ماه";
            }
            default:
                return "جدید";
        }
    }

    /** حذف تگ‌های HTML برای نمایش خلاصه */
    public static String stripHtml(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<br\\s*/?>", " ")
                .replaceAll("(?is)</?(p|div|li|tr|h[1-6])[^>]*>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&laquo;", "«").replace("&raquo;", "»");
        return s.replaceAll("\\s+", " ").trim();
    }

    /** ایمن‌سازی برای درج در HTML */
    public static String escapeHtml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /** نتیجهٔ مقایسهٔ تایپ کاربر با پاسخ درست، به‌صورت HTML رنگی */
    public static String typeCompareHtml(String target, String typed) {
        String t = stripHtml(target == null ? "" : target).trim();
        String u = typed == null ? "" : typed.trim();
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='typeresult' style='margin-top:14px'>");
        sb.append("<div class='typeGood' style='font-size:24px;font-weight:700'>").append(escapeHtml(t)).append("</div>");
        if (!u.isEmpty()) {
            sb.append("<div style='font-size:22px;margin-top:6px'>");
            for (int i = 0; i < u.length(); i++) {
                boolean ok = i < t.length() && t.charAt(i) == u.charAt(i);
                sb.append(ok ? "<span class='typeGood'>" : "<span class='typeBad'>")
                        .append(escapeHtml(String.valueOf(u.charAt(i))))
                        .append("</span>");
            }
            sb.append("</div>");
            if (!u.equals(t)) {
                sb.append("<div class='typeBad' style='margin-top:4px;font-size:13px'>تایپ شده با پاسخ مطابقت ندارد</div>");
            }
        }
        sb.append("</div>");
        return sb.toString();
    }
}
