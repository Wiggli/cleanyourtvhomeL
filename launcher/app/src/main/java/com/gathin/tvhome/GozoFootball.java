package com.gathin.tvhome;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

    void positionForScroll(int shift, float alpha) {
        card.setTranslationY(-shift);
        card.setAlpha(alpha);
    }

    private void refresh() {
        new Thread(() -> {
            Fixture value = read();
            activity.runOnUiThread(() -> {
                if (value == null) {
                    card.setVisibility(View.GONE);
                    return;
                }
                card.fixture = value;
                card.setVisibility(View.VISIBLE);
                card.invalidate();
            });
        }, "gozo-football-card").start();
    }

    private Fixture read() {
        try (Cursor cursor = activity.getContentResolver().query(NEXT, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            String[] teams = cursor.getString(1).split("\\s+v\\s+", 2);
            String[] detail = cursor.getString(2).split("\\s+·\\s+", 2);
            return new Fixture(cursor.getString(0).contains("LIVE"),
                    value(cursor, "home_name", teams[0]),
                    value(cursor, "away_name", teams.length > 1 ? teams[1] : ""),
                    value(cursor, "competition", detail.length > 1 ? detail[1] : "Football"),
                    value(cursor, "match_time", detail[0]),
                    image(cursor, "home_crest"), image(cursor, "away_crest"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String value(Cursor cursor, String column, String fallback) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? fallback : cursor.getString(index);
    }

    private static Bitmap image(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return null;
        byte[] bytes = cursor.getBlob(index);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private static final class Fixture {
        final boolean live;
        final String home, away, competition, time;
        final Bitmap homeCrest, awayCrest;

        Fixture(boolean live, String home, String away, String competition, String time,
                Bitmap homeCrest, Bitmap awayCrest) {
            this.live = live;
            this.home = home;
            this.away = away;
            this.competition = competition;
            this.time = time;
            this.homeCrest = homeCrest;
            this.awayCrest = awayCrest;
        }
    }

    private static final class FootballCard extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Fixture fixture;

        FootballCard(Activity activity) {
            super(activity);
            setFocusable(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (fixture == null) return;
            float scale = getWidth() / 580f;
            canvas.save();
            canvas.scale(scale, scale);
            GozoWeather.drawCardSurface(canvas, paint, 170);
            text(canvas, fixture.live ? "Live now" : "Next match", 25, 33, 21,
                    fixture.live ? 0xff82e6ad : 0xff86d8ff, true, 295);
            drawTeam(canvas, 76, fixture.home, fixture.homeCrest);
            drawTeam(canvas, 128, fixture.away, fixture.awayCrest);
            paint.setColor(0x50ffffff);
            paint.setStrokeWidth(1);
            canvas.drawLine(328, 51, 328, 146, paint);
            text(canvas, fixture.time, 348, 88, 24, Color.WHITE, true, 211);
            text(canvas, fixture.competition, 348, 121, 20, 0xffc3dfff, false, 211);
            canvas.restore();
        }

        private void drawTeam(Canvas canvas, float y, String name, Bitmap crest) {
            paint.setColor(crest == null ? 0xff365773 : 0xffe3eef9);
            canvas.drawCircle(46, y, 22, paint);
            if (crest == null) textCenter(canvas, initials(name), 46, y + 6, 16, Color.WHITE, true);
            else {
                paint.setColor(Color.WHITE);
                float width = 34f * crest.getWidth() / Math.max(crest.getWidth(), crest.getHeight());
                float height = 34f * crest.getHeight() / Math.max(crest.getWidth(), crest.getHeight());
                canvas.drawBitmap(crest, null, new RectF(46 - width / 2, y - height / 2,
                        46 + width / 2, y + height / 2), paint);
            }
            text(canvas, name, 80, y + 8, 25, Color.WHITE, true, 237);
        }

        private static String initials(String name) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.length() <= 3) return trimmed.toUpperCase();
            String[] words = trimmed.split("\\s+");
            if (words.length == 1) return trimmed.substring(0, 3).toUpperCase();
            return ("" + words[0].charAt(0) + words[words.length - 1].charAt(0)).toUpperCase();
        }

        private void text(Canvas canvas, String value, float x, float baseline, float size,
                          int color, boolean bold, float width) {
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.LEFT);
            android.text.TextPaint measure = new android.text.TextPaint(paint);
            CharSequence line = android.text.TextUtils.ellipsize(value, measure, width,
                    android.text.TextUtils.TruncateAt.END);
            canvas.drawText(line, 0, line.length(), x, baseline, paint);
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
