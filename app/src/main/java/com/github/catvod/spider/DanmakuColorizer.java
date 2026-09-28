package com.github.catvod.spider;

import android.graphics.Color;
import android.text.TextUtils;

public class DanmakuColorizer {

    private static final int[] COLORFUL_PALETTE = {
            0xFFFE0302, 0xFFFF7204, 0xFFFFAA02, 0xFFFFD302, 0xFFFFFF00,
            0xFFA0EE00, 0xFF00CD00, 0xFF019833, 0xFF66CCFF, 0xFF4266BE,
            0xFF89D5EC, 0xFF7A73E8, 0xFFEC8ED4, 0xFFFF66CC, 0xFFFFBD01,
            0xFF4DD5A2
    };

    private static final int COLOR_FIELD_INDEX = 3;
    private static final float GRADIENT_HUE_PER_SECOND = 6f;
    private static final float GRADIENT_SATURATION = 0.75f;
    private static final float GRADIENT_VALUE = 1.0f;

    public static String applyColorMode(String xmlData, int colorIndex) {
        boolean colorful = colorIndex == DanmakuDisplaySettings.COLOR_INDEX_COLORFUL;
        boolean gradient = colorIndex == DanmakuDisplaySettings.COLOR_INDEX_GRADIENT;
        if (!colorful && !gradient) return xmlData;
        if (TextUtils.isEmpty(xmlData)) return xmlData;

        try {
            int coloredCount = 0;
            StringBuilder output = new StringBuilder(xmlData.length() + 64);
            int index = 0;

            while (index < xmlData.length()) {
                int tagStart = xmlData.indexOf("<d", index);
                if (tagStart < 0) {
                    output.append(xmlData, index, xmlData.length());
                    break;
                }

                if (!isDanmakuTag(xmlData, tagStart)) {
                    output.append(xmlData, index, tagStart + 2);
                    index = tagStart + 2;
                    continue;
                }

                int tagEnd = xmlData.indexOf('>', tagStart + 2);
                if (tagEnd < 0) {
                    output.append(xmlData, index, xmlData.length());
                    break;
                }

                output.append(xmlData, index, tagStart);
                String tag = rewriteTagColor(xmlData, tagStart, tagEnd + 1, colorful);
                if (tag != null) {
                    output.append(tag);
                    coloredCount++;
                } else {
                    output.append(xmlData, tagStart, tagEnd + 1);
                }
                index = tagEnd + 1;
            }

            if (coloredCount == 0) return xmlData;
            DanmakuSpider.log("🎨 已应用弹幕颜色模式: " + (colorful ? "彩色" : "渐变") + "，处理 " + coloredCount + " 条");
            return output.toString();
        } catch (Exception e) {
            DanmakuSpider.log("弹幕颜色处理失败，使用原始弹幕: " + e.getMessage());
            return xmlData;
        }
    }

    private static String rewriteTagColor(String xmlData, int tagStart, int tagEnd, boolean colorful) {
        int valueStart = findPValueStart(xmlData, tagStart + 2, tagEnd);
        if (valueStart < 0) return null;

        int valueEnd = findPValueEnd(xmlData, valueStart, tagEnd);
        if (valueEnd <= valueStart) return null;

        int comma1 = -1;
        int comma2 = -1;
        int comma3 = -1;
        for (int i = valueStart; i < valueEnd; i++) {
            if (xmlData.charAt(i) != ',') continue;
            if (comma1 < 0) comma1 = i;
            else if (comma2 < 0) comma2 = i;
            else if (comma3 < 0) comma3 = i;
            else break;
        }
        if (comma1 < 0 || comma2 < 0 || comma3 < 0) return null;

        int colorStart = comma3 + 1;
        int colorEnd = comma3;
        for (int i = colorStart; i < valueEnd; i++) {
            if (xmlData.charAt(i) == ',') {
                colorEnd = i;
                break;
            }
            colorEnd = i + 1;
        }
        if (colorEnd <= colorStart) return null;

        try {
            Long.parseLong(xmlData.substring(colorStart, colorEnd).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }

        int color;
        if (colorful) {
            color = pickColorfulColor(xmlData.substring(valueStart, valueEnd));
        } else {
            color = pickGradientColor(xmlData, valueStart, comma1);
        }
        int rgb = color & 0xFFFFFF;
        if (rgb == 0) return null;

        String colorText = String.valueOf(rgb);
        StringBuilder tag = new StringBuilder(tagEnd - tagStart + colorText.length());
        tag.append(xmlData, tagStart, colorStart);
        tag.append(colorText);
        tag.append(xmlData, colorEnd, tagEnd);
        return tag.toString();
    }

    private static int pickColorfulColor(String pValue) {
        int hash = 0x811C9DC5;
        for (int i = 0; i < pValue.length(); i++) {
            hash ^= pValue.charAt(i);
            hash *= 0x01000193;
        }
        int idx = (hash >>> 1) % COLORFUL_PALETTE.length;
        return COLORFUL_PALETTE[idx];
    }

    private static int pickGradientColor(String xmlData, int valueStart, int comma1) {
        try {
            float seconds = Float.parseFloat(xmlData.substring(valueStart, comma1).trim());
            float hue = (seconds * GRADIENT_HUE_PER_SECOND) % 360f;
            if (hue < 0) hue += 360f;
            float[] hsv = {hue, GRADIENT_SATURATION, GRADIENT_VALUE};
            return Color.HSVToColor(hsv);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isDanmakuTag(String text, int tagStart) {
        int next = tagStart + 2;
        if (next >= text.length()) return false;
        char c = text.charAt(next);
        return Character.isWhitespace(c) || c == '>' || c == '/';
    }

    private static int findPValueStart(String text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (text.charAt(i) != 'p') continue;
            if (i > start && !Character.isWhitespace(text.charAt(i - 1))) continue;
            int next = i + 1;
            while (next < end && Character.isWhitespace(text.charAt(next))) next++;
            if (next < end && text.charAt(next) != '=') continue;
            next++;
            while (next < end && Character.isWhitespace(text.charAt(next))) next++;
            if (next >= end) return -1;
            char quote = text.charAt(next);
            if (quote == '"' || quote == '\'') return next + 1;
            return next;
        }
        return -1;
    }

    private static int findPValueEnd(String text, int valueStart, int end) {
        for (int i = valueStart; i < end; i++) {
            if (text.charAt(i) == '"' || text.charAt(i) == '\'') return i;
        }
        return end;
    }
}
