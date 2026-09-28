package com.neuanki.app.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.neuanki.app.R;
import com.neuanki.app.util.CrashReport;

import java.util.Locale;

/** پایهٔ همهٔ اکتیویتی‌ها — فارسی RTL اجباری + نمایش گزارش کرش */
public class Base extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context base) {
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(new Locale("fa"));
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        offerCrashReport();
    }

    /** اگر کرش ثبت شده باشد، دکمهٔ ارسال گزارش نشان بده */
    private void offerCrashReport() {
        final String trace = CrashReport.read(this);
        if (trace == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_title)
                .setMessage(trace)
                .setPositiveButton(R.string.crash_send, (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, "NeuAnki Crash Report");
                    i.putExtra(Intent.EXTRA_TEXT, trace);
                    try {
                        startActivity(Intent.createChooser(i, getString(R.string.crash_send)));
                    } catch (Exception ignored) {
                    }
                    CrashReport.clear(this);
                })
                .setNegativeButton(R.string.crash_later, (d, w) -> CrashReport.clear(this))
                .show();
    }
}
