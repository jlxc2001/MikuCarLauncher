package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.DisplayMetrics;

public final class UiScaleHelper {
    public static final String PREF_UI_AUTO_SCALE = "ui_auto_scale";
    public static final String PREF_UI_SCALE_X = "ui_scale_x";
    public static final String PREF_UI_SCALE_Y = "ui_scale_y";
    public static final String PREF_UI_OFFSET_X = "ui_offset_x";
    public static final String PREF_UI_OFFSET_Y = "ui_offset_y";

    public static final boolean DEFAULT_AUTO_SCALE = true;
    public static final float DEFAULT_SCALE_X = 1.0f;
    public static final float DEFAULT_SCALE_Y = 1.0f;
    public static final float DEFAULT_OFFSET_X = 0.0f;
    public static final float DEFAULT_OFFSET_Y = 0.0f;

    public static final float MIN_SCALE = 0.70f;
    public static final float MAX_SCALE = 1.30f;
    public static final float MIN_OFFSET_X = -520f;
    public static final float MAX_OFFSET_X = 520f;
    public static final float MIN_OFFSET_Y = -180f;
    public static final float MAX_OFFSET_Y = 180f;

    public static final float DESIGN_W = 2560f;
    public static final float DESIGN_H = 720f;

    private UiScaleHelper() {
    }

    public static boolean autoScaleEnabled(Context context) {
        return prefs(context).getBoolean(PREF_UI_AUTO_SCALE, DEFAULT_AUTO_SCALE);
    }

    public static float scaleX(Context context) {
        SharedPreferences sp = prefs(context);
        float manual = clamp(sp.getFloat(PREF_UI_SCALE_X, DEFAULT_SCALE_X), MIN_SCALE, MAX_SCALE);
        return clamp(autoScaleX(context) * manual, 0.45f, 1.80f);
    }

    public static float scaleY(Context context) {
        SharedPreferences sp = prefs(context);
        float manual = clamp(sp.getFloat(PREF_UI_SCALE_Y, DEFAULT_SCALE_Y), MIN_SCALE, MAX_SCALE);
        return clamp(autoScaleY(context) * manual, 0.45f, 1.80f);
    }

    public static float manualScaleX(Context context) {
        return clamp(prefs(context).getFloat(PREF_UI_SCALE_X, DEFAULT_SCALE_X), MIN_SCALE, MAX_SCALE);
    }

    public static float manualScaleY(Context context) {
        return clamp(prefs(context).getFloat(PREF_UI_SCALE_Y, DEFAULT_SCALE_Y), MIN_SCALE, MAX_SCALE);
    }

    public static float offsetX(Context context) {
        SharedPreferences sp = prefs(context);
        float manual = clamp(sp.getFloat(PREF_UI_OFFSET_X, DEFAULT_OFFSET_X), MIN_OFFSET_X, MAX_OFFSET_X);
        return autoOffsetX(context) + manual;
    }

    public static float offsetY(Context context) {
        SharedPreferences sp = prefs(context);
        float manual = clamp(sp.getFloat(PREF_UI_OFFSET_Y, DEFAULT_OFFSET_Y), MIN_OFFSET_Y, MAX_OFFSET_Y);
        return autoOffsetY(context) + manual;
    }

    public static float manualOffsetX(Context context) {
        return clamp(prefs(context).getFloat(PREF_UI_OFFSET_X, DEFAULT_OFFSET_X), MIN_OFFSET_X, MAX_OFFSET_X);
    }

    public static float manualOffsetY(Context context) {
        return clamp(prefs(context).getFloat(PREF_UI_OFFSET_Y, DEFAULT_OFFSET_Y), MIN_OFFSET_Y, MAX_OFFSET_Y);
    }

    public static float autoScaleX(Context context) {
        if (!autoScaleEnabled(context)) {
            return 1.0f;
        }
        Fit fit = computeFit(context);
        return fit.scaleX;
    }

    public static float autoScaleY(Context context) {
        if (!autoScaleEnabled(context)) {
            return 1.0f;
        }
        Fit fit = computeFit(context);
        return fit.scaleY;
    }

    public static float autoOffsetX(Context context) {
        if (!autoScaleEnabled(context)) {
            return 0.0f;
        }
        Fit fit = computeFit(context);
        return fit.offsetX;
    }

    public static float autoOffsetY(Context context) {
        if (!autoScaleEnabled(context)) {
            return 0.0f;
        }
        Fit fit = computeFit(context);
        return fit.offsetY;
    }

    public static void applyUiTransform(Canvas canvas, Context context) {
        canvas.translate(offsetX(context), offsetY(context));
        canvas.scale(scaleX(context), scaleY(context));
    }

