package com.wiggli.launcherupdates;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/** Commits a verified launcher APK through Android's installer, without replacing any recovery checks. */
final class UpdateInstaller {
    private static final String ACTION_RESULT = "com.wiggli.launcherupdates.INSTALL_RESULT";

    private UpdateInstaller() {
    }

    static void commit(Context context, File apk) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(UpdateClient.LAUNCHER_PACKAGE);
        params.setSize(apk.length());
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }

        int sessionId = installer.createSession(params);
        try {
            try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                try (FileInputStream input = new FileInputStream(apk);
                     OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                    session.fsync(output);
                }

                Intent result = new Intent(context, InstallResultReceiver.class)
                        .setAction(ACTION_RESULT)
                        .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                flags |= Build.VERSION.SDK_INT >= 31
                        ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE;
                PendingIntent callback = PendingIntent.getBroadcast(context, sessionId, result, flags);
                session.commit(callback.getIntentSender());
            }
        } catch (Exception error) {
            installer.abandonSession(sessionId);
            throw error;
        }
    }
}
