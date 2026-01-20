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
    // 归一化正则：剔除标点和杂质，用于精准Key生成和标题对比
    private static final Pattern NORMALIZE_P = Pattern.compile("(?i)(1080P|60帧|蓝光|BD|HD|修正|[\\s\\p{Punct}？。，！：])");
    private static final LruCache<String, String> SEARCH_CACHE = new LruCache<>(100);

    public static void appendBili(String title, long duration, List<Danmaku> list) {
        if (title == null || title.isEmpty()) return;
        
        // 1. 生成归一化缓存Key (基于清洗后的关键词+秒级时长)
        String keyword = NORMALIZE_P.matcher(title).replaceAll("").toLowerCase();
        String cacheKey = keyword + "_" + (duration / 1000);
        String cachedCid = SEARCH_CACHE.get(cacheKey);
        
        if (cachedCid != null) {
            list.add(Danmaku.createBilibiliDanmaku(cachedCid).name("B站(Cache)"));
            return;
        }

        try {
            String searchUrl = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" + OkHttp.urlEncode(keyword);
            OkResult searchRes = OkHttp.get(searchUrl, getBiliHeaders());
            JsonObject json = JsonParser.parseString(searchRes.getBody()).getAsJsonObject();
            if (json.get("code").getAsInt() != 0) return;

            JsonArray results = json.getAsJsonObject("data").getAsJsonArray("result");
            if (results == null || results.size() == 0) return;

            // 2. 智能筛选：结合 Jaro-Winkler 相似度与时长差异
            JsonObject bestMatch = null;
            double maxScore = 0;

            // 动态搜索深度：优先匹配前5，若无高分则下探至前10
            for (int i = 0; i < Math.min(results.size(), 10); i++) {
                if (i >= 5 && maxScore > 0.7) break; 

                JsonObject item = results.get(i).getAsJsonObject();
                String itemTitle = item.get("title").getAsString().replaceAll("<em.*?/em>", "");
                String itemDurationStr = item.get("duration").getAsString();
                
                // 相似度计算
                double simScore = jaroWinklerScore(keyword, NORMALIZE_P.matcher(itemTitle).replaceAll("").toLowerCase());
                
                // 时长加成 (阈值120秒)
                if (duration > 0) {
                    long itemSec = parseDuration(itemDurationStr);
                    long targetSec = duration / 1000;
                    if (Math.abs(itemSec - targetSec) <= 120) simScore += 0.25; 
                    else if (Math.abs(itemSec - targetSec) > 300) simScore -= 0.3;
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
                    list.add(Danmaku.createBilibiliDanmaku(cid).name("B站:" + bestMatch.get("title").getAsString().replaceAll("<em.*?/em>", "")));
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("Danmaku Error: " + title + " -> " + e.getMessage());
        }
    }

    /**
     * 宽口径时长解析 (支持 MM:SS, HH:MM:SS, 纯秒, 中文描述)
     */
    private static long parseDuration(String dur) {
        if (dur == null || dur.isEmpty()) return 0;
        try {
            if (dur.contains(":")) {
                String[] p = dur.split(":");
                if (p.length == 2) return Long.parseLong(p[0]) * 60 + Long.parseLong(p[1]);
                if (p.length == 3) return Long.parseLong(p[0]) * 3600 + Long.parseLong(p[1]) * 60 + Long.parseLong(p[2]);
            }
            return Long.parseLong(dur.replaceAll("[^0-9]", ""));
        } catch (Exception e) { return 0; }
    }

    /**
     * Jaro-Winkler 相似度实现 (针对短标题优化)
     */
    private static double jaroWinklerScore(String s1, String s2) {
        double jaro = jaroDistance(s1, s2);
        if (jaro < 0.7) return jaro;
        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(s1.length(), s2.length())); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++; else break;
        }
        return jaro + (prefix * 0.1 * (1.0 - jaro));
    }

    private static double jaroDistance(String s1, String s2) {
        int s1L = s1.length(), s2L = s2.length();
        if (s1L == 0 || s2L == 0) return 0.0;
        int matchDist = Math.max(0, Math.max(s1L, s2L) / 2 - 1);
        boolean[] s1M = new boolean[s1L], s2M = new boolean[s2L];
        int matches = 0;
        for (int i = 0; i < s1L; i++) {
            int start = Math.max(0, i - matchDist), end = Math.min(i + matchDist + 1, s2L);
            for (int j = start; j < end; j++) {
                if (!s2M[j] && s1.charAt(i) == s2.charAt(j)) {
                    s1M[i] = s2M[j] = true; matches++; break;
                }
            }
        }
        if (matches == 0) return 0.0;
        double t = 0; int k = 0;
        for (int i = 0; i < s1L; i++) {
            if (s1M[i]) {
                while (!s2M[k]) k++;
                if (s1.charAt(i) != s2.charAt(k)) t += 0.5;
                k++;
            }
        }
        return (matches / (double) s1L + matches / (double) s2L + (matches - t) / matches) / 3.0;
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