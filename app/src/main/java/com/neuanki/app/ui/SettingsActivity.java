package com.neuanki.app.ui;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.SwitchCompat;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.neuanki.app.R;
import com.neuanki.app.db.Store;
import com.neuanki.app.remind.Reminders;
import com.neuanki.app.util.Util;
import com.neuanki.app.widget.NeumorphIconButton;

/** تنظیمات: سقف روزانه، مراحل یادگیری و یادآور */
public class SettingsActivity extends Base {

    private static final int REQ_NOTIF = 11;

    private TextView tvNewLimit, tvRevLimit, tvSteps;
    private SwitchCompat swReminder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        NeumorphIconButton btnBack = findViewById(R.id.btnBackSt);
        LinearLayout rowNew = findViewById(R.id.rowNewLimit);
        LinearLayout rowRev = findViewById(R.id.rowRevLimit);
        LinearLayout rowSteps = findViewById(R.id.rowSteps);
        LinearLayout rowAbout = findViewById(R.id.rowAbout);
        swReminder = findViewById(R.id.swReminder);
        tvNewLimit = findViewById(R.id.tvNewLimit);
        tvRevLimit = findViewById(R.id.tvRevLimit);
        tvSteps = findViewById(R.id.tvSteps);

        btnBack.setOnClickListener(v -> finish());
        rowNew.setOnClickListener(v -> askNumber(getString(R.string.settings_new_limit), "newPerDay", 20));
        rowRev.setOnClickListener(v -> askNumber(getString(R.string.settings_rev_limit), "revPerDay", 200));
        rowSteps.setOnClickListener(v -> askSteps());
        rowAbout.setOnClickListener(v ->
                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.settings_about)
                        .setMessage(R.string.settings_about_desc)
                        .setPositiveButton(R.string.ok, null)
                        .show());

        swReminder.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) enableReminder();
            else {
                Reminders.cancel(this);
                Store.setPref(this, "remindOn", "0");
                Toast.makeText(this, R.string.settings_reminder_off, Toast.LENGTH_SHORT).show();
            }
        });

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        tvNewLimit.setText(Util.fa(Store.prefInt(this, "newPerDay", 20)) + " " + getString(R.string.day));
        tvRevLimit.setText(Util.fa(Store.prefInt(this, "revPerDay", 200)) + " " + getString(R.string.day));
        tvSteps.setText(Store.pref(this, "steps", "1,10"));
        swReminder.setOnCheckedChangeListener(null);
        swReminder.setChecked(Store.prefInt(this, "remindOn", 0) == 1);
        swReminder.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) enableReminder();
            else {
                Reminders.cancel(this);
                Store.setPref(this, "remindOn", "0");
                Toast.makeText(this, R.string.settings_reminder_off, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void askNumber(String title, String key, int def) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(String.valueOf(Store.prefInt(this, key, def)));
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setTextColor(0xFFE7EAED);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        et.setPadding(p, p / 2, p, p / 2);
        et.setBackground(getDrawable(R.drawable.bg_inset));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(p, 0, p, 0);
        wrap.addView(et, lp);
        new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton(R.string.save, (dlg, w) -> {
                    try {
                        int v = Integer.parseInt(et.getText().toString().trim());
                        if (v < 0) v = 0;
                        Store.setPref(this, key, String.valueOf(v));
                        refresh();
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void askSteps() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(Store.pref(this, "steps", "1,10"));
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        et.setTextColor(0xFFE7EAED);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        et.setPadding(p, p / 2, p, p / 2);
        et.setBackground(getDrawable(R.drawable.bg_inset));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(p, 0, p, 0);
        wrap.addView(et, lp);
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_steps)
                .setMessage(R.string.settings_steps_desc)
                .setView(wrap)
                .setPositiveButton(R.string.save, (dlg, w) -> {
                    Store.setPref(this, "steps", et.getText().toString().trim());
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------- یادآور

    private void enableReminder() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        pickTimeAndSchedule();
    }

    private void pickTimeAndSchedule() {
        int h = Store.prefInt(this, "remindH", 21);
        int m = Store.prefInt(this, "remindM", 0);
        new TimePickerDialog(this, (picker, hh, mm) -> {
            Store.setPref(this, "remindOn", "1");
            Store.setPref(this, "remindH", String.valueOf(hh));
            Store.setPref(this, "remindM", String.valueOf(mm));
            Reminders.schedule(this, hh, mm);
            Toast.makeText(this, R.string.settings_reminder_on, Toast.LENGTH_SHORT).show();
            refresh();
        }, h, m, true).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickTimeAndSchedule();
            } else {
                Toast.makeText(this, R.string.perm_denied, Toast.LENGTH_LONG).show();
                swReminder.setOnCheckedChangeListener(null);
                swReminder.setChecked(false);
                swReminder.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) enableReminder();
                    else {
                        Reminders.cancel(this);
                        Store.setPref(this, "remindOn", "0");
                    }
                });
            }
        }
    }
}
