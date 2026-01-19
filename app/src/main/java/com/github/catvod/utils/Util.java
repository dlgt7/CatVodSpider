package com.github.catvod.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.github.catvod.spider.Init;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class Util {

    public static final Pattern THUNDER = Pattern.compile("(magnet|thunder|ed2k):.*");
    public static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
    public static final List<String> MEDIA = Arrays.asList("mp4", "mkv", "mov", "wav", "wma", "wmv", "flv", "avi", "iso", "mpg", "ts", "mp3", "aac", "flac", "m4a", "ape", "ogg");
    public static final List<String> SUB = Arrays.asList("srt", "ass", "ssa", "vtt");

    public static boolean isThunder(String url) {
        return THUNDER.matcher(url).find() || isTorrent(url);
    }

    public static boolean isTorrent(String url) {
        if (url == null || url.startsWith("magnet")) return false;
        String cleanUrl = url.split(";")[0];
        int lastDotIndex = cleanUrl.lastIndexOf('.');
        if (lastDotIndex == -1) return false;
        String ext = cleanUrl.substring(lastDotIndex + 1);
        // 处理可能带查询参数的情况，如 file.torrent?v=1
        if (ext.startsWith("torrent")) return true;
        return cleanUrl.toLowerCase().endsWith(".torrent");
    }

    public static boolean isSub(String text) {
        return SUB.contains(getExt(text).toLowerCase());
    }

    public static boolean isMedia(String text) {
        return MEDIA.contains(getExt(text).toLowerCase());
    }

    public static String getExt(String name) {
        if (name == null) return "";
        // 先提取文件名（去掉路径部分）
        int lastSlashIndex = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String fileName = lastSlashIndex >= 0 ? name.substring(lastSlashIndex + 1) : name;
        return fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase() : fileName.toLowerCase();
    }

    public static String getSize(double size) {
        if (size <= 0) return "";
        String[] units = new String[]{"bytes", "KB", "MB", "GB", "TB"};
        int digitGroups = 0;
        double currentSize = size;
        while (currentSize >= 1024 && digitGroups < units.length - 1) {
            currentSize /= 1024;
            digitGroups++;
        }
        // 确保不会越界
        digitGroups = Math.min(digitGroups, units.length - 1);
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String removeExt(String text) {
        if (text == null) return text;
        // 先提取文件名（去掉路径部分）
        int lastSlashIndex = Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'));
        int lastDotIndex = text.lastIndexOf('.');
        
        // 如果点在最后一个斜杠之后，则表示是扩展名
        if (lastDotIndex > lastSlashIndex) {
            return text.substring(0, lastDotIndex);
        } else {
            return text; // 没有扩展名或路径中有点的情况
        }
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) {
            return text.substring(0, text.length() - num);
        } else {
            return text;
        }
    }

    public static String getVar(String data, String param) {
        for (String var : data.split("var")) if (var.contains(param)) return checkVar(var);
        return "";
    }

    private static String checkVar(String var) {
        if (var.contains("'")) {
            String[] parts = var.split("'");
            if (parts.length >= 2) return parts[1];
        }
        if (var.contains("\"")) {
            String[] parts = var.split("\"");
            if (parts.length >= 2) return parts[1];
        }
        return "";
    }

    public static void copy(String text) {
        ClipboardManager manager = (ClipboardManager) Init.context().getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("fongmi", text));
        Notify.show("已複製 " + text);
    }
    
    public static String urlEncode(String str) {
        try {
            return java.net.URLEncoder.encode(str, "UTF-8");
        } catch (Exception e) {
            return str;
        }
    }
    
}
