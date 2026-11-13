package com.wiggli.gozofootball;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class TeamCrests {
    private TeamCrests() {}

    static Bitmap load(int id) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    "https://a.espncdn.com/i/teamlogos/soccer/500/" + id + ".png").openConnection();
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(3000);
            if (connection.getResponseCode() != 200) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            try (InputStream body = connection.getInputStream()) {
                return BitmapFactory.decodeStream(body, null, options);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
