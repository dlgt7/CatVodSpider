package com.github.catvod.utils;

import com.github.catvod.bean.Danmaku;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 弹幕工具类 - 负责多源聚合与自动化匹配
 */
public class DanmakuUtil {

    private static final Pattern NOISE = Pattern.compile("(?i)(1080P|60帧|蓝光|BD|HD|修正|\\s)");

    /**
     * 合并多个弹幕源并根据优先级排序和去重
     */
    public static List<Danmaku> merge(List<Danmaku>... lists) {
        Map<String, Danmaku> map = new LinkedHashMap<>();
        for (List<Danmaku> list : lists) {
            if (list == null) continue;
            for (Danmaku d : list) {
                if (!d.isValid()) continue;
                String key = d.getUrl();
                // 相同URL保留优先级高的
                if (!map.containsKey(key) || d.getPriority() > map.get(key).getPriority()) {
                    map.put(key, d);
                }
            }
        }
        List<Danmaku> result = new ArrayList<>(map.values());
        Collections.sort(result, (a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return result;
    }

    /**
     * 自动化集成：根据标题获取 B 站弹幕并加入列表
     */
    public static void appendBili(String title, List<Danmaku> list) {
        try {
            String cleanTitle = NOISE.matcher(title).replaceAll("");
            String searchUrl = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" + OkHttp.encode(cleanTitle);
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", "https://www.bilibili.com/");
            
            OkResult searchRes = OkHttp.get(searchUrl, headers);
            JsonObject data = JsonParser.parseString(searchRes.getBody()).getAsJsonObject().getAsJsonObject("data");
            
            if (data != null && data.has("result")) {
                JsonArray results = data.getAsJsonArray("result");
                if (results.size() > 0) {
                    // 取搜索结果第一位的 bvid
                    String bvid = results.get(0).getAsJsonObject().get("bvid").getAsString();
                    
                    // 获取 CID
                    OkResult cidRes = OkHttp.get("https://api.bilibili.com/x/player/pagelist?bvid=" + bvid, headers);
                    JsonArray pages = JsonParser.parseString(cidRes.getBody()).getAsJsonObject().getAsJsonArray("data");
                    if (pages != null && pages.size() > 0) {
                        String cid = pages.get(0).getAsJsonObject().get("cid").getAsString();
                        list.add(Danmaku.bilibili(cid));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * 辅助方法：快速过滤无效弹幕
     */
    public static List<Danmaku> filter(List<Danmaku> list) {
        List<Danmaku> valid = new ArrayList<>();
        if (list == null) return valid;
        for (Danmaku d : list) {
            if (d.isValid()) valid.add(d);
        }
        return valid;
    }
}
