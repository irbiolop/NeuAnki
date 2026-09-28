package com.neuanki.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import com.adivery.sdk.Adivery;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class App extends Application {

    public static final String CHANNEL_REMINDERS = "reminders";

    /** شناسهٔ اپلیکیشن در ادیوری */
    public static final String ADIVERY_APP_ID = "0b2ebfcf-50e9-43a5-b876-7a7b045915be";
    /** شناسهٔ تبلیغ‌گاه همسان در ادیوری */
    public static final String ADIVERY_NATIVE_PLACEMENT = "9eeae552-dd92-47e1-8831-32a902578f90";

    @Override
    protected void attachBaseContext(Context base) {
        // رابط همیشه فارسی و راست‌چین
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(new Locale("fa"));
        super.attachBaseContext(base.createConfigurationContext(cfg));
        com.neuanki.app.util.CrashReport.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        copyFonts();
        initAds();
    }

    /** راه‌اندازی یک‌بارهٔ SDK ادیوری برای کل پروسه */
    private void initAds() {
        try {
            Adivery.setLoggingEnabled(false);
            Adivery.configure(this, ADIVERY_APP_ID);
        } catch (Exception ignored) {
            // تبلیغ حیاتی نیست؛ اگر SDK آماده نبود برنامه بدون آن کار می‌کند
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_REMINDERS,
                    getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription(getString(R.string.notif_channel));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    /** کپی فونت برای استفادهٔ WebView در کارت‌ها */
    private void copyFonts() {
        try {
            File dir = new File(getFilesDir(), "fonts");
            if (!dir.exists()) dir.mkdirs();
            String[][] pairs = {
                    {"fonts/vazirmatn_regular.ttf", "Vazirmatn-Regular.ttf"},
                    {"fonts/vazirmatn_bold.ttf", "Vazirmatn-Bold.ttf"}
            };
            for (String[] p : pairs) {
                File out = new File(dir, p[1]);
                if (out.exists()) continue;
                InputStream in = getAssets().open(p[0]);
                FileOutputStream fo = new FileOutputStream(out);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                in.close();
                fo.close();
            }
        } catch (IOException ignored) {
        }
    }
}
