package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/** Optional Gozo glance card with a compact visual forecast. */
final class GozoWeather {
    private static final String API =
            "https://api.open-meteo.com/v1/forecast?latitude=36.0443&longitude=14.2397"
                    + "&current=temperature_2m,weather_code"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                    + "&timezone=Europe%2FMalta&forecast_days=5";
    private static final long CACHE_MS = 30 * 60 * 1000L;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final WeatherCard card;
    private volatile boolean inFlight;
    private long lastSuccess;

    GozoWeather(Activity activity, FrameLayout root) {
        float density = activity.getResources().getDisplayMetrics().density;
        card = new WeatherCard(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                (int) (290 * density), (int) (100 * density), Gravity.TOP | Gravity.END);
        params.topMargin = (int) (146 * density);
        params.rightMargin = (int) (42 * density);
        root.addView(card, params);
    }

    void start() {
        refresh();
    }

    void stop() {
    }

    private void refresh() {
        if (inFlight || lastSuccess > 0 && System.currentTimeMillis() - lastSuccess < CACHE_MS) return;
        inFlight = true;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && response.length() < 32768) response.append(line);
                }
                JSONObject root = new JSONObject(response.toString());
                JSONObject current = root.getJSONObject("current");
                JSONObject daily = root.getJSONObject("daily");
                JSONArray dates = daily.getJSONArray("time");
                JSONArray codes = daily.getJSONArray("weather_code");
                JSONArray high = daily.getJSONArray("temperature_2m_max");
                JSONArray low = daily.getJSONArray("temperature_2m_min");
                Forecast forecast = new Forecast();
                forecast.current = (int) Math.round(current.getDouble("temperature_2m"));
                forecast.currentCode = current.getInt("weather_code");
                int count = Math.min(4, dates.length());
                forecast.days = new String[count];
                forecast.codes = new int[count];
                forecast.high = new int[count];
                forecast.low = new int[count];
                for (int i = 0; i < count; i++) {
                    LocalDate date = LocalDate.parse(dates.getString(i));
                    forecast.days[i] = i == 0 ? "Now" : date.getDayOfWeek()
                            .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                    forecast.codes[i] = codes.getInt(i);
                    forecast.high[i] = (int) Math.round(high.getDouble(i));
                    forecast.low[i] = (int) Math.round(low.getDouble(i));
                }
                ui.post(() -> {
                    lastSuccess = System.currentTimeMillis();
                    card.forecast = forecast;
                    card.invalidate();
                    inFlight = false;
                });
            } catch (Exception ignored) {
                inFlight = false;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "gozo-weather").start();
    }

    private static final class Forecast {
        int current;
        int currentCode;
        String[] days;
        int[] codes;
        int[] high;
        int[] low;
    }

    private static final class WeatherCard extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Forecast forecast;

        WeatherCard(Activity activity) {
            super(activity);
            setFocusable(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float scale = getWidth() / 580f;
            canvas.save();
            canvas.scale(scale, scale);
            round(canvas, 0, 0, 580, 198, 34, Color.argb(222, 10, 21, 34));
            stroke(canvas, 0, 0, 580, 198, 34, Color.argb(70, 255, 255, 255), 2);
            text(canvas, "GOZO", 26, 34, 19, 0xff72d7ff, true);
            text(canvas, "Weather", 26, 60, 18, 0xffc9d4df, false);
            if (forecast == null) {
                text(canvas, "Updating forecast…", 26, 126, 24, Color.WHITE, false);
                canvas.restore();
                return;
            }

            drawWeatherIcon(canvas, 72, 119, 28, forecast.currentCode);
            text(canvas, forecast.current + "°", 116, 139, 54, Color.WHITE, true);
            text(canvas, weatherLabel(forecast.currentCode), 28, 178, 17, 0xffaebdca, false);

            float start = 250;
            for (int i = 1; i < forecast.days.length; i++) {
                float x = start + (i - 1) * 102;
                if (i > 1) {
                    paint.setColor(0x28ffffff);
                    paint.setStrokeWidth(1);
                    canvas.drawLine(x - 14, 42, x - 14, 166, paint);
                }
                textCenter(canvas, forecast.days[i], x + 26, 51, 16, 0xffb9c7d3, true);
                drawWeatherIcon(canvas, x + 26, 92, 18, forecast.codes[i]);
                textCenter(canvas, forecast.high[i] + "°", x + 26, 137, 22, Color.WHITE, true);
                textCenter(canvas, forecast.low[i] + "°", x + 26, 166, 17, 0xff8fa2b3, false);
            }
            canvas.restore();
        }

        private void drawWeatherIcon(Canvas canvas, float x, float y, float radius, int code) {
            boolean rain = code >= 51;
            boolean cloudy = code >= 2;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffffc857);
            canvas.drawCircle(x - (cloudy ? radius * .38f : 0), y - (cloudy ? radius * .28f : 0),
                    radius * .58f, paint);
            if (cloudy) {
                paint.setColor(0xffe9f0f5);
                canvas.drawCircle(x - radius * .28f, y + radius * .1f, radius * .52f, paint);
                canvas.drawCircle(x + radius * .18f, y - radius * .08f, radius * .67f, paint);
                canvas.drawRoundRect(new RectF(x - radius * .78f, y, x + radius * .9f,
                        y + radius * .62f), radius * .28f, radius * .28f, paint);
            }
            if (rain) {
                paint.setColor(0xff72d7ff);
                paint.setStrokeWidth(Math.max(3, radius * .15f));
                for (int i = -1; i <= 1; i++) {
                    float dx = x + i * radius * .45f;
                    canvas.drawLine(dx, y + radius * .72f, dx - radius * .12f,
                            y + radius * 1.1f, paint);
                }
            }
        }

        private static String weatherLabel(int code) {
            if (code <= 1) return "Clear in Gozo";
            if (code <= 3) return "Partly cloudy";
            if (code < 51) return "Low cloud";
            if (code < 80) return "Rain nearby";
            return "Showers";
        }

        private void round(Canvas canvas, float left, float top, float right, float bottom,
                           float radius, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
        }

        private void stroke(Canvas canvas, float left, float top, float right, float bottom,
                            float radius, int color, float width) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(width);
            paint.setColor(color);
            canvas.drawRoundRect(new RectF(left + width / 2, top + width / 2,
                    right - width / 2, bottom - width / 2), radius, radius, paint);
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
