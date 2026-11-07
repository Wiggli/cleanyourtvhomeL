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
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Optional, non-focusable forecast overlay enabled only by the pushed Gozo theme. */
final class GozoWeather {
    private static final String API =
        "https://api.open-meteo.com/v1/forecast?latitude=36.0443&longitude=14.2397"
        + "&daily=temperature_2m_max,temperature_2m_min"
        + "&timezone=Europe%2FMalta&forecast_days=5";
    private static final long CACHE_MS = 30 * 60 * 1000L;
    private final Activity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LinearLayout panel;
    private final TextView forecast;
    private volatile boolean inFlight;
    private boolean active;
    private long lastSuccess;

    GozoWeather(Activity activity, FrameLayout root) {
        this.activity = activity;
        float d = activity.getResources().getDisplayMetrics().density;
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.setPadding((int)(16*d), (int)(12*d), (int)(16*d), (int)(12*d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x990C1721);
        bg.setCornerRadius(20*d);
        panel.setBackground(bg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            (int)(265*d), FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.END);
        lp.topMargin = (int)(146*d);
        lp.rightMargin = (int)(42*d);
        root.addView(panel, lp);
        TextView headline = line("GOZO  ·  WEATHER", 20, Color.WHITE);
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(-1, -2);
        panel.addView(headline, headLp);
        forecast = line("Forecast unavailable", 13, 0xDDFFFFFF);
        LinearLayout.LayoutParams forecastLp = new LinearLayout.LayoutParams(-1, -2);
        forecastLp.topMargin = (int)(9*d);
        panel.addView(forecast, forecastLp);
    }

    private TextView line(String text, int size, int colour) {
        TextView v = new TextView(activity);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(colour);
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
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && response.length() < 32768) response.append(line);
                }
                JSONObject json = new JSONObject(response.toString());
                JSONObject daily = json.getJSONObject("daily");
                JSONArray dates = daily.getJSONArray("time");
                JSONArray hi = daily.getJSONArray("temperature_2m_max");
                JSONArray lo = daily.getJSONArray("temperature_2m_min");
                StringBuilder days = new StringBuilder();
                for (int i = 0; i < Math.min(5, dates.length()); i++) {
                    if (i > 0) days.append("\n");
                    String day = i == 0 ? "Today" : dates.getString(i).substring(5);
                    days.append(day).append("  ")
                        .append(Math.round(hi.getDouble(i))).append("° / ")
                        .append(Math.round(lo.getDouble(i))).append("°");
                }
                ui.post(() -> {
                    lastSuccess = System.currentTimeMillis();
                    forecast.setText(days.toString());
                    inFlight = false;
                });
            } catch (Exception ignored) {
                inFlight = false;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }
}
