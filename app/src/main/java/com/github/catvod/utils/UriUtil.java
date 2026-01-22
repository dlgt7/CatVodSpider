package com.github.catvod.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.github.catvod.spider.Init;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 影视聚合工具类 - 集成 URI 解析、路径补全与媒体识别
 */
public final class UriUtil {

    // --- 常量定义 ---
    // 升级至 2026 年主流 Chrome 版本号
    public static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
    
    // 预编译正则，提高匹配效率
    private static final Pattern THUNDER = Pattern.compile("^(magnet|thunder|ed2k):", Pattern.CASE_INSENSITIVE);
    
    // 使用不可变集合，防止运行期间被意外篡改
    private static final Set<String> MEDIA_EXT;
    private static final Set<String> SUB_EXT;

    static {
        String[] media = {"mp4", "mkv", "mov", "wav", "wma", "wmv", "flv", "avi", "iso", "mpg", "ts", "mp3", "aac", "flac", "m4a", "ape", "ogg"};
        String[] sub = {"srt", "ass", "ssa", "vtt"};
        MEDIA_EXT = new HashSet<>(media.length);
        SUB_EXT = new HashSet<>(sub.length);
        Collections.addAll(MEDIA_EXT, media);
        Collections.addAll(SUB_EXT, sub);
    }

    private static final int SCHEME_COLON = 0, PATH = 1, QUERY = 2, FRAGMENT = 3, INDEX_COUNT = 4;

    // --- 业务工具方法 ---

    /**
     * 识别是否为下载协议（磁力、迅雷、电驴或种子）
     */
    public static boolean isThunder(String url) {
        if (TextUtils.isEmpty(url)) return false;
        return THUNDER.matcher(url).find() || isTorrent(url);
    }

    public static boolean isTorrent(String url) {
        if (TextUtils.isEmpty(url)) return false;
        // 查找分号位置，避免 split 生成数组带来的内存分配
        int semiIndex = url.indexOf(';');
        String cleanUrl = (semiIndex != -1) ? url.substring(0, semiIndex) : url;
        return !cleanUrl.startsWith("magnet") && cleanUrl.toLowerCase().endsWith(".torrent");
    }

    public static String getExt(String name) {
        if (TextUtils.isEmpty(name)) return "";
        int dot = name.lastIndexOf(".");
        return dot == -1 ? name.toLowerCase() : name.substring(dot + 1).toLowerCase();
    }

    public static boolean isSub(String text) {
        return SUB_EXT.contains(getExt(text));
    }

    public static boolean isMedia(String text) {
        return MEDIA_EXT.contains(getExt(text));
    }

    /**
     * 格式化文件大小，增加单位自动匹配精度
     */
    public static String getSize(double size) {
        if (size <= 0) return "";
        final String[] units = {"bytes", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static void copy(String text) {
        try {
            Context ctx = Init.context();
            if (ctx == null || text == null) return;
            ClipboardManager manager = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("fongmi", text));
            }
        } catch (Exception ignored) {}
    }

    // --- URI 核心解析逻辑 ---

    /**
     * 相对路径转绝对路径（基于 RFC-3986）
     */
    public static String resolve(@Nullable String baseUri, @Nullable String referenceUri) {
        if (baseUri == null) baseUri = "";
        if (referenceUri == null) referenceUri = "";

        int[] refIndices = getUriIndices(referenceUri);
        if (refIndices[SCHEME_COLON] != -1) {
            // 已经是绝对路径，仅格式化 "." 段
            return removeDotSegments(new StringBuilder(referenceUri), refIndices[PATH], refIndices[QUERY]);
        }

        int[] baseIndices = getUriIndices(baseUri);
        StringBuilder uri = new StringBuilder();

        if (refIndices[FRAGMENT] == 0) {
            return uri.append(baseUri, 0, baseIndices[FRAGMENT]).append(referenceUri).toString();
        }
        if (refIndices[QUERY] == 0) {
            return uri.append(baseUri, 0, baseIndices[QUERY]).append(referenceUri).toString();
        }
        if (refIndices[PATH] != 0) {
            int baseLimit = baseIndices[SCHEME_COLON] + 1;
            uri.append(baseUri, 0, baseLimit).append(referenceUri);
            return removeDotSegments(uri, baseLimit + refIndices[PATH], baseLimit + refIndices[QUERY]);
        }
        if (referenceUri.length() > 0 && referenceUri.charAt(0) == '/') {
            uri.append(baseUri, 0, baseIndices[PATH]).append(referenceUri);
            return removeDotSegments(uri, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY]);
        }

