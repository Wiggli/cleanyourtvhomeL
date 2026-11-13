package io.github.sumyabazar1.weather;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Small weather illustrations drawn at the size the card actually displays. */
final class WeatherSymbol extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int code;
    private final boolean day;
    private int alpha = 255;

    WeatherSymbol(int code, boolean day) {
        this.code = code;
        this.day = day;
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int layer = alpha == 255 ? -1 : canvas.saveLayerAlpha(bounds.left, bounds.top,
                bounds.right, bounds.bottom, alpha);
        float size = Math.min(bounds.width(), bounds.height());
        canvas.save();
        canvas.translate(bounds.exactCenterX() - size / 2, bounds.exactCenterY() - size / 2);
        canvas.scale(size / 48f, size / 48f);
        boolean cloud = code >= 2;
        if (code <= 2) {
            float x = cloud ? 16 : 24;
            float y = cloud ? 15 : 24;
            if (day) sun(canvas, x, y, cloud ? 8 : 11);
            else {
                paint.setColor(0xffdce8ff);
                canvas.drawCircle(x, y, cloud ? 10 : 14, paint);
                paint.setColor(0xff30476a);
                canvas.drawCircle(x + 5, y - 6, cloud ? 9 : 12, paint);
            }
        }
        if (cloud) cloud(canvas, code >= 51 ? 0xffc8dbf1 : 0xffedf5ff);
        paint.setStrokeWidth(2.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (code == 45 || code == 48) {
            paint.setColor(0xffbbd9ed);
            canvas.drawLine(9, 34, 36, 34, paint);
            canvas.drawLine(14, 40, 41, 40, paint);
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            paint.setColor(0xffe2f3ff);
            for (int i = 0; i < 3; i++) canvas.drawCircle(16 + i * 9, 37 + (i % 2) * 4, 2, paint);
        } else if (code >= 95) {
            paint.setColor(0xffffd47b);
            canvas.drawLine(26, 29, 21, 37, paint);
            canvas.drawLine(21, 37, 28, 37, paint);
            canvas.drawLine(28, 37, 23, 45, paint);
        } else if (code >= 51) {
            paint.setColor(0xff8bd8ff);
            for (int i = 0; i < 3; i++) {
                float x = 17 + 9 * i;
                canvas.drawLine(x, 33, x - 3, 40, paint);
            }
        }
        canvas.restore();
        if (layer >= 0) canvas.restoreToCount(layer);
    }

    private void sun(Canvas canvas, float x, float y, float radius) {
        paint.setColor(0xffffd47b);
        paint.setStrokeWidth(2);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawCircle(x, y, radius, paint);
        for (int i = 0; i < 8; i++) {
            canvas.save();
            canvas.rotate(i * 45f, x, y);
            canvas.drawLine(x, y - radius - 3, x, y - radius - 6, paint);
            canvas.restore();
        }
    }

    private void cloud(Canvas canvas, int color) {
        paint.setColor(color);
        canvas.drawCircle(17, 24, 7, paint);
        canvas.drawCircle(26, 19, 10, paint);
        canvas.drawCircle(35, 24, 7, paint);
        canvas.drawRoundRect(10, 23, 42, 31, 4, 4, paint);
    }

    @Override public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
        invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
