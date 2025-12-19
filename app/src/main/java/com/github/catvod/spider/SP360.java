package com.fongmi.android.tv.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SP360 extends Spider {

    private static final String SITE_URL = "https://www.360kan.com";
    private static final String SEARCH_URL = "https://api.so.360kan.com/search";
    private static final String DETAIL_URL = "https://api.web.360kan.com/v1/vod/detail";
    private static final String PLAY_URL = "https://api.web.360kan.com/v1/vod/play";

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36");
        headers.put("Referer", SITE_URL + "/");
        headers.put("Origin", SITE_URL);
        return headers;
    }

    // 搜索（当前360主要靠搜索）
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        Map<String, String> headers = getHeaders();

        String url = SEARCH_URL + "?kw=" + URLEncoder.encode(key, "UTF-8") + "&page=1&pagenum=24";

        String content = OkHttp.get(url, headers);

        JSONObject data = new JSONObject(content);
        JSONArray list = new JSONArray();

        JSONArray items = data.optJSONArray("data");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);

                String title = item.optString("title");
                String vodId = item.optString("id");
                String pic = item.optString("cover"); // 或 "pic"
                String remark = item.optString("desc"); // 年份或更新状态

                JSONObject v = new JSONObject();
                v.put("vod_id", vodId);
                v.put("vod_name", title);
                v.put("vod_pic", pic.startsWith("http") ? pic : "https:" + pic);
                v.put("vod_remarks", remark);

                list.put(v);
            }
        }

        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    // 播放内容（flag + id 格式，如 "m3u8$xxx" 或直接 id + 线路参数）
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        Map<String, String> headers = getHeaders();

        // 先获取详情，拿到 playlinks
        String detailUrl = DETAIL_URL + "?id=" + id;
        String detailContent = OkHttp.get(detailUrl, headers);
        JSONObject detailJson = new JSONObject(detailContent);
        JSONObject detailData = detailJson.optJSONObject("data");

        if (detailData == null) {
            return failResult();
        }

        // 解析线路（playlinks 里是各线路的链接列表）
        JSONObject playlinks = detailData.optJSONObject("playlinks");

        // flag 通常是线路名，如 "hd1"、"hd2"、"m3u8" 等，社区常用默认取第一个可用
        String playFlag = flag.isEmpty() ? getFirstAvailableFlag(playlinks) : flag;

        JSONArray links = playlinks.optJSONArray(playFlag);
        if (links == null || links.length() == 0) {
            return failResult();
        }

        // 取第一个播放源（通常是集数索引或直接链接）
        // 对于剧集，id 会是 "id=xxx&nid=1" 格式，这里简化取默认第一集
        String playId = "1"; // 默认第一集，可扩展为多集选择
        if (id.contains("&nid=")) {
            playId = id.split("&nid=")[1];
        }

        // 构建播放请求参数
        JSONObject params = new JSONObject();
        params.put("id", detailData.optString("id"));
        params.put("flag", playFlag);
        params.put("nid", playId);

        String playContent = OkHttp.post(PLAY_URL, params.toString(), headers);
        JSONObject playJson = new JSONObject(playContent);

        String url = playJson.optString("url");
        String realUrl = playJson.optString("real_url"); // 有时有 real_url

        if ((url == null || url.isEmpty()) && (realUrl == null || realUrl.isEmpty())) {
            return failResult();
        }

        String finalUrl = realUrl != null && !realUrl.isEmpty() ? realUrl : url;

        JSONObject result = new JSONObject();
        result.put("parse", 0); // 直链，大多是 m3u8/mp4
        result.put("playUrl", "");
        result.put("url", finalUrl);
        result.put("header", new JSONObject(headers).toString()); // 可选加 header

        return result.toString();
    }

    private String getFirstAvailableFlag(JSONObject playlinks) {
        // 默认优先 m3u8 或 hd1 等
        if (playlinks.has("m3u8")) return "m3u8";
        if (playlinks.has("hd1")) return "hd1";
        if (playlinks.has("hd2")) return "hd2";
        return playlinks.keys().next(); // 第一个
    }

    private String failResult() {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("url", "");
        result.put("jx", "0");
        result.put("msg", "暂无播放源或解析失败");
        return result.toString();
    }

    // 可选：首页（如果需要）
    @Override
    public String homeContent(boolean filter) throws Exception {
        // 360 主靠搜索，首页可返回空或固定推荐
        JSONObject result = new JSONObject();
        result.put("list", new JSONArray());
        return result.toString();
    }
}
