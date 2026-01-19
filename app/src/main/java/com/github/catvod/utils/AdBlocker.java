package com.github.catvod.utils;

import android.text.TextUtils;
import com.github.catvod.crawler.SpiderDebug;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 广告过滤器 - 旗舰版
 * 1. 支持嵌套 M3U8 拦截 (#EXT-X-STREAM-INF)
 * 2. 状态机维护 #EXTINF 标签平衡
 * 3. 拦截日志静默处理（防刷屏）
 */
public class AdBlocker {

    private static final List<String> AD_DOMAIN_PATTERNS = Arrays.asList(
            "ads?\\.", "\\.ad\\.", "doubleclick", "googlesyndication", "googletagmanager",
            "log\\.bytedance", "mssdk\\.", "adservice", "gdt\\.qq\\.com", "lancer\\.iqiyi",
            "cupid\\.iqiyi", "cm\\.l\\.qq\\.com", "pgdt\\.gtimg", "ev\\.v\\.qq\\.com",
            "business\\.msstatic", "static\\.g\\.iqiyi", "adv\\.", "analytics",
            "ykad", "atm\\.youku", "adx", "star-ad", "v1-ad", "v2-ad", "v3-ad",
            "iad\\.g\\.163", "livep\\.l\\.aiseet", "lives\\.l\\.ott\\.video\\.qq\\.com",
            "pgdt\\.ugdtimg", "q\\.i\\.gdt", "tj\\.video\\.qq", "vlive\\.qqvideo",
            "admaster", "miaozhen", "gridsum"
    );

    private static final Pattern AD_PATTERN = Pattern.compile("(" + String.join("|", AD_DOMAIN_PATTERNS) + ")", Pattern.CASE_INSENSITIVE);
    private static final String[] M3U8_AD_KEYWORDS = {"#EXT-X-AD-", "#EXT-X-CUE-", "AD-TRACKING", "#EXT-X-SPONSOR-", "#EXT-X-DISCONTINUITY"};

    public static boolean isAdUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lowerUrl = url.toLowerCase();

        // 1. 权重识别：媒体文件 + 广告关键字
        String ext = Util.getExt(url).toLowerCase();
        if (Util.MEDIA.contains(ext)) {
            if (lowerUrl.contains("/ad/") || lowerUrl.contains("/adv/") || lowerUrl.contains("v-ad") || lowerUrl.contains("advert")) {
                return true;
            }
        }

        // 2. 域名/路径正则匹配
        Matcher matcher = AD_PATTERN.matcher(lowerUrl);
        return matcher.find();
    }

    /**
     * 深度清洗 M3U8
     */
    public static String filterM3u8Content(String content) {
        if (TextUtils.isEmpty(content) || !content.contains("#EXTM3U")) return content;

        String[] lines = content.split("\n");
        StringBuilder cleanContent = new StringBuilder();
        String pendingInf = null; 
        String pendingStreamInf = null;
        int blockCount = 0; // 拦截计数器

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // --- A. 处理嵌套 M3U8 (#EXT-X-STREAM-INF) ---
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                String nextLine = (i + 1 < lines.length) ? lines[i + 1].trim() : "";
                if (isAdUrl(nextLine)) {
                    blockCount++;
                    i++; // 跳过下一行 URL
                    pendingStreamInf = null;
                    continue;
                }
                // 正常流，记录标签，等待下一行 URL
                pendingStreamInf = line;
                continue;
            }

            // --- B. 处理切片标签 (#EXTINF) ---
            if (line.startsWith("#EXTINF")) {
                pendingInf = line;
                continue;
            }

            // --- C. 检查特定的广告描述标签 ---
            boolean isAdTag = false;
            for (String keyword : M3U8_AD_KEYWORDS) {
                if (line.contains(keyword)) {
                    isAdTag = true;
                    break;
                }
            }
            if (isAdTag) {
                pendingInf = null;
                continue;
            }

            // --- D. 处理 URL 行 (TS 切片或二级 M3U8) ---
            if (line.startsWith("http") || line.contains(".ts") || line.contains(".m4s") || line.contains(".mp4") || line.contains(".m3u8")) {
                if (isAdUrl(line)) {
                    if (blockCount == 0) SpiderDebug.log("🛡️ M3U8 首次拦截: " + line);
                    blockCount++;
                    pendingInf = null;
                    pendingStreamInf = null;
                } else {
                    // 写入之前暂存的标签
                    if (pendingStreamInf != null) {
                        cleanContent.append(pendingStreamInf).append("\n");
                        pendingStreamInf = null;
                    }
                    if (pendingInf != null) {
                        cleanContent.append(pendingInf).append("\n");
                        pendingInf = null;
                    }
                    cleanContent.append(line).append("\n");
                }
                continue;
            }

            // --- E. 其它行直接保留 ---
            cleanContent.append(line).append("\n");
        }

        if (blockCount > 0) {
            SpiderDebug.log("🛡️ M3U8 清洗完成，共拦截广告项: " + blockCount);
        }

        return cleanContent.toString();
    }

    public static List<String> filterAdUrls(List<String> urls) {
        List<String> filtered = new ArrayList<>();
        if (urls == null) return filtered;
        for (String url : urls) {
            if (!isAdUrl(url)) filtered.add(url);
        }
        return filtered;
    }
}
