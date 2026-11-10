package com.wiggli.launcherupdates;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;
    private TextView details;
    private Button check;
    private Button install;
    private UpdateClient.UpdateInfo available;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(buildScreen());
        UpdateJob.schedule(this);
        requestNotificationsIfNeeded();
        checkForUpdate();
    }

    private View buildScreen() {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding((int) (80 * density), (int) (54 * density), (int) (80 * density), (int) (48 * density));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(6, 15, 27), Color.rgb(16, 42, 65), Color.rgb(7, 19, 34)});
        root.setBackground(background);

        TextView eyebrow = label("LUQA BOX", 15, 0xff75d4ff, true);
        root.addView(eyebrow, fullWidth(40));

        TextView title = label("Launcher updates", 38, Color.WHITE, true);
        root.addView(title, fullWidth(60));

        TextView subtitle = label("Your box checks your GitHub fork daily and installs verified launcher updates automatically.",
                19, 0xffb8c8d8, false);
        root.addView(subtitle, fullWidth(72));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding((int) (34 * density), (int) (28 * density), (int) (34 * density), (int) (28 * density));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(0xcc101e2d);
        panelBackground.setCornerRadius(28 * density);
        panelBackground.setStroke((int) density, 0x44ffffff);
        panel.setBackground(panelBackground);
        root.addView(panel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        status = label("Checking GitHub…", 28, Color.WHITE, true);
        panel.addView(status, fullWidth(58));
        details = label("The installed launcher is being compared with the latest verified Luqa release.",
                18, 0xffb8c8d8, false);
        details.setLineSpacing(0, 1.25f);
        panel.addView(details, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        panel.addView(actions, fullWidth(64));

        check = button("Check again");
        check.setOnClickListener(v -> checkForUpdate());
        actions.addView(check, new LinearLayout.LayoutParams((int) (220 * density), (int) (56 * density)));

        install = button("Download and install");
        install.setEnabled(false);
        install.setOnClickListener(v -> approveAndInstall());
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams((int) (300 * density), (int) (56 * density));
        installParams.setMargins((int) (18 * density), 0, 0, 0);
        actions.addView(install, installParams);

        TextView footer = label("Only a newer release is downloaded, after which its checksum, package, version and signing key are verified.",
                14, 0xff7f98ad, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, fullWidth(62));
        return root;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setFocusable(true);
        return button;
    }

    private LinearLayout.LayoutParams fullWidth(int heightDp) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (heightDp * getResources().getDisplayMetrics().density));
    }

    private void checkForUpdate() {
        check.setEnabled(false);
        install.setEnabled(false);
        status.setText("Checking GitHub…");
        details.setText("The installed launcher is being compared with the latest verified Luqa release.");
        executor.execute(() -> {
            UpdateClient.CheckResult result = UpdateClient.check(getApplicationContext());
            runOnUiThread(() -> showResult(result));
        });
    }

    private void showResult(UpdateClient.CheckResult result) {
        check.setEnabled(true);
        if (result.error != null) {
            available = null;
            status.setText("Update check unavailable");
            details.setText(result.error + "\n\nNothing was downloaded or installed.");
            return;
        }
        if (!result.updateAvailable) {
            available = null;
            status.setText("Launcher is up to date");
            details.setText("Installed version " + result.installedVersionName + " is the latest approved release.\n\n"
                    + "The box will check again automatically within a day.");
            return;
        }
        available = result.update;
        status.setText("Version " + result.update.versionName + " is available");
        String shortCommit = result.update.commit.length() > 10 ? result.update.commit.substring(0, 10) : result.update.commit;
        details.setText("Installed version: " + result.installedVersionName + "\n"
                + "Available version: " + result.update.versionName + "\n"
                + "GitHub revision: " + shortCommit + "\n\n"
                + "The daily updater will verify and install this release automatically. Select Download and install to run it now.");
        install.setEnabled(true);
        install.requestFocus();
    }

    private void approveAndInstall() {
        if (available == null) return;
        install.setEnabled(false);
        check.setEnabled(false);
        status.setText("Downloading verified update…");
        details.setText("The box is downloading the launcher from your GitHub release and checking its checksum, package name, version and signing certificate.");
        executor.execute(() -> {
            try {
                File apk = UpdateClient.downloadAndVerify(getApplicationContext(), available);
                UpdateInstaller.commit(getApplicationContext(), apk);
                runOnUiThread(() -> {
                    status.setText("Verified update submitted");
                    details.setText("Android is applying the signed launcher update. If this box has not completed its one-time installer setup, Android will request approval instead.");
                    check.setEnabled(true);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    status.setText("Update was blocked");
                    details.setText(error.getMessage() == null ? "The downloaded update could not be verified." : error.getMessage());
                    check.setEnabled(true);
                    install.setEnabled(true);
                });
            }
        });
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 70);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
