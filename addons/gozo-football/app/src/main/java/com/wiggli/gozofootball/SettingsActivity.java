package com.wiggli.gozofootball;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

/** Remote-first settings for choosing the teams and competitions shown by the match centre. */
public final class SettingsActivity extends Activity {
    private SettingsView settingsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsView = new SettingsView();
        setContentView(settingsView);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            settingsView.move(-1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            settingsView.move(1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            settingsView.toggle();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private final class SettingsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean[] teams = new boolean[FootballConfig.TEAMS.length];
        private final boolean[] competitions = new boolean[FootballConfig.COMPETITIONS.length];
        private int selected;

        SettingsView() {
            super(SettingsActivity.this);
            SharedPreferences preferences = FootballConfig.preferences(SettingsActivity.this);
            for (int i = 0; i < teams.length; i++) {
                teams[i] = FootballConfig.isTeamEnabled(preferences, FootballConfig.TEAMS[i]);
            }
            for (int i = 0; i < competitions.length; i++) {
                competitions[i] = FootballConfig.isCompetitionEnabled(preferences, FootballConfig.COMPETITIONS[i]);
            }
            setFocusable(true);
            requestFocus();
        }

        void move(int amount) {
            selected = (selected + amount + itemCount()) % itemCount();
            invalidate();
        }

        void toggle() {
            if (selected < teams.length) {
                if (teams[selected] && countSelected(teams) == 1) {
                    Toast.makeText(SettingsActivity.this, "Keep at least one team selected", Toast.LENGTH_SHORT).show();
                    return;
                }
                teams[selected] = !teams[selected];
            } else {
                int competition = selected - teams.length;
                if (competitions[competition] && countSelected(competitions) == 1) {
                    Toast.makeText(SettingsActivity.this, "Keep at least one competition selected", Toast.LENGTH_SHORT).show();
                    return;
                }
                competitions[competition] = !competitions[competition];
            }
            save();
            invalidate();
        }

        private void save() {
            SharedPreferences.Editor editor = FootballConfig.preferences(SettingsActivity.this).edit();
            for (int i = 0; i < teams.length; i++) {
                editor.putBoolean("team_" + FootballConfig.TEAMS[i].key, teams[i]);
            }
            for (int i = 0; i < competitions.length; i++) {
                editor.putBoolean("competition_" + FootballConfig.COMPETITIONS[i].slug, competitions[i]);
            }
            editor.apply();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth() / 1920f, getHeight() / 1080f);
            canvas.save();
            canvas.translate((getWidth() - 1920f * scale) / 2f, (getHeight() - 1080f * scale) / 2f);
            canvas.scale(scale, scale);

            canvas.drawColor(Color.rgb(8, 12, 20));
            circle(canvas, 1650, 100, 430, Color.argb(55, 218, 41, 28));
            circle(canvas, 160, 1010, 500, Color.argb(38, 0, 102, 179));
            round(canvas, 80, 55, 1760, 970, 44, Color.argb(230, 18, 23, 34));
            stroke(canvas, 80, 55, 1760, 970, 44, Color.argb(45, 255, 255, 255), 2);

            text(canvas, "Football interests", 140, 135, 46, Color.WHITE, true);
            text(canvas, "Choose what appears on your match centre", 140, 177, 23,
                    Color.argb(175, 255, 255, 255), false);

            text(canvas, "TEAMS", 140, 250, 18, Color.rgb(255, 143, 132), true);
            for (int i = 0; i < teams.length; i++) {
                drawRow(canvas, 140, 280 + i * 130, 700, FootballConfig.TEAMS[i].name,
                        FootballConfig.TEAMS[i].shortName, teams[i], i);
            }

            text(canvas, "COMPETITIONS", 930, 250, 18, Color.rgb(147, 197, 253), true);
            for (int i = 0; i < competitions.length; i++) {
                int column = i / 6;
                int row = i % 6;
                FootballConfig.Competition competition = FootballConfig.COMPETITIONS[i];
                drawRow(canvas, 930 + column * 415, 280 + row * 92, 380, competition.name,
                        competition.region, competitions[i], teams.length + i);
            }

            round(canvas, 140, 870, 1635, 92, 24, Color.argb(210, 21, 33, 48));
            text(canvas, "↑  ↓  move", 185, 928, 22, Color.argb(205, 255, 255, 255), false);
            text(canvas, "OK select", 450, 928, 22, Color.argb(205, 255, 255, 255), false);
            text(canvas, "Back saves and returns", 690, 928, 22, Color.argb(205, 255, 255, 255), false);
            textRight(canvas, countSelected(teams) + " teams  •  " + countSelected(competitions)
                    + " competitions", 1725, 928, 22, Color.WHITE);
            canvas.restore();
        }

        private void drawRow(Canvas canvas, float x, float y, float width, String title, String detail,
                             boolean checked, int index) {
            boolean focused = selected == index;
            int fill = focused ? Color.argb(225, 56, 69, 86) : Color.argb(195, 25, 37, 52);
            round(canvas, x, y, width, 82, 20, fill);
            stroke(canvas, x, y, width, 82, 20,
                    focused ? Color.argb(225, 255, 255, 255) : Color.argb(30, 255, 255, 255), focused ? 3 : 1);
            round(canvas, x + 20, y + 19, 44, 44, 12,
                    checked ? Color.rgb(218, 41, 28) : Color.argb(30, 255, 255, 255));
            if (checked) textCentered(canvas, "✓", x + 42, y + 51, 25, Color.WHITE, true);
            text(canvas, title, x + 82, y + 39, 23, Color.WHITE, true);
            text(canvas, detail, x + 82, y + 66, 16, Color.argb(145, 255, 255, 255), false);
        }

        private int itemCount() {
            return teams.length + competitions.length;
        }

        private int countSelected(boolean[] values) {
            int count = 0;
            for (boolean value : values) if (value) count++;
            return count;
        }

        private void circle(Canvas canvas, float x, float y, float radius, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, y, radius, paint);
        }

        private void round(Canvas canvas, float x, float y, float width, float height, float radius, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new RectF(x, y, x + width, y + height), radius, radius, paint);
        }

        private void stroke(Canvas canvas, float x, float y, float width, float height, float radius,
                            int color, float strokeWidth) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            canvas.drawRoundRect(new RectF(x, y, x + width, y + height), radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void text(Canvas canvas, String value, float x, float y, float size, int color, boolean bold) {
            paint.setTextSize(size);
            paint.setColor(color);
            paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(value, x, y, paint);
        }

        private void textRight(Canvas canvas, String value, float x, float y, float size, int color) {
            paint.setTextSize(size);
            paint.setColor(color);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(value, x, y, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void textCentered(Canvas canvas, String value, float x, float y, float size, int color,
                                  boolean bold) {
            paint.setTextSize(size);
            paint.setColor(color);
            paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(value, x, y, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }
}
