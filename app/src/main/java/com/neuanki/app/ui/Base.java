package com.neuanki.app.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/** پایهٔ همهٔ اکتیویتی‌ها — فارسی RTL اجباری */
public class Base extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context base) {
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(new Locale("fa"));
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }
}