        // 处理同级路径转换
        if (baseIndices[SCHEME_COLON] + 2 < baseIndices[PATH] && baseIndices[PATH] == baseIndices[QUERY]) {
            uri.append(baseUri, 0, baseIndices[PATH]).append('/').append(referenceUri);
            return removeDotSegments(uri, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY] + 1);
        } else {
            int lastSlashIndex = baseUri.lastIndexOf('/', baseIndices[QUERY] - 1);
            int baseLimit = (lastSlashIndex == -1) ? baseIndices[PATH] : lastSlashIndex + 1;
            uri.append(baseUri, 0, baseLimit).append(referenceUri);
            return removeDotSegments(uri, baseIndices[PATH], baseLimit + refIndices[QUERY]);
        }
    }

    private static String removeDotSegments(StringBuilder uri, int offset, int limit) {
        if (offset >= limit) return uri.toString();
        if (uri.charAt(offset) == '/') offset++;
        int segmentStart = offset;
        int i = offset;
        while (i <= limit) {
            int nextSegmentStart;
            if (i == limit) nextSegmentStart = i;
            else if (uri.charAt(i) == '/') nextSegmentStart = i + 1;
            else { i++; continue; }

            if (i == segmentStart + 1 && uri.charAt(segmentStart) == '.') {
                uri.delete(segmentStart, nextSegmentStart);
                limit -= (nextSegmentStart - segmentStart);
                i = segmentStart;
            } else if (i == segmentStart + 2 && uri.charAt(segmentStart) == '.' && uri.charAt(segmentStart + 1) == '.') {
                int prevSegmentStart = uri.lastIndexOf("/", segmentStart - 2) + 1;
                int removeFrom = Math.max(prevSegmentStart, offset);
                uri.delete(removeFrom, nextSegmentStart);
                limit -= (nextSegmentStart - removeFrom);
                i = segmentStart = prevSegmentStart;
            } else {
                i++;
                segmentStart = i;
            }
        }
        return uri.toString();
    }

    private static int[] getUriIndices(String uriString) {
        int[] indices = new int[INDEX_COUNT];
        if (TextUtils.isEmpty(uriString)) {
            indices[SCHEME_COLON] = -1;
            return indices;
        }
        int length = uriString.length();
        int fragmentIndex = uriString.indexOf('#');
        if (fragmentIndex == -1) fragmentIndex = length;
        int queryIndex = uriString.indexOf('?');
        if (queryIndex == -1 || queryIndex > fragmentIndex) queryIndex = fragmentIndex;
        int schemeIndexLimit = uriString.indexOf('/');
        if (schemeIndexLimit == -1 || schemeIndexLimit > queryIndex) schemeIndexLimit = queryIndex;
        int schemeIndex = uriString.indexOf(':');
        if (schemeIndex > schemeIndexLimit) schemeIndex = -1;

        boolean hasAuthority = schemeIndex + 2 < queryIndex && length > schemeIndex + 2 
                && uriString.charAt(schemeIndex + 1) == '/' && uriString.charAt(schemeIndex + 2) == '/';
        
        int pathIndex;
        if (hasAuthority) {
            pathIndex = uriString.indexOf('/', schemeIndex + 3);
            if (pathIndex == -1 || pathIndex > queryIndex) pathIndex = queryIndex;
        } else {
            pathIndex = schemeIndex + 1;
        }
        indices[SCHEME_COLON] = schemeIndex;
        indices[PATH] = pathIndex;
        indices[QUERY] = queryIndex;
        indices[FRAGMENT] = fragmentIndex;
        return indices;
    }
}
