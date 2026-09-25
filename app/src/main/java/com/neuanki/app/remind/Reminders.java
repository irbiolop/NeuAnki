package com.neuanki.app.remind;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/** زمان‌بندی یادآور روزانه با WorkManager */
public class Reminders {

    private static final String WORK_NAME = "daily_reminder";

    public static void schedule(Context c, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long now = System.currentTimeMillis();
        if (cal.getTimeInMillis() <= now) cal.add(Calendar.DAY_OF_YEAR, 1);

        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(ReminderWorker.class, 1, TimeUnit.DAYS)
                .setInitialDelay(cal.getTimeInMillis() - now, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE, req);
    }

    public static void cancel(Context c) {
        WorkManager.getInstance(c).cancelUniqueWork(WORK_NAME);
    }
}
