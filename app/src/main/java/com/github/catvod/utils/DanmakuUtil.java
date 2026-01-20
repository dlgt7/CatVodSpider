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
    
    // 清理标题中的杂质，提高匹配度
    private static final Pattern CLEAN_P = Pattern.compile("(?i)(1080P|60帧|蓝光|BD|HD|修正|\\s|第[\\d]+集)");
    
    // 内存缓存：Key = title + duration, 防止频繁调用搜索API
    private static final LruCache<String, String> SEARCH_CACHE = new LruCache<>(100);

    /**
     * 智能匹配并追加B站弹幕
     * @param title 视频标题
     * @param duration 视频时长(毫秒)，传入0则跳过时长校验
     * @param list 结果存放池
     */
    public static void appendBili(String title, long duration, List<Danmaku> list) {
        if (title == null || title.isEmpty()) return;

        String cacheKey = title + "_" + duration;
        String cachedCid = SEARCH_CACHE.get(cacheKey);
        if (cachedCid != null) {
            list.add(Danmaku.bilibili(cachedCid).name("B站(来自缓存)"));
            return;
        }

        try {
            String keyword = CLEAN_P.matcher(title).replaceAll("");
            String searchUrl = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" + OkHttp.encode(keyword);
            
            // 1. 发起搜索
            OkResult searchRes = OkHttp.get(searchUrl, getBiliHeaders());
            JsonObject json = JsonParser.parseString(searchRes.getBody()).getAsJsonObject();
            if (json.get("code").getAsInt() != 0) return;

            JsonArray results = json.getAsJsonObject("data").getAsJsonArray("result");
            if (results == null || results.size() == 0) return;

            // 2. 智能筛选：遍历搜索结果，结合标题相似度与时长差异
            JsonObject bestMatch = null;
            double maxScore = 0;

            for (int i = 0; i < Math.min(results.size(), 5); i++) {
                JsonObject item = results.get(i).getAsJsonObject();
                String itemTitle = item.get("title").getAsString().replaceAll("<em.*?/em>", "");
                String itemDurationStr = item.get("duration").getAsString(); // 格式 "MM:SS" 或 "HH:MM:SS"
                
                double simScore = calculateSimilarity(keyword, itemTitle);
                
                // 时长校验：如果时长相差超过2分钟，判定为不匹配(可能是短视频/切片)
                if (duration > 0) {
                    long itemSec = parseDuration(itemDurationStr);
                    long targetSec = duration / 1000;
                    if (Math.abs(itemSec - targetSec) > 120) {
                        simScore *= 0.2; // 严重降权
                    } else {
                        simScore *= 1.5; // 时长契合，提权
                    }
                }

                if (simScore > maxScore) {
                    maxScore = simScore;
                    bestMatch = item;
                }
            }

            // 3. 获取CID并存入列表
            if (bestMatch != null && maxScore > 0.4) {
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
            SpiderDebug.log("Danmaku Search Error: " + e.getMessage());
        }
    }

    /**
     * 智能去重并合并列表（源级合并）
     */
    @SafeVarargs
    public static List<Danmaku> merge(List<Danmaku>... lists) {
        Map<String, Danmaku> map = new LinkedHashMap<>();
        for (List<Danmaku> l : lists) {
            if (l == null) continue;
            for (Danmaku d : l) {
                if (!d.isValid()) continue;
                String key = d.getUrl();
                if (!map.containsKey(key) || d.getPriority() > map.get(key).getPriority()) {
                    map.put(key, d);
                }
            }
        }
        List<Danmaku> result = new ArrayList<>(map.values());
        Collections.sort(result, (a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return result;
    }

    // --- 内部算法 ---

    private static long parseDuration(String dur) {
        try {
            String[] parts = dur.split(":");
            if (parts.length == 2) return Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]);
            if (parts.length == 3) return Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
        } catch (Exception ignored) {}
        return 0;
    }

    private static double calculateSimilarity(String s1, String s2) {
        String t1 = s1.toLowerCase();
        String t2 = s2.toLowerCase();
        int hits = 0;
        for (char c : t1.toCharArray()) { if (t2.indexOf(c) != -1) hits++; }
        return (double) hits / Math.max(t1.length(), t2.length());
    }

    private static Map<String, String> getBiliHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("Referer", "https://www.bilibili.com/");
        h.put("User-Agent", "Mozilla/5.0");
        return h;
    }
}
