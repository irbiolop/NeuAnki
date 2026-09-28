package com.neuanki.app.tpl;

import com.neuanki.app.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * موتور قالب کارت‌های انکی — خالص (بدون وابستگی به اندروید).
 * پشتیبانی از: {{Field}}، {{text:Field}}، {{type:Field}}، {{hint:Field}}،
 * {{cloze:Field}}، {{FrontSide}}، {{Card}}، {{Deck}}، {{Tags}} و بخش‌های شرطی
 * {{#Field}}...{{/Field}} و {{^Field}}...{{/Field}}
 */
public class TemplateEngine {

    public static class Data {
        public String qfmt = "";
        public String afmt = "";
        public String css = "";
        public String[] fieldNames = new String[0];
        public String[] fieldValues = new String[0];
        public boolean isCloze;
        /** اندیس قالب؛ برای کلوز، شمارهٔ کلوز منهای ۱ */
        public int ord;
        public String deckName = "";
        public String cardName = "";
        public String tags = "";
        /** css فونت برای @font-face (اختیاری) */
        public String fontCss = "";
        /** بلوک نتیجهٔ تایپ برای پشت کارت */
        public String typeHtml = "";
    }

    /** نام فیلدی که با {{type:...}} درخواست شده (null = بدون تایپ) */
    public static String lastTypeField;

    private static final Pattern P_COND = Pattern.compile("\\{\\{#([^}]+)\\}\\}([\\s\\S]*?)\\{\\{/\\s*[^}]*\\}\\}");
    private static final Pattern P_COND_NOT = Pattern.compile("\\{\\{\\^([^}]+)\\}\\}([\\s\\S]*?)\\{\\{/\\s*[^}]*\\}\\}");
    private static final Pattern P_CLOZE_TAG = Pattern.compile("\\{\\{cloze:([^}]+)\\}\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CLOZE = Pattern.compile("\\{\\{c(\\d+)::(.*?)(?:::([^}]*))?\\}\\}", Pattern.DOTALL);
    private static final Pattern P_TOKEN = Pattern.compile("\\{\\{([^{}]+)\\}\\}");

    public static String front(Data d) {
        lastTypeField = null;
        return page(d, expand(d, d.qfmt, null, true));
    }

    public static String back(Data d) {
        lastTypeField = null;
        String fs = expand(d, d.qfmt, null, true);
        String body = expand(d, d.afmt, fs, false);
        return page(d, body);
    }

    // ------------------------------------------------------------------

    private static String expand(Data d, String tmpl, String frontSide, boolean isFront) {
        if (tmpl == null) tmpl = "";
        Map<String, String> fm = fieldMap(d);
        String out = applyConditionals(tmpl, fm);
        out = applyCloze(d, out, fm, isFront);
        out = applyTokens(d, out, fm, frontSide, isFront);
        return out;
    }

    private static Map<String, String> fieldMap(Data d) {
        Map<String, String> m = new HashMap<>();
        int n = d.fieldNames == null ? 0 : d.fieldNames.length;
        for (int i = 0; i < n; i++) {
            String name = d.fieldNames[i];
            String val = (d.fieldValues != null && i < d.fieldValues.length && d.fieldValues[i] != null)
                    ? d.fieldValues[i] : "";
            m.put(name.toLowerCase().trim(), val);
        }
        return m;
    }

    private static String getField(Map<String, String> fm, String name) {
        if (name == null) return "";
        String v = fm.get(name.toLowerCase().trim());
        return v == null ? "" : v;
    }

    /** {{#Field}}...{{/Field}} و {{^Field}}...{{/Field}} */
    private static String applyConditionals(String in, Map<String, String> fm) {
        String s = in;
        for (int pass = 0; pass < 8; pass++) {
            boolean changed = false;

            Matcher m = P_COND.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String name = m.group(1).trim();
                String inner = m.group(2);
                String rep = getField(fm, name).trim().isEmpty() ? "" : inner;
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
                changed = true;
            }
            m.appendTail(sb);
            s = sb.toString();

            m = P_COND_NOT.matcher(s);
            sb = new StringBuilder();
            while (m.find()) {
                String name = m.group(1).trim();
                String inner = m.group(2);
                String rep = getField(fm, name).trim().isEmpty() ? inner : "";
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
                changed = true;
            }
            m.appendTail(sb);
            s = sb.toString();

            if (!changed) break;
        }
        return s;
    }

    private static String applyCloze(Data d, String in, Map<String, String> fm, boolean isFront) {
        if (!d.isCloze) return in;
        Matcher mt = P_CLOZE_TAG.matcher(in);
        if (!mt.find()) return in;

        StringBuilder sb = new StringBuilder();
        int last = 0;
        mt.reset();
        while (mt.find()) {
            sb.append(in, last, mt.start());
            String fieldValue = getField(fm, mt.group(1));
            sb.append(transformCloze(fieldValue, isFront, d.ord + 1));
            last = mt.end();
        }
        sb.append(in.substring(last));
        return sb.toString();
    }

    private static String transformCloze(String fieldValue, boolean isFront, int activeNum) {
        if (fieldValue == null || fieldValue.isEmpty()) return "";
        if (!fieldValue.contains("{{c")) return fieldValue;
        Matcher m = P_CLOZE.matcher(fieldValue);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String rep;
            if (isFront) {
                rep = (m.group(3) != null && !m.group(3).isEmpty())
                        ? "[" + m.group(3) + "]" : "[...]";
            } else {
                int num;
                try { num = Integer.parseInt(m.group(1)); } catch (Exception e) { num = -1; }
                String text = m.group(2) == null ? "" : m.group(2);
                rep = (num == activeNum)
                        ? "<span class=\"cloze\">" + text + "</span>"
                        : text;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String applyTokens(Data d, String in, Map<String, String> fm, String frontSide, boolean isFront) {
        String s = in;
        for (int pass = 0; pass < 5; pass++) {
            Matcher m = P_TOKEN.matcher(s);
            StringBuilder sb = new StringBuilder();
            boolean any = false;
            while (m.find()) {
                String tok = m.group(1).trim();
                String low = tok.toLowerCase();
                String rep;
                if (low.startsWith("type:")) {
                    lastTypeField = tok.substring(5).trim();
                    rep = "";
                } else if (low.startsWith("text:")) {
                    rep = Util.stripHtml(getField(fm, tok.substring(5)));
                } else if (low.startsWith("hint:")) {
                    rep = hintHtml(getField(fm, tok.substring(5)));
                } else if (low.equalsIgnoreCase("frontside")) {
                    rep = frontSide == null ? "" : frontSide;
                } else if (low.equalsIgnoreCase("card")) {
                    rep = d.cardName == null ? "" : d.cardName;
                } else if (low.equalsIgnoreCase("deck")) {
                    rep = d.deckName == null ? "" : d.deckName;
                } else if (low.equalsIgnoreCase("tags")) {
                    rep = d.tags == null ? "" : d.tags;
                } else if (fm.containsKey(low)) {
                    rep = getField(fm, tok);
                } else {
                    rep = "";
                }
                any = true;
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            s = sb.toString();
            if (!any) break;
        }
        return s;
    }

    private static String hintHtml(String value) {
        String v = Util.stripHtml(value);
        if (v.isEmpty()) return "";
        return "<span class=\"hint\"><a href=\"javascript:void(0)\" "
                + "onclick=\"this.parentNode.classList.add('open')\">نمایش راهنما</a>"
                + "<span class=\"htxt\">" + Util.escapeHtml(v) + "</span></span>";
    }

    private static String page(Data d, String body) {
        StringBuilder h = new StringBuilder(4096);
        h.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">");
        h.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=3.0\">");
        h.append("<style>").append(BASE_CSS);
        if (d.fontCss != null && !d.fontCss.isEmpty()) h.append(d.fontCss);
        h.append("</style>");
        if (d.css != null && !d.css.trim().isEmpty()) {
            h.append("<style>").append(d.css).append("</style>");
        }
        h.append("</head><body>");
        h.append("<div class=\"card night_mode\" dir=\"auto\">");
        h.append(body);
        if (d.typeHtml != null && !d.typeHtml.isEmpty()) h.append(d.typeHtml);
        h.append("</div></body></html>");
        return h.toString();
    }

    private static final String BASE_CSS =
            "body{background:#23272B;color:#E7EAED;font-size:21px;line-height:1.8;"
            + "word-wrap:break-word;overflow-wrap:break-word;padding:10px 6px;"
            + "-webkit-text-size-adjust:100%;font-family:'Vazirmatn',sans-serif}"
            + ".card{color:#E7EAED}"
            + "a{color:#34D399}"
            + "img{max-width:100%;height:auto;border-radius:12px}"
            + "table{border-collapse:collapse;width:100%}"
            + "td,th{border:1px solid #3A4249;padding:4px 8px}"
            + ".cloze{color:#34D399;font-weight:700}"
            + ".hint a{color:#34D399;text-decoration:none;font-weight:700;cursor:pointer}"
            + ".hint .htxt{display:none;color:#E7EAED}"
            + ".hint.open .htxt{display:inline}"
            + ".typeGood{color:#34D399}.typeBad{color:#F87171}";
}
