package com.github.catvod.utils;

import android.util.LruCache;
import com.github.catvod.bean.Danmaku;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.*;
import java.util.regex.Pattern;

public class DanmakuUtil {
    // 清理正则：新增对标点符号的清理，用于生成更稳健的缓存Key
    private static final Pattern CLEAN_P = Pattern.compile("(?i)(1080P|60帧|蓝光|BD|HD|修正|[\\s\\p{Punct}？。，！：])");
    private static final LruCache<String, String> SEARCH_CACHE = new LruCache<>(100);

    public static void appendBili(String title, long duration, List<Danmaku> list) {
        if (title == null || title.isEmpty()) return;
        
        // 1. 生成归一化缓存Key (忽略空格标点)
        String keyword = CLEAN_P.matcher(title).replaceAll("").toLowerCase();
        String cacheKey = keyword + "_" + (duration / 1000); // 秒级缓存
        String cachedCid = SEARCH_CACHE.get(cacheKey);
        
        if (cachedCid != null) {
            list.add(Danmaku.bilibili(cachedCid).name("B站(Cache)"));
            return;
        }

        try {
            String searchUrl = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" + OkHttp.encode(keyword);
            OkResult searchRes = OkHttp.get(searchUrl, getBiliHeaders());
            JsonObject json = JsonParser.parseString(searchRes.getBody()).getAsJsonObject();
            if (json.get("code").getAsInt() != 0) return;

            JsonArray results = json.getAsJsonObject("data").getAsJsonArray("result");
            if (results == null || results.size() == 0) return;

            // 2. 动态搜索深度：初始5条，若匹配度极低则扩充至10条
            int searchLimit = 5;
            JsonObject bestMatch = null;
            double maxScore = 0;

            for (int i = 0; i < Math.min(results.size(), 10); i++) {
                if (i >= searchLimit && maxScore > 0.6) break; // 已找到较好结果，停止深度搜索
                
                JsonObject item = results.get(i).getAsJsonObject();
                String itemTitle = item.get("title").getAsString().replaceAll("<em.*?/em>", "");
                String itemDurationStr = item.get("duration").getAsString();
                
                // 使用 Jaro-Winkler 算法计算标题相似度
                double simScore = jaroWinklerScore(keyword, CLEAN_P.matcher(itemTitle).replaceAll("").toLowerCase());
                
                // 时长校验 (阈值120秒)
                if (duration > 0) {
                    long itemSec = parseDuration(itemDurationStr);
                    long targetSec = duration / 1000;
                    if (Math.abs(itemSec - targetSec) <= 120) {
                        simScore += 0.3; // 时长匹配加分
                    } else if (Math.abs(itemSec - targetSec) > 300) {
                        simScore -= 0.4; // 时长差距过大减分
                    }
                }

                if (simScore > maxScore) {
                    maxScore = simScore;
                    bestMatch = item;
                }
            }

            if (bestMatch != null && maxScore > 0.5) {
                String bvid = bestMatch.get("bvid").getAsString();
                OkResult cidRes = OkHttp.get("https://api.bilibili.com/x/player/pagelist?bvid=" + bvid, getBiliHeaders());
                JsonObject cidJson = JsonParser.parseString(cidRes.getBody()).getAsJsonObject();
                if (cidJson.get("code").getAsInt() == 0) {
                    String cid = cidJson.getAsJsonArray("data").get(0).getAsJsonObject().get("cid").getAsString();
                    SEARCH_CACHE.put(cacheKey, cid);
                    list.add(Danmaku.bilibili(cid).name("B站:" + bestMatch.get("title").getAsString().replaceAll("<em.*?/em>", "")));
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("Danmaku Error: " + title + " -> " + e.getMessage());
        }
    }

    /**
     * 宽口径时长解析
     */
    private static long parseDuration(String dur) {
        if (dur == null || dur.isEmpty()) return 0;
        try {
            if (dur.contains(":")) {
                String[] parts = dur.split(":");
                if (parts.length == 2) return Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]);
                if (parts.length == 3) return Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
            }
            // 处理 "120" (纯秒) 或 "2分30秒"
            return Long.parseLong(dur.replaceAll("[^0-9]", ""));
        } catch (Exception e) { return 0; }
    }

    /**
     * Jaro-Winkler 相似度算法实现 (针对短文本/标题优化)
     */
    private static double jaroWinklerScore(String s1, String s2) {
        double jaro = jaroDistance(s1, s2);
        if (jaro < 0.7) return jaro;
        int prefix = 0; // 共同前缀长度，最大为4
        for (int i = 0; i < Math.min(4, Math.min(s1.length(), s2.length())); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++; else break;
        }
        return jaro + (prefix * 0.1 * (1.0 - jaro));
    }

    private static double jaroDistance(String s1, String s2) {
        int s1Len = s1.length(), s2Len = s2.length();
        if (s1Len == 0 && s2Len == 0) return 1.0;
        int matchDist = Math.max(0, Math.max(s1Len, s2Len) / 2 - 1);
        boolean[] s1Matches = new boolean[s1Len], s2Matches = new boolean[s2Len];
        int matches = 0;
        for (int i = 0; i < s1Len; i++) {
            int start = Math.max(0, i - matchDist), end = Math.min(i + matchDist + 1, s2Len);
            for (int j = start; j < end; j++) {
                if (!s2Matches[j] && s1.charAt(i) == s2.charAt(j)) {
                    s1Matches[i] = s2Matches[j] = true;
                    matches++; break;
                }
            }
        }
        if (matches == 0) return 0.0;
        double t = 0; int k = 0;
        for (int i = 0; i < s1Len; i++) {
            if (s1Matches[i]) {
                while (!s2Matches[k]) k++;
                if (s1.charAt(i) != s2.charAt(k)) t += 0.5;
                k++;
            }
        }
        return (matches / (double) s1Len + matches / (double) s2Len + (matches - t) / matches) / 3.0;
    }

    private static Map<String, String> getBiliHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("Referer", "https://www.bilibili.com/");
        h.put("User-Agent", "Mozilla/5.0");
        return h;
    }

    @SafeVarargs
    public static List<Danmaku> merge(List<Danmaku>... lists) {
        Map<String, Danmaku> map = new LinkedHashMap<>();
        for (List<Danmaku> l : lists) {
            if (l == null) continue;
            for (Danmaku d : l) {
                if (d.isValid() && !map.containsKey(d.getUrl())) map.put(d.getUrl(), d);
            }
        }
        List<Danmaku> res = new ArrayList<>(map.values());
        Collections.sort(res, (a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return res;
    }
}
