package com.gathin.tvhome;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/** Optional Home match card backed by the separately installed Gozo Football app. */
final class GozoFootball {
    private static final Uri NEXT = Uri.parse("content://com.wiggli.gozofootball.fixtures/next");
    private final Activity activity;
    private final FootballCard card;

    GozoFootball(Activity activity, FrameLayout root) {
        this.activity = activity;
        float density = activity.getResources().getDisplayMetrics().density;
        card = new FootballCard(activity);
        card.setVisibility(View.GONE);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                (int) (290 * density), (int) (86 * density), Gravity.TOP | Gravity.END);
        params.topMargin = (int) (252 * density);
        params.rightMargin = (int) (42 * density);
        root.addView(card, params);
    }

    void start() {
        refresh();
    }

    private void refresh() {
        new Thread(() -> {
            String[] value = read();
            activity.runOnUiThread(() -> {
                if (value == null) {
                    card.setVisibility(View.GONE);
                    return;
                }
                card.headline = value[0];
                card.fixture = value[1];
                card.detail = value[2];
                card.setVisibility(View.VISIBLE);
                card.invalidate();
            });
        }, "gozo-football-card").start();
    }

    private String[] read() {
        try (Cursor cursor = activity.getContentResolver().query(NEXT, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            return new String[]{cursor.getString(0), cursor.getString(1), cursor.getString(2)};
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class FootballCard extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String headline = "FOOTBALL  ·  NEXT";
        private String fixture = "";
        private String detail = "";

        FootballCard(Activity activity) {
            super(activity);
            setFocusable(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float scale = getWidth() / 580f;
            canvas.save();
            canvas.scale(scale, scale);
            round(canvas, 0, 0, 580, 170, 34, Color.argb(225, 12, 22, 36));
            stroke(canvas, 0, 0, 580, 170, 34, Color.argb(70, 255, 255, 255), 2);
            paint.setColor(0x246ad5ff);
            canvas.drawCircle(540, 18, 108, paint);

            boolean live = headline.contains("LIVE");
            text(canvas, live ? "LIVE NOW" : "NEXT MATCH", 25, 34, 17,
                    live ? 0xffff665c : 0xff72d7ff, true);
            String[] teams = fixture.split("\\s+v\\s+", 2);
            String first = teams.length > 0 ? teams[0].trim() : fixture;
            String second = teams.length > 1 ? teams[1].trim() : "";
            String[] parts = detail.split("\\s+·\\s+", 2);
            String when = parts.length > 0 ? parts[0] : detail;
            String competition = parts.length > 1 ? parts[1] : "Football";

            drawBadge(canvas, 78, 96, initials(first), colorFor(first));
            drawBadge(canvas, 260, 96, initials(second), colorFor(second));
            textCenter(canvas, "v", 169, 104, 20, 0xff8fa2b3, true);
            textCenter(canvas, shortName(first), 78, 148, 17, Color.WHITE, true);
            textCenter(canvas, shortName(second), 260, 148, 17, Color.WHITE, true);

            round(canvas, 342, 52, 214, 88, 24, 0x441f354b);
            text(canvas, when, 365, 88, 25, Color.WHITE, true);
            text(canvas, competition, 365, 119, 17, 0xffb2c2d0, false);
            canvas.restore();
        }

        private void drawBadge(Canvas canvas, float x, float y, String letters, int color) {
            paint.setColor(0xfff4f7fa);
            canvas.drawCircle(x, y, 33, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(color);
            canvas.drawCircle(x, y, 28, paint);
            paint.setStyle(Paint.Style.FILL);
            textCenter(canvas, letters, x, y + 7, 17, color, true);
        }

        private static String initials(String name) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.length() <= 3) return trimmed.toUpperCase();
            String[] words = trimmed.split("\\s+");
            if (words.length == 1) return trimmed.substring(0, 3).toUpperCase();
            return ("" + words[0].charAt(0) + words[words.length - 1].charAt(0)).toUpperCase();
        }

        private static String shortName(String name) {
            if (name == null) return "";
            String value = name.trim();
            return value.length() > 14 ? value.substring(0, 13) + "…" : value;
        }

        private static int colorFor(String name) {
            int[] colors = {0xffe63946, 0xff168aad, 0xff5a56e9, 0xffe09f3e, 0xff2a9d8f};
            return colors[Math.abs(name == null ? 0 : name.hashCode()) % colors.length];
        }

        private void round(Canvas canvas, float x, float y, float width, float height,
                           float radius, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawRoundRect(new RectF(x, y, x + width, y + height), radius, radius, paint);
        }

        private void stroke(Canvas canvas, float x, float y, float width, float height,
                            float radius, int color, float strokeWidth) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setColor(color);
            canvas.drawRoundRect(new RectF(x + strokeWidth / 2, y + strokeWidth / 2,
                    x + width - strokeWidth / 2, y + height - strokeWidth / 2), radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void text(Canvas canvas, String value, float x, float baseline, float size,
                          int color, boolean bold) {
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(value, x, baseline, paint);
        }

        private void textCenter(Canvas canvas, String value, float x, float baseline, float size,
                                int color, boolean bold) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
            canvas.drawText(value, x, baseline, paint);
        }
    }
}
