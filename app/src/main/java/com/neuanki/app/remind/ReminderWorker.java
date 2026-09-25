package com.neuanki.app.remind;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.neuanki.app.App;
import com.neuanki.app.R;
import com.neuanki.app.db.Store;
import com.neuanki.app.ui.MainActivity;
import com.neuanki.app.util.Util;

/** بررسی کارت‌های سررسید و ارسال نوتیفیکیشن */
public class ReminderWorker extends Worker {

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        long now = System.currentTimeMillis();
        long today = Util.dayNum(now);
        int newLimit = Store.prefInt(c, "newPerDay", 20);
        int revLimit = Store.prefInt(c, "revPerDay", 200);
        int[] cnt = Store.allCounts(c, now, today, newLimit, revLimit);
        int due = cnt[0] + cnt[1] + cnt[2];
        if (due > 0) notifyDue(c, due);
        return Result.success();
    }

    private void notifyDue(Context c, int due) {
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 1001, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification n = new NotificationCompat.Builder(c, App.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_bell)
                .setColor(0xFF10B981)
                .setContentTitle(c.getString(R.string.notif_title))
                .setContentText(c.getString(R.string.notif_text_fmt, Util.fa(due)))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            NotificationManagerCompat.from(c).notify(1001, n);
        } catch (SecurityException ignored) {
        }
    }
}
