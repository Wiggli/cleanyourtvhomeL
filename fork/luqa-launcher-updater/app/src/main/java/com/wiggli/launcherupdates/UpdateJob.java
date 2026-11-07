package com.wiggli.launcherupdates;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class UpdateJob extends JobService {
    private static final int JOB_ID = 0x4c555151;
    private static final int NOTIFICATION_ID = 701;
    private static final String CHANNEL = "launcher_updates";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    static void schedule(Context context) {
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, UpdateJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(TimeUnit.DAYS.toMillis(3), TimeUnit.HOURS.toMillis(12))
                .build();
        context.getSystemService(JobScheduler.class).schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        executor.execute(() -> {
            UpdateClient.CheckResult result = UpdateClient.check(getApplicationContext());
            if (result.updateAvailable) notifyAvailable(result);
            jobFinished(params, false);
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }

    @Override public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void notifyAvailable(UpdateClient.CheckResult result) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Launcher updates",
                NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new android.app.Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle("Launcher update available")
                .setContentText("Version " + result.update.versionName + " is ready for your approval")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }
}
