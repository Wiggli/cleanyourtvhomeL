package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Optional, non-focusable forecast overlay enabled only by the pushed Gozo theme. */
final class GozoWeather {
    private static final String API =
        "https://api.open-meteo.com/v1/forecast?latitude=36.0443&longitude=14.2397"
        + "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min"
        + "&timezone=Europe%2FMalta&forecast_days=5";
    private static final long CACHE_MS = 30 * 60 * 1000L;
    private final Activity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LinearLayout panel;
    private final TextView headline;
    private final TextView forecast;
    private volatile boolean inFlight;
    private boolean active;
    private long lastSuccess;

    static boolean enabled(Activity activity) {
        try {
            File dir = Config.dir(activity);
            if (dir == null) return false;
            File file = new File(dir, Config.FILE_NAME);
            if (!file.isFile() || file.length() > 65536) return false;
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int n = in.read(bytes, offset, bytes.length - offset);
                    if (n < 0) return false;
                    offset += n;
                }
            }
            return new JSONObject(new String(bytes, StandardCharsets.UTF_8))
                .optBoolean("gozo_inline_weather", false);
        } catch (Exception ignored) {
            return false;
        }
    }

    GozoWeather(Activity activity, FrameLayout root) {
        this.activity = activity;
        float d = activity.getResources().getDisplayMetrics().density;
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setFocusable(false);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.setPadding((int)(16*d), (int)(12*d), (int)(16*d), (int)(12*d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x990C1721);
        bg.setCornerRadius(20*d);
        bg.setStroke(Math.max(1, (int)d), 0x40FFFFFF);
        panel.setBackground(bg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            (int)(265*d), FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.END);
        lp.topMargin = (int)(146*d);
        lp.rightMargin = (int)(42*d);
        root.addView(panel, lp);
        TextView label = line("GOZO  ·  WEATHER", 11, 0xCCFFFFFF);
        label.setLetterSpacing(0.14f);
        panel.addView(label);
        headline = line("Loading forecast…", 23, Color.WHITE);
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(-1, -2);
        headLp.topMargin = (int)(7*d);
        panel.addView(headline, headLp);
        forecast = line("", 13, 0xDDFFFFFF);
        forecast.setLineSpacing(5*d, 1f);
        LinearLayout.LayoutParams forecastLp = new LinearLayout.LayoutParams(-1, -2);
        forecastLp.topMargin = (int)(9*d);
        panel.addView(forecast, forecastLp);
    }

    private TextView line(String text, int size, int colour) {
        TextView v = new TextView(activity);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(colour);
        v.setFocusable(false);
        return v;
    }

    void start() {
        active = true;
        refresh();
    }

    void stop() {
        active = false;
    }

    private void refresh() {
        if (inFlight || (lastSuccess > 0 && System.currentTimeMillis() - lastSuccess < CACHE_MS)) return;
        inFlight = true;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setRequestProperty("Accept", "application/json");
                if (connection.getResponseCode() != 200) throw new IllegalStateException("Weather HTTP error");
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && response.length() < 32768) response.append(line);
                }
                JSONObject json = new JSONObject(response.toString());
                JSONObject current = json.getJSONObject("current");
                JSONObject daily = json.getJSONObject("daily");
                String now = Math.round(current.getDouble("temperature_2m")) + "°C  ·  "
                    + condition(current.getInt("weather_code"));
                JSONArray dates = daily.getJSONArray("time");
                JSONArray hi = daily.getJSONArray("temperature_2m_max");
                JSONArray lo = daily.getJSONArray("temperature_2m_min");
                JSONArray codes = daily.getJSONArray("weather_code");
                StringBuilder days = new StringBuilder();
                DateTimeFormatter name = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
                for (int i = 0; i < Math.min(5, dates.length()); i++) {
                    if (i > 0) days.append("\n");
                    String day = i == 0 ? "Today" : LocalDate.parse(dates.getString(i)).format(name);
                    days.append(String.format(Locale.ENGLISH, "%-6s  %2.0f° / %2.0f°   %s",
                        day, hi.getDouble(i), lo.getDouble(i), condition(codes.getInt(i))));
                }
                ui.post(() -> {
                    lastSuccess = System.currentTimeMillis();
                    headline.setText(now);
                    forecast.setText(days.toString());
                    inFlight = false;
                });
            } catch (Exception ignored) {
                ui.post(() -> {
                    if (lastSuccess == 0) {
                        headline.setText("Forecast unavailable");
                        forecast.setText("Open Weather for details");
                    }
                    inFlight = false;
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "gozo-weather-fetch").start();
    }

    private static String condition(int code) {
        if (code == 0) return "Clear";
        if (code <= 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67 || code == 80 || code == 81 || code == 82) return "Rain";
        if (code <= 77 || code == 85 || code == 86) return "Snow";
        if (code >= 95) return "Storm";
        return "Mixed";
    }
}
