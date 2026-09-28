package com.github.catvod.spider;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹幕显示设置（外观 / 时间 / 密度 / 显示）
 * <p>
 * 参考 FongMi TV 宿主的 com.fongmi.android.tv.setting.DanmakuSetting 实现，
 * 键名、默认值、取值范围与宿主完全一致，并且读写同一个 SharedPreferences 文件
 * （&lt;宿主包名&gt;_preferences），因此这里保存后：
 * 1. 宿主播放器在下次配置时会直接读取生效；
 * 2. 通过 {@link #applyToHost(Activity)} 反射调用宿主 DanmakuSetting.getConfig()
 * 并推送给 PlayerManager.setDanmakuConfig() 可以立即生效。
 * <p>
 * 若宿主为旧版 FongMi（无弹幕设置能力），写入的键值不会影响宿主运行。
 */
public class DanmakuDisplaySettings {

    // ========== 取值范围（与宿主 DanmakuSetting 一致） ==========
    private static final float MIN_TEXT_SCALE = 0.5f;
    private static final float MAX_TEXT_SCALE = 3.0f;
    private static final float MIN_TRANSPARENCY = 0.0f;
    private static final float MAX_TRANSPARENCY = 0.9f;
    private static final float MIN_STROKE_WIDTH_MULTIPLIER = 0.05f;
    private static final float MAX_STROKE_WIDTH_MULTIPLIER = 0.3f;
    private static final float MIN_PROJECTION_OFFSET = 0.02f;
    private static final float MAX_PROJECTION_OFFSET = 0.15f;
    private static final long MIN_TIME_OFFSET_MS = -300000L;
    private static final long MAX_TIME_OFFSET_MS = 300000L;
    private static final long MIN_DURATION_MS = 3000L;
    private static final long MAX_DURATION_MS = 15000L;
    private static final long MIN_FIXED_DURATION_MS = 2000L;
    private static final long MAX_FIXED_DURATION_MS = 10000L;
    private static final int MIN_MAX_ON_SCREEN = 10;
    private static final int MAX_MAX_ON_SCREEN = 500;
    private static final float MIN_SCROLL_AREA_RATIO = 0.1f;
    private static final float MAX_SCROLL_AREA_RATIO = 1.0f;
    private static final float MIN_SCROLL_GAP_RATIO = 0.0f;
    private static final float MAX_SCROLL_GAP_RATIO = 5.0f;
    private static final float MIN_LINE_SPACING = 1.0f;
    private static final float MAX_LINE_SPACING = 2.0f;
    private static final int MIN_MAX_SCROLL_LINES = 0;
    private static final int MAX_MAX_SCROLL_LINES = 20;
    private static final int MIN_MAX_FIXED_LINES = 0;
    private static final int MAX_MAX_FIXED_LINES = 10;

    // ========== SharedPreferences 键（与宿主一致） ==========
    private static final String KEY_TEXT_SCALE = "danmaku_text_scale";
    private static final String KEY_TRANSPARENCY = "danmaku_transparency";
    private static final String KEY_TEXT_BOLD = "danmaku_text_bold";
    private static final String KEY_STYLE_MODE = "danmaku_style_mode";
    private static final String KEY_SHADOW_TRANSPARENCY = "danmaku_shadow_transparency";
    private static final String KEY_STROKE_WIDTH_MULTIPLIER = "danmaku_stroke_width_multiplier";
    private static final String KEY_PROJECTION_OFFSET_X = "danmaku_projection_offset_x";
    private static final String KEY_PROJECTION_OFFSET_Y = "danmaku_projection_offset_y";
    private static final String KEY_PROJECTION_TRANSPARENCY = "danmaku_projection_transparency";
    private static final String KEY_COLOR_MODE = "danmaku_color_mode";
    private static final String KEY_TIME_OFFSET = "danmaku_time_offset";
    private static final String KEY_DURATION = "danmaku_duration";
    private static final String KEY_FIXED_DURATION = "danmaku_fixed_duration";
    private static final String KEY_MAX_ON_SCREEN = "danmaku_max_on_screen";
    private static final String KEY_SCROLL_AREA_RATIO = "danmaku_scroll_area_ratio";
    private static final String KEY_SCROLL_GAP_RATIO = "danmaku_scroll_gap_ratio";
    private static final String KEY_LINE_SPACING = "danmaku_line_spacing";
    private static final String KEY_MAX_SCROLL_LINES = "danmaku_max_scroll_lines";
    private static final String KEY_MAX_TOP_LINES = "danmaku_max_top_lines";
    private static final String KEY_MAX_BOTTOM_LINES = "danmaku_max_bottom_lines";
    private static final String KEY_SHOW_SCROLL = "danmaku_show_scroll";
    private static final String KEY_SHOW_TOP = "danmaku_show_top";
    private static final String KEY_SHOW_BOTTOM = "danmaku_show_bottom";
    private static final String KEY_SHOW_REVERSE = "danmaku_show_reverse";
    private static final String KEY_SHOW_POSITIONED = "danmaku_show_positioned";
    private static final String KEY_SHOW_SUBTITLE = "danmaku_show_subtitle";
    private static final String KEY_SHOW_SPECIAL = "danmaku_show_special";

    // ========== 弹幕样式索引（内部使用） ==========
    public static final int STYLE_INDEX_NONE = 0;
    public static final int STYLE_INDEX_SHADOW = 1;
    public static final int STYLE_INDEX_STROKE = 2;
    public static final int STYLE_INDEX_PROJECTION = 3;
    public static final int COLOR_INDEX_DEFAULT = 0;
    public static final int COLOR_INDEX_COLORFUL = 1;
    public static final int COLOR_INDEX_GRADIENT = 2;

    /**
     * 宿主 androidx.media3.ui.danmaku.DanmakuConfig 的常量缓存。
     * 顺序：STYLE_NONE, STYLE_SHADOW, STYLE_STROKE, STYLE_PROJECTION,
     * COLOR_MODE_DEFAULT, COLOR_MODE_COLORFUL, COLOR_MODE_GRADIENT
     */
    private static final ConcurrentHashMap<String, int[]> HOST_CONSTANT_CACHE = new ConcurrentHashMap<>();

    private DanmakuDisplaySettings() {
    }

    // ========== SharedPreferences ==========
    private static SharedPreferences prefs(Context context) {
        Context ctx = context != null ? context.getApplicationContext() : Init.context();
        if (ctx == null) return null;
        return ctx.getSharedPreferences(ctx.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    private static float getFloat(Context context, String key, float def) {
        SharedPreferences prefers = prefs(context);
        if (prefers == null) return def;
        try {
            return prefers.getFloat(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static int getInt(Context context, String key, int def) {
        SharedPreferences prefers = prefs(context);
        if (prefers == null) return def;
        try {
            return prefers.getInt(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static long getLong(Context context, String key, long def) {
        SharedPreferences prefers = prefs(context);
        if (prefers == null) return def;
        try {
            return prefers.getLong(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean getBoolean(Context context, String key, boolean def) {
        SharedPreferences prefers = prefs(context);
        if (prefers == null) return def;
        try {
            return prefers.getBoolean(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static void putFloat(Context context, String key, float value) {
        SharedPreferences prefers = prefs(context);
        if (prefers != null) prefers.edit().putFloat(key, value).apply();
    }

    private static void putInt(Context context, String key, int value) {
        SharedPreferences prefers = prefs(context);
        if (prefers != null) prefers.edit().putInt(key, value).apply();
    }

    private static void putLong(Context context, String key, long value) {
        SharedPreferences prefers = prefs(context);
        if (prefers != null) prefers.edit().putLong(key, value).apply();
    }

    private static void putBoolean(Context context, String key, boolean value) {
        SharedPreferences prefers = prefs(context);
        if (prefers != null) prefers.edit().putBoolean(key, value).apply();
    }

    // ========== 宿主常量解析 ==========

    /**
     * 读取宿主 androidx.media3.ui.danmaku.DanmakuConfig 中的静态常量，
     * 避免硬编码样式值与宿主版本不一致。读取失败时回退到 0/1/2/3 顺序。
     */
    private static int[] hostConstants(Context context) {
        int[] cached = HOST_CONSTANT_CACHE.get("danmaku_config");
        if (cached != null) return cached;
        int[] result = {0, 1, 2, 3, 0, 1, 2};
        try {
            ClassLoader loader = context instanceof Activity ? ((Activity) context).getClassLoader() : DanmakuDisplaySettings.class.getClassLoader();
            if (loader == null) loader = DanmakuDisplaySettings.class.getClassLoader();
            Class<?> cls = Class.forName("androidx.media3.ui.danmaku.DanmakuConfig", false, loader);
            result[0] = readStaticInt(cls, "STYLE_NONE", result[0]);
            result[1] = readStaticInt(cls, "STYLE_SHADOW", result[1]);
            result[2] = readStaticInt(cls, "STYLE_STROKE", result[2]);
            result[3] = readStaticInt(cls, "STYLE_PROJECTION", result[3]);
            result[4] = readStaticInt(cls, "COLOR_MODE_DEFAULT", result[4]);
            result[5] = readStaticInt(cls, "COLOR_MODE_COLORFUL", result[5]);
            result[6] = readStaticInt(cls, "COLOR_MODE_GRADIENT", result[6]);
        } catch (Throwable ignored) {
        }
        HOST_CONSTANT_CACHE.put("danmaku_config", result);
        return result;
    }

    private static int readStaticInt(Class<?> cls, String field, int def) {
        try {
            java.lang.reflect.Field f = cls.getField(field);
            Object value = f.get(null);
            if (value instanceof Integer) return (Integer) value;
        } catch (Throwable ignored) {
        }
        return def;
    }

    // ========== 外观（Appearance） ==========

    public static float getTextScale(Context context) {
        return clamp(getFloat(context, KEY_TEXT_SCALE, 1.0f), MIN_TEXT_SCALE, MAX_TEXT_SCALE);
    }

    public static void putTextScale(Context context, float value) {
        putFloat(context, KEY_TEXT_SCALE, clamp(value, MIN_TEXT_SCALE, MAX_TEXT_SCALE));
    }

    public static float getTransparency(Context context) {
        return clamp(getFloat(context, KEY_TRANSPARENCY, 0.0f), MIN_TRANSPARENCY, MAX_TRANSPARENCY);
    }

    public static void putTransparency(Context context, float value) {
        putFloat(context, KEY_TRANSPARENCY, clamp(value, MIN_TRANSPARENCY, MAX_TRANSPARENCY));
    }

    public static boolean isTextBold(Context context) {
        return getBoolean(context, KEY_TEXT_BOLD, false);
    }

    public static void putTextBold(Context context, boolean value) {
        putBoolean(context, KEY_TEXT_BOLD, value);
    }

    public static int getStyleMode(Context context) {
        // 宿主默认值为 DanmakuConfig.STYLE_STROKE
        return getInt(context, KEY_STYLE_MODE, hostConstants(context)[STYLE_INDEX_STROKE]);
    }

    public static void putStyleMode(Context context, int value) {
        putInt(context, KEY_STYLE_MODE, value);
    }

    public static int getStyleIndex(Context context) {
        int mode = getStyleMode(context);
        int[] constants = hostConstants(context);
        for (int i = 0; i < 4; i++) {
            if (constants[i] == mode) return i;
        }
        return STYLE_INDEX_STROKE;
    }

    public static void putStyleIndex(Context context, int index) {
        if (index < STYLE_INDEX_NONE || index > STYLE_INDEX_PROJECTION) index = STYLE_INDEX_STROKE;
        putStyleMode(context, hostConstants(context)[index]);
    }

    public static int getColorMode(Context context) {
        return getInt(context, KEY_COLOR_MODE, hostConstants(context)[COLOR_INDEX_DEFAULT]);
    }

    public static void putColorMode(Context context, int value) {
        putInt(context, KEY_COLOR_MODE, value);
    }

    public static int getColorIndex(Context context) {
        int mode = getColorMode(context);
        int[] constants = hostConstants(context);
        for (int i = 0; i < 3; i++) {
            if (constants[4 + i] == mode) return i;
        }
        return COLOR_INDEX_DEFAULT;
    }

    public static void putColorIndex(Context context, int index) {
        if (index < COLOR_INDEX_DEFAULT || index > COLOR_INDEX_GRADIENT) index = COLOR_INDEX_DEFAULT;
        putColorMode(context, hostConstants(context)[4 + index]);
    }

    public static float getShadowTransparency(Context context) {
        return clamp(getFloat(context, KEY_SHADOW_TRANSPARENCY, 0.1f), MIN_TRANSPARENCY, MAX_TRANSPARENCY);
    }

    public static void putShadowTransparency(Context context, float value) {
        putFloat(context, KEY_SHADOW_TRANSPARENCY, clamp(value, MIN_TRANSPARENCY, MAX_TRANSPARENCY));
    }

    public static float getStrokeWidthMultiplier(Context context) {
        return clamp(getFloat(context, KEY_STROKE_WIDTH_MULTIPLIER, 0.12f), MIN_STROKE_WIDTH_MULTIPLIER, MAX_STROKE_WIDTH_MULTIPLIER);
    }

    public static void putStrokeWidthMultiplier(Context context, float value) {
        putFloat(context, KEY_STROKE_WIDTH_MULTIPLIER, clamp(value, MIN_STROKE_WIDTH_MULTIPLIER, MAX_STROKE_WIDTH_MULTIPLIER));
    }

    public static float getProjectionOffsetX(Context context) {
        return clamp(getFloat(context, KEY_PROJECTION_OFFSET_X, 0.08f), MIN_PROJECTION_OFFSET, MAX_PROJECTION_OFFSET);
    }

    public static void putProjectionOffsetX(Context context, float value) {
        putFloat(context, KEY_PROJECTION_OFFSET_X, clamp(value, MIN_PROJECTION_OFFSET, MAX_PROJECTION_OFFSET));
    }

    public static float getProjectionOffsetY(Context context) {
        return clamp(getFloat(context, KEY_PROJECTION_OFFSET_Y, 0.08f), MIN_PROJECTION_OFFSET, MAX_PROJECTION_OFFSET);
    }

    public static void putProjectionOffsetY(Context context, float value) {
        putFloat(context, KEY_PROJECTION_OFFSET_Y, clamp(value, MIN_PROJECTION_OFFSET, MAX_PROJECTION_OFFSET));
    }

    public static float getProjectionTransparency(Context context) {
        return clamp(getFloat(context, KEY_PROJECTION_TRANSPARENCY, 0.2f), MIN_TRANSPARENCY, MAX_TRANSPARENCY);
    }

    public static void putProjectionTransparency(Context context, float value) {
        putFloat(context, KEY_PROJECTION_TRANSPARENCY, clamp(value, MIN_TRANSPARENCY, MAX_TRANSPARENCY));
    }

    // ========== 时间（Timing） ==========

    public static long getTimeOffsetMs(Context context) {
        return clamp(getLong(context, KEY_TIME_OFFSET, 0L), MIN_TIME_OFFSET_MS, MAX_TIME_OFFSET_MS);
    }

    public static void putTimeOffsetMs(Context context, long value) {
        putLong(context, KEY_TIME_OFFSET, clamp(value, MIN_TIME_OFFSET_MS, MAX_TIME_OFFSET_MS));
    }

    public static long getDurationMs(Context context) {
        return clamp(getLong(context, KEY_DURATION, 8000L), MIN_DURATION_MS, MAX_DURATION_MS);
    }

    public static void putDurationMs(Context context, long value) {
        putLong(context, KEY_DURATION, clamp(value, MIN_DURATION_MS, MAX_DURATION_MS));
    }

    public static long getFixedDurationMs(Context context) {
        return clamp(getLong(context, KEY_FIXED_DURATION, 5000L), MIN_FIXED_DURATION_MS, MAX_FIXED_DURATION_MS);
    }

    public static void putFixedDurationMs(Context context, long value) {
        putLong(context, KEY_FIXED_DURATION, clamp(value, MIN_FIXED_DURATION_MS, MAX_FIXED_DURATION_MS));
    }

    // ========== 密度（Density） ==========

    public static int getMaxOnScreen(Context context) {
        return clamp(getInt(context, KEY_MAX_ON_SCREEN, 150), MIN_MAX_ON_SCREEN, MAX_MAX_ON_SCREEN);
    }

    public static void putMaxOnScreen(Context context, int value) {
        putInt(context, KEY_MAX_ON_SCREEN, clamp(value, MIN_MAX_ON_SCREEN, MAX_MAX_ON_SCREEN));
    }

    public static float getScrollAreaRatio(Context context) {
        return clamp(getFloat(context, KEY_SCROLL_AREA_RATIO, 0.5f), MIN_SCROLL_AREA_RATIO, MAX_SCROLL_AREA_RATIO);
    }

    public static void putScrollAreaRatio(Context context, float value) {
        putFloat(context, KEY_SCROLL_AREA_RATIO, clamp(value, MIN_SCROLL_AREA_RATIO, MAX_SCROLL_AREA_RATIO));
    }

    public static float getScrollGapRatio(Context context) {
        return clamp(getFloat(context, KEY_SCROLL_GAP_RATIO, 0.0f), MIN_SCROLL_GAP_RATIO, MAX_SCROLL_GAP_RATIO);
    }

    public static void putScrollGapRatio(Context context, float value) {
        putFloat(context, KEY_SCROLL_GAP_RATIO, clamp(value, MIN_SCROLL_GAP_RATIO, MAX_SCROLL_GAP_RATIO));
    }

    public static float getLineSpacing(Context context) {
        return clamp(getFloat(context, KEY_LINE_SPACING, 1.4f), MIN_LINE_SPACING, MAX_LINE_SPACING);
    }

    public static void putLineSpacing(Context context, float value) {
        putFloat(context, KEY_LINE_SPACING, clamp(value, MIN_LINE_SPACING, MAX_LINE_SPACING));
    }

    public static int getMaxScrollLines(Context context) {
        return clamp(getInt(context, KEY_MAX_SCROLL_LINES, 0), MIN_MAX_SCROLL_LINES, MAX_MAX_SCROLL_LINES);
    }

    public static void putMaxScrollLines(Context context, int value) {
        putInt(context, KEY_MAX_SCROLL_LINES, clamp(value, MIN_MAX_SCROLL_LINES, MAX_MAX_SCROLL_LINES));
    }

    public static int getMaxTopLines(Context context) {
        return clamp(getInt(context, KEY_MAX_TOP_LINES, 0), MIN_MAX_FIXED_LINES, MAX_MAX_FIXED_LINES);
    }

    public static void putMaxTopLines(Context context, int value) {
        putInt(context, KEY_MAX_TOP_LINES, clamp(value, MIN_MAX_FIXED_LINES, MAX_MAX_FIXED_LINES));
    }

    public static int getMaxBottomLines(Context context) {
        return clamp(getInt(context, KEY_MAX_BOTTOM_LINES, 0), MIN_MAX_FIXED_LINES, MAX_MAX_FIXED_LINES);
    }

    public static void putMaxBottomLines(Context context, int value) {
        putInt(context, KEY_MAX_BOTTOM_LINES, clamp(value, MIN_MAX_FIXED_LINES, MAX_MAX_FIXED_LINES));
    }

    // ========== 显示（Display） ==========

    public static boolean isShowScroll(Context context) {
        return getBoolean(context, KEY_SHOW_SCROLL, true);
    }

    public static void putShowScroll(Context context, boolean value) {
        putBoolean(context, KEY_SHOW_SCROLL, value);
    }

    public static boolean isShowTop(Context context) {
        return getBoolean(context, KEY_SHOW_TOP, true);
    }

    public static void putShowTop(Context context, boolean value) {
        putBoolean(context, KEY_SHOW_TOP, value);
    }

    public static boolean isShowBottom(Context context) {
        return getBoolean(context, KEY_SHOW_BOTTOM, true);
    }

    public static void putShowBottom(Context context, boolean value) {
        putBoolean(context, KEY_SHOW_BOTTOM, value);
    }

    public static boolean isShowReverse(Context context) {
        return getBoolean(context, KEY_SHOW_REVERSE, true);
    }

    public static void putShowReverse(Context context, boolean value) {
        putBoolean(context, KEY_SHOW_REVERSE, value);
    }

    public static boolean isShowPositioned(Context context) {
        return getBoolean(context, KEY_SHOW_POSITIONED, true);
    }

    public static void putShowPositioned(Context context, boolean value) {
        putBoolean(context, KEY_SHOW_POSITIONED, value);
    }

    public static boolean isShowSubtitle(Context context) {
        return getBoolean(context, KEY_SHOW_SUBTITLE, true);
    }

    public static void putShowSubtitle(Context context, boolean value) {
        putBoolean(context, KEY_SHOW_SUBTITLE, value);
    }

    public static boolean isShowSpecial(Context context) {
        return getBoolean(context, KEY_SHOW_SPECIAL, true);
    }

    public static void putShowSpecial(Context context, boolean value) {
        putBoolean(context, KEY_SHOW_SPECIAL, value);
    }

    // ========== 恢复默认 ==========

    public static void resetAppearance(Context context) {
        putTextScale(context, 1.0f);
        putTransparency(context, 0.0f);
        putTextBold(context, false);
        putStyleIndex(context, STYLE_INDEX_STROKE);
        putShadowTransparency(context, 0.1f);
        putStrokeWidthMultiplier(context, 0.12f);
        putProjectionOffsetX(context, 0.08f);
        putProjectionOffsetY(context, 0.08f);
        putProjectionTransparency(context, 0.2f);
        putColorIndex(context, COLOR_INDEX_DEFAULT);
    }

    public static void resetTiming(Context context) {
        putTimeOffsetMs(context, 0L);
        putDurationMs(context, 8000L);
        putFixedDurationMs(context, 5000L);
    }

    public static void resetDensity(Context context) {
        putMaxOnScreen(context, 150);
        putScrollAreaRatio(context, 0.5f);
        putScrollGapRatio(context, 0.0f);
        putLineSpacing(context, 1.4f);
        putMaxScrollLines(context, 0);
        putMaxTopLines(context, 0);
        putMaxBottomLines(context, 0);
    }

    public static void resetDisplay(Context context) {
        putShowScroll(context, true);
        putShowTop(context, true);
        putShowBottom(context, true);
        putShowReverse(context, true);
        putShowPositioned(context, true);
        putShowSubtitle(context, true);
        putShowSpecial(context, true);
    }

    public static void resetAll(Context context) {
        resetAppearance(context);
        resetTiming(context);
        resetDensity(context);
        resetDisplay(context);
    }

    // ========== 实时应用到宿主 ==========

    /**
     * 将当前设置实时应用到宿主播放器（FongMi TV）。
     * 原理：反射调用宿主 DanmakuSetting.getConfig() 组装配置对象，
     * 再调用宿主 PlayerManager.setDanmakuConfig(DanmakuConfig)。
     * 宿主不支持时返回 false（设置仍会持久化，等待宿主下次生效）。
     */
    public static boolean applyToHost(Activity activity) {
        try {
            return LeoDanmakuService.pushDanmakuConfigToHost(activity);
        } catch (Throwable e) {
            DanmakuSpider.log("应用弹幕设置到宿主异常: " + e.getMessage());
            return false;
        }
    }

    // ========== 工具 ==========

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    private static long clamp(long value, long min, long max) {
        return value < min ? min : Math.min(value, max);
    }
}
