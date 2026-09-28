package com.neuanki.app.db;

/** مدل یادداشت */
public class Note {
    public long id;
    public String guid = "";
    public long mid;
    public long mod;
    /** فیلدها با \u001f از هم جدا شده‌اند */
    public String flds = "";
    public String tags = "";

    public String[] fieldsArray() {
        if (flds == null || flds.isEmpty()) return new String[0];
        return flds.split("\u001f", -1);
    }

    public String joinedFields(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append('\u001f');
            sb.append(arr[i]);
        }
        return sb.toString();
    }
}
