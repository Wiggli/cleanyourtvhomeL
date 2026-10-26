package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional, theme-enabled Stremio shelf. The TV provider supplies the titles and launch intents;
 * the launcher never reads a Stremio account or invents viewing history.
 */
final class ContentRows {
    static final int PERMISSION_REQUEST = 409;
    private static final String STREMIO = "com.stremio.one";
    private static final int MAX_ITEMS = 12;
    private final Activity activity;
    private final LinearLayout panel;
    private final LinearLayout tabs;
    private final LinearLayout cards;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, List<Item>> categories = new LinkedHashMap<>();
    private int generation;
    private boolean closed;

    private ContentRows(Activity activity, LinearLayout panel, LinearLayout tabs, LinearLayout cards) {
        this.activity = activity;
        this.panel = panel;
        this.tabs = tabs;
        this.cards = cards;
    }

    static ContentRows attach(Activity activity, View grid) {
        if (!enabled(activity)) return null;
        ViewGroup wrapper = (ViewGroup) grid.getParent();
        ViewGroup column = (ViewGroup) wrapper.getParent();
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        panel.setVisibility(View.GONE);
        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(tabs, new LinearLayout.LayoutParams(-1, dp(activity, 34)));
        HorizontalScrollView scroller = new HorizontalScrollView(activity);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        LinearLayout cards = new LinearLayout(activity);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER_VERTICAL);
        scroller.addView(cards);
        panel.addView(scroller, new LinearLayout.LayoutParams(-1, dp(activity, 169)));
        column.addView(panel, column.indexOfChild(wrapper) + 1,
                new LinearLayout.LayoutParams(-1, -2));
        return new ContentRows(activity, panel, tabs, cards);
    }

    private static boolean enabled(Activity activity) {
        File dir = activity.getExternalFilesDir(null);
        if (dir == null) return false;
        File file = new File(dir, Config.FILE_NAME);
        if (!file.isFile() || file.length() > 128 * 1024) return false;
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[2048];
            int count;
            while ((count = in.read(chunk)) != -1) bytes.write(chunk, 0, count);
            return new JSONObject(bytes.toString("UTF-8")).optBoolean("content_rows", false);
        } catch (Exception ignored) {
            return false;
        }
    }

    void refresh() {
        if (closed) return;
        if (activity.checkSelfPermission(Manifest.permission.READ_TV_LISTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            showPermissionButton();
            return;
        }
        final int request = ++generation;
        worker.execute(() -> {
            Map<String, List<Item>> found = readChannels();
            main.post(() -> {
                if (closed || request != generation) return;
                categories.clear();
                categories.putAll(found);
                showCategories();
            });
        });
    }

    private void showPermissionButton() {
        panel.setVisibility(View.VISIBLE);
        tabs.removeAllViews();
        cards.removeAllViews();
        TextView button = label("Enable Stremio rows", 15);
        button.setFocusable(true);
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
        button.setBackground(background(false));
        button.setOnClickListener(v -> activity.requestPermissions(
                new String[]{Manifest.permission.READ_TV_LISTINGS}, PERMISSION_REQUEST));
        tabs.addView(button);
    }

    private Map<String, List<Item>> readChannels() {
        Map<Long, String> channelNames = new LinkedHashMap<>();
        try (Cursor cursor = activity.getContentResolver().query(
                TvContract.Channels.CONTENT_URI, null, null, null, null)) {
            if (cursor != null) while (cursor.moveToNext()) {
                if (!STREMIO.equals(value(cursor, TvContract.Channels.COLUMN_PACKAGE_NAME))) continue;
                String name = value(cursor, TvContract.Channels.COLUMN_DISPLAY_NAME);
                int id = cursor.getColumnIndex(TvContract.Channels._ID);
                if (id >= 0 && name != null && !name.isEmpty()) channelNames.put(cursor.getLong(id), name);
            }
        } catch (RuntimeException ignored) {
            return new LinkedHashMap<>();
        }
        Map<String, List<Item>> result = new LinkedHashMap<>();
        for (String name : channelNames.values()) result.put(name, new ArrayList<>());
        try (Cursor cursor = activity.getContentResolver().query(
                TvContract.PreviewPrograms.CONTENT_URI, null, null, null, null)) {
            if (cursor != null) while (cursor.moveToNext()) {
                if (!STREMIO.equals(value(cursor, TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME))) continue;
                int channel = cursor.getColumnIndex(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID);
                if (channel < 0) continue;
                String name = channelNames.get(cursor.getLong(channel));
                List<Item> items = result.get(name);
                if (items == null || items.size() >= MAX_ITEMS) continue;
                String title = value(cursor, TvContract.PreviewPrograms.COLUMN_TITLE);
                if (title == null || title.isEmpty()) continue;
                items.add(new Item(title,
                        value(cursor, TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI),
                        value(cursor, TvContract.PreviewPrograms.COLUMN_INTENT_URI)));
            }
        } catch (RuntimeException ignored) {
            return new LinkedHashMap<>();
        }
        result.values().removeIf(List::isEmpty);
        return result;
    }

    private static String value(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }

    private void showCategories() {
        tabs.removeAllViews();
        cards.removeAllViews();
        panel.setVisibility(categories.isEmpty() ? View.GONE : View.VISIBLE);
        if (categories.isEmpty()) return;
        for (String name : categories.keySet()) {
            TextView tab = label(name, 15);
            tab.setFocusable(true);
            tab.setPadding(dp(12), dp(6), dp(12), dp(6));
            tab.setOnClickListener(v -> select(name));
            tabs.addView(tab);
        }
        String first = categories.containsKey("Continue Watching")
                ? "Continue Watching" : categories.keySet().iterator().next();
        select(first);
    }

    private void select(String name) {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView tab = (TextView) tabs.getChildAt(i);
            boolean selected = name.contentEquals(tab.getText());
            tab.setTextColor(selected ? Color.WHITE : 0xA6FFFFFF);
            tab.setBackground(background(selected));
        }
        cards.removeAllViews();
        List<Item> items = categories.get(name);
        if (items == null) return;
        for (Item item : items) {
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setBackground(background(false));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(126), dp(160));
            cardParams.setMargins(0, 0, dp(10), 0);
            cards.addView(card, cardParams);
            ImageView poster = new ImageView(activity);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackgroundColor(0x263A465B);
            card.addView(poster, new LinearLayout.LayoutParams(-1, dp(122)));
            TextView title = label(item.title, 12);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setPadding(dp(8), dp(6), dp(8), 0);
            card.addView(title, new LinearLayout.LayoutParams(-1, dp(35)));
            card.setContentDescription(item.title);
            card.setOnFocusChangeListener((v, focused) -> {
                v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                        .setDuration(120).start();
                v.setBackground(background(focused));
            });
            card.setOnClickListener(v -> open(item));
            if (item.poster != null) loadPoster(item.poster, poster);
        }
    }

    private void open(Item item) {
        if (item.intent != null) try {
            Intent intent = Intent.parseUri(item.intent, Intent.URI_INTENT_SCHEME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return;
        } catch (Exception ignored) {
            // The source app may have removed a title since publishing its channel.
        }
        Intent fallback = activity.getPackageManager().getLeanbackLaunchIntentForPackage(STREMIO);
        if (fallback != null) activity.startActivity(fallback);
    }

    private void loadPoster(String source, ImageView target) {
        final int request = generation;
        worker.execute(() -> {
            Bitmap image = null;
            try {
                Uri uri = Uri.parse(source);
                String scheme = uri.getScheme();
                if ("https".equalsIgnoreCase(scheme)) {
                    HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
                    connection.setConnectTimeout(2500);
                    connection.setReadTimeout(2500);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("Accept", "image/*");
                    try (InputStream stream = connection.getInputStream()) {
                        image = decodeSmall(stream);
                    } finally {
                        connection.disconnect();
                    }
                } else if ("content".equalsIgnoreCase(scheme)
                        || "android.resource".equalsIgnoreCase(scheme)) {
                    try (InputStream stream = activity.getContentResolver().openInputStream(uri)) {
                        if (stream != null) image = decodeSmall(stream);
                    }
                }
            } catch (Exception ignored) {
                // Keep the quiet placeholder when artwork is unavailable.
            }
            Bitmap result = image;
            main.post(() -> {
                if (!closed && request == generation && result != null) target.setImageBitmap(result);
                else if (result != null) result.recycle();
            });
        });
    }

    private static Bitmap decodeSmall(InputStream stream) throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] part = new byte[8192];
        int total = 0;
        int count;
        while ((count = stream.read(part)) != -1) {
            total += count;
            if (total > 2 * 1024 * 1024) return null;
            data.write(part, 0, count);
        }
        byte[] bytes = data.toByteArray();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > 400
                || bounds.outHeight / options.inSampleSize > 500) options.inSampleSize *= 2;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private static GradientDrawable background(boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(focused ? 0x40FFFFFF : 0x1FFFFFFF);
        shape.setCornerRadius(18);
        shape.setStroke(1, focused ? 0xCCFFFFFF : 0x40FFFFFF);
        return shape;
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    void close() {
        closed = true;
        generation++;
        worker.shutdownNow();
    }

    private static final class Item {
        final String title;
        final String poster;
        final String intent;

        Item(String title, String poster, String intent) {
            this.title = title;
            this.poster = poster;
            this.intent = intent;
        }
    }
}