    public static float toUiX(Context context, float designX) {
        return (designX - offsetX(context)) / safeScale(scaleX(context));
    }

    public static float toUiY(Context context, float designY) {
        return (designY - offsetY(context)) / safeScale(scaleY(context));
    }

    public static float toScreenDesignX(Context context, float uiX) {
        return offsetX(context) + uiX * scaleX(context);
    }

    public static float toScreenDesignY(Context context, float uiY) {
        return offsetY(context) + uiY * scaleY(context);
    }

    public static RectF toScreenDesignRect(Context context, RectF rect) {
        float sx = scaleX(context);
        float sy = scaleY(context);
        float ox = offsetX(context);
        float oy = offsetY(context);
        return new RectF(
                ox + rect.left * sx,
                oy + rect.top * sy,
                ox + rect.right * sx,
                oy + rect.bottom * sy
        );
    }

    public static RectF toUiRect(Context context, RectF rect) {
        float sx = safeScale(scaleX(context));
        float sy = safeScale(scaleY(context));
        float ox = offsetX(context);
        float oy = offsetY(context);
        return new RectF(
                (rect.left - ox) / sx,
                (rect.top - oy) / sy,
                (rect.right - ox) / sx,
                (rect.bottom - oy) / sy
        );
    }

    public static String currentSummary(Context context) {
        return "自动适配=" + (autoScaleEnabled(context) ? "开" : "关")
                + "，最终X=" + round2(scaleX(context))
                + "，最终Y=" + round2(scaleY(context))
                + "，偏移X=" + round1(offsetX(context))
                + "，偏移Y=" + round1(offsetY(context))
                + "，自动X=" + round2(autoScaleX(context))
                + "，自动Y=" + round2(autoScaleY(context));
    }

    public static void reset(Context context) {
        prefs(context).edit()
                .putBoolean(PREF_UI_AUTO_SCALE, DEFAULT_AUTO_SCALE)
                .putFloat(PREF_UI_SCALE_X, DEFAULT_SCALE_X)
                .putFloat(PREF_UI_SCALE_Y, DEFAULT_SCALE_Y)
                .putFloat(PREF_UI_OFFSET_X, DEFAULT_OFFSET_X)
                .putFloat(PREF_UI_OFFSET_Y, DEFAULT_OFFSET_Y)
                .apply();
    }

    public static float parseFloat(String text, float fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            String clean = text.trim().replace("%", "");
            if (clean.length() == 0) {
                return fallback;
            }
            return Float.parseFloat(clean);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static float clampScale(float value) {
        return clamp(value, MIN_SCALE, MAX_SCALE);
    }

    public static float clampOffsetX(float value) {
        return clamp(value, MIN_OFFSET_X, MAX_OFFSET_X);
    }

    public static float clampOffsetY(float value) {
        return clamp(value, MIN_OFFSET_Y, MAX_OFFSET_Y);
    }

    private static Fit computeFit(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float screenW = Math.max(1f, Math.max(dm.widthPixels, dm.heightPixels));
        float screenH = Math.max(1f, Math.min(dm.widthPixels, dm.heightPixels));

        // 外层 Canvas 已经把 2560×720 拉伸到真实屏幕。
        // 这里的内部变换用于抵消非 32:9 屏幕上的拉伸，让 UI 以 32:9 等比完整显示。
        float outerX = screenW / DESIGN_W;
        float outerY = screenH / DESIGN_H;
        float fitOuter = Math.min(outerX, outerY);

        float sx = fitOuter / Math.max(0.01f, outerX);
        float sy = fitOuter / Math.max(0.01f, outerY);

        float uiScreenW = DESIGN_W * sx * outerX;
        float uiScreenH = DESIGN_H * sy * outerY;

        float offsetScreenX = (screenW - uiScreenW) / 2f;
        float offsetScreenY = (screenH - uiScreenH) / 2f;

        float ox = offsetScreenX / Math.max(0.01f, outerX);
        float oy = offsetScreenY / Math.max(0.01f, outerY);

        return new Fit(sx, sy, ox, oy);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
    }

    private static float safeScale(float value) {
        return Math.max(0.01f, value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String round1(float value) {
        return String.valueOf(Math.round(value * 10f) / 10f);
    }

    private static String round2(float value) {
        return String.valueOf(Math.round(value * 100f) / 100f);
    }

    private static final class Fit {
        final float scaleX;
        final float scaleY;
        final float offsetX;
        final float offsetY;

        Fit(float scaleX, float scaleY, float offsetX, float offsetY) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }
}
