package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.view.View;

/** Full-screen ambient photo frame, using the active theme's wallpaper collection and location captions. */
final class PhotoFrameScene {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WallpaperPlayer photos;
    private final ClockScene clock;
    private final long pollMs;
    private boolean running;

    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!running) return;
            photos.update();
            handler.postDelayed(this, pollMs);
        }
    };

    PhotoFrameScene(Context context, FrameLayout root, Config config) {
        photos = new WallpaperPlayer(context, root, config, true);
        View scrim = new View(context);
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xD9000000, 0x50000000, 0x08000000, 0x00000000}));
        root.addView(scrim, Math.min(1, root.getChildCount()), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        clock = new ClockScene(root);
        long interval = Math.max(0, config.wallpaperRotationMinutes) * 60_000L;
        pollMs = interval == 0 ? 60_000L : Math.min(60_000L, interval);
    }

    void start() {
        running = true;
        photos.start();
        clock.start();
        handler.removeCallbacks(rotate);
        handler.postDelayed(rotate, pollMs);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(rotate);
        photos.stop();
        clock.stop();
    }

    void close() {
        stop();
        photos.close();
    }
}
