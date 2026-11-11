package com.gathin.tvhome;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Optional Home match card backed by the separately installed Gozo Football app. */
final class GozoFootball {
    private static final Uri NEXT = Uri.parse("content://com.wiggli.gozofootball.fixtures/next");
    private final Activity activity;
    private final LinearLayout panel;
    private final TextView headline, fixture, detail;

    GozoFootball(Activity activity, FrameLayout root) {
        this.activity = activity;
        float d = activity.getResources().getDisplayMetrics().density;
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding((int) (18 * d), (int) (13 * d), (int) (18 * d), (int) (13 * d));
        panel.setVisibility(View.GONE);
        panel.setBackgroundColor(0xAA101722);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (350 * d), -2,
                Gravity.TOP | Gravity.END);
        lp.topMargin = (int) (146 * d);
        lp.rightMargin = (int) (325 * d);
        root.addView(panel, lp);
        headline = line("FOOTBALL  ·  NEXT", 14, 0xCCFFFFFF);
        fixture = line("", 18, Color.WHITE);
        detail = line("", 13, 0xCCFFFFFF);
        panel.addView(headline);
        panel.addView(fixture);
        panel.addView(detail);
    }

    private TextView line(String value, int size, int colour) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(colour);
        return view;
    }

    void start() {
        refresh();
    }

    private void refresh() {
        new Thread(() -> {
            String[] value = read();
            activity.runOnUiThread(() -> {
                if (value == null) {
                    panel.setVisibility(View.GONE);
                    return;
                }
                panel.setVisibility(View.VISIBLE);
                headline.setText(value[0]);
                fixture.setText(value[1]);
                detail.setText(value[2]);
            });
        }).start();
    }

    private String[] read() {
        try (Cursor c = activity.getContentResolver().query(NEXT, null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            return new String[] {c.getString(0), c.getString(1), c.getString(2)};
        } catch (Exception ignored) {
            return null;
        }
    }
}
