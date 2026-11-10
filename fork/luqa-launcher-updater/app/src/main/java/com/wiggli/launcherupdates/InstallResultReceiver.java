package com.wiggli.launcherupdates;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Reports the result of an automatic install and exposes Android's confirmation only as a fallback. */
public final class InstallResultReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "launcher_updates";
    private static final int NOTIFICATION_ID = 702;

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_SUCCESS) {
            notify(context, "Launcher updated automatically",
                    "The signed Luqa launcher update was installed", homeIntent(context));
            return;
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            notify(context, "One-time update approval needed",
                    "Open this notice and approve the installer so later updates can run unattended",
                    confirmation == null ? homeIntent(context) : confirmation);
            return;
        }
        String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        notify(context, "Launcher update was not installed",
                detail == null || detail.trim().isEmpty() ? "The current launcher was left unchanged" : detail,
                homeIntent(context));
    }

    private static Intent homeIntent(Context context) {
        return new Intent(context, MainActivity.class);
    }

    private static void notify(Context context, String title, String text, Intent target) {
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(context, NOTIFICATION_ID, target,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Launcher updates",
                NotificationManager.IMPORTANCE_DEFAULT));
        manager.notify(NOTIFICATION_ID, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build());
    }
}
