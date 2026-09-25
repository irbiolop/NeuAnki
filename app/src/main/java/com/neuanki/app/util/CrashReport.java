package com.neuanki.app.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** ضبط کرش‌ها در فایل تا در اجرای بعدی قابل مشاهده و ارسال باشند */
public final class CrashReport {

    private CrashReport() {}

    private static File file(Context c) {
        File d = c.getExternalFilesDir(null);
        return d == null ? new File(c.getFilesDir(), "crash.txt") : new File(d, "crash.txt");
    }

    public static void install(final Context c) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    Writer w = new FileWriter(file(c), true);
                    w.append("\n=== کرش در ")
                            .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
                            .append(" ===\n")
                            .append(android.util.Log.getStackTraceString(e));
                    w.close();
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    /** آخرین گزارش کرش یا null */
    public static String read(Context c) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(file(c)));
            StringBuilder sb = new StringBuilder();
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n++ < 150) sb.append(line).append('\n');
            r.close();
            String s = sb.toString().trim();
            return s.isEmpty() ? null : s;
        } catch (IOException e) {
            return null;
        }
    }

    public static void clear(Context c) {
        try {
            file(c).delete();
        } catch (Throwable ignored) {
        }
    }
}
