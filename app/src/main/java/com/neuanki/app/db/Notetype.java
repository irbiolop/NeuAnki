package com.neuanki.app.db;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * نوع یادداشت (Notetype / Model).
 * json خام همان مدل انکی است و در لحظهٔ نیاز تجزیه می‌شود.
 */
public class Notetype {
    public long id;
    public String name = "";
    /** 0 = استاندارد، 1 = کلوز */
    public int type;
    public String json = "{}";

    public int templateCount() {
        try {
            JSONObject o = new JSONObject(json);
            JSONArray t = o.optJSONArray("tmpls");
            return t == null ? 1 : Math.max(1, t.length());
        } catch (Exception e) {
            return 1;
        }
    }

    public int fieldCount() {
        try {
            JSONObject o = new JSONObject(json);
            JSONArray f = o.optJSONArray("flds");
            return f == null ? 0 : f.length();
        } catch (Exception e) {
            return 0;
        }
    }
}
