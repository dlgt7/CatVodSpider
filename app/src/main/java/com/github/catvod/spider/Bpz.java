package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Bpz extends Spider {

    private static final String HOST = "https://www.qyzf88.com";
    private static final String API_URL = HOST + "/api.php/provide/vod/at/json/";

    /**
     * 通用请求头
     */
    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", HOST + "/");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        try {
            String content = OkHttp.string(API_URL, getHeaders());
            JSONObject data = new JSONObject(content);

            JSONArray classes = data.getJSONArray("class");

            JSONObject result = new JSONObject();
            result.put("class", classes);

            if (filter) {
                result.put("filters", new JSONObject());
            }

            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "{\"class\":[]}";
        }
    }

    @Override
    public String homeVideoContent() throws Exception {
        return categoryContent("", "1", false, null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            StringBuilder url = new StringBuilder(API_URL);
            url.append("?ac=videolist");

            if (!tid.isEmpty()) url.append("&t=").append(tid);
            url.append("&pg=").append(pg.isEmpty() ? "1" : pg);

            String content = OkHttp.string(url.toString(), getHeaders());
            return parseListResponse(content);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "{\"list\":[],\"page\":1,\"pagecount\":1,\"limit\":20,\"total\":0}";
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String id = ids.get(0);
            String url = API_URL + "?ac=detail&ids=" + id;

            String content = OkHttp.string(url, getHeaders());
            JSONObject data = new JSONObject(content);
            JSONArray list = data.getJSONArray("list");
            if (list.length() == 0) return "{\"list\":[]}";

            JSONObject vod = list.getJSONObject(0);

            JSONObject v = new JSONObject();
            v.put("vod_id", vod.getString("vod_id"));
            v.put("vod_name", vod.getString("vod_name"));
            v.put("vod_pic", fixUrl(vod.optString("vod_pic")));
            v.put("type_name", vod.optString("type_name"));
            v.put("vod_year", vod.optString("vod_year"));
            v.put("vod_area", vod.optString("vod_area"));
            v.put("vod_remarks", vod.optString("vod_remarks"));
            v.put("vod_actor", vod.optString("vod_actor"));
            v.put("vod_director", vod.optString("vod_director"));
            v.put("vod_content", vod.optString("vod_content").replaceAll("<[^>]*>", "").trim());

            // 保留原始 $$$ 分隔符，播放器必须识别
            v.put("vod_play_from", vod.getString("vod_play_from"));
            v.put("vod_play_url", vod.getString("vod_play_url"));

            JSONArray array = new JSONArray();
            array.put(v);

            JSONObject result = new JSONObject();
            result.put("list", array);
            return result.toString();

        } catch (Exception e) {
            SpiderDebug.log(e);
            return "{\"list\":[]}";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        try {
            String wd = URLEncoder.encode(key, "UTF-8");
            String url = API_URL + "?ac=videolist&wd=" + wd + "&pg=" + (pg.isEmpty() ? "1" : pg);

            String content = OkHttp.string(url, getHeaders());
            return parseListResponse(content);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "{\"list\":[]}";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            JSONObject result = new JSONObject();

            // 优先使用本地代理（防盗链更彻底）
            // 如果是视频直链，播放器会自动嗅探，这里我们也提供 header 备用
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", id);

            JSONObject header = new JSONObject();
            header.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            header.put("Referer", HOST + "/");
            header.put("Origin", HOST);
            result.put("header", header.toString());

            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "{\"parse\":0,\"url\":\"" + id + "\"}";
        }
    }

    /**
     * 【新增】嗅探支持：识别常见视频格式
     */
    @Override
    public boolean isVideoFormat(String url) throws Exception {
        if (url == null) return false;
        url = url.toLowerCase();
        return url.contains(".m3u8") || url.contains(".mp4") || url.contains(".flv") ||
               url.contains(".avi") || url.contains(".mkv") || url.contains(".mov") ||
               url.contains(".wmv") || url.contains("video/");
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        return true; // 开启手动嗅探检查（配合 isVideoFormat 使用）
    }

    /**
     * 【新增】本地代理播放（解决防盗链）
     * 支持 mp4 / m3u8 / flv
     */
    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        String type = params.get("type");
        if (type == null || type.isEmpty()) type = "video";

        // 只代理视频类型
        if ("video".equals(type)) {
            String url = params.get("url");
            if (url == null || url.isEmpty()) return null;

            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4") || lowerUrl.contains(".flv")) {

                Map<String, String> headers = getHeaders();

                // 返回格式：{ 代理后的本地路径, 原始类型, headers }
                return new Object[]{
                        url,                    // 原始链接（代理服务器会转发）
                        "video/*",              // MIME 类型
                        headers                 // 必要请求头
                };
            }
        }
        return null; // 不支持的类型不代理
    }

    /**
     * 统一解析列表（分类、搜索、首页）
     */
    private String parseListResponse(String content) throws JSONException {
        JSONObject data = new JSONObject(content);
        JSONArray list = data.getJSONArray("list");

        JSONArray videos = new JSONArray();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);

            JSONObject v = new JSONObject();
            v.put("vod_id", item.getString("vod_id"));
            v.put("vod_name", item.getString("vod_name"));
            v.put("vod_pic", fixUrl(item.optString("vod_pic")));
            v.put("vod_remarks", item.optString("vod_remarks", ""));
            videos.put(v);
        }

        JSONObject result = new JSONObject();
        result.put("list", videos);
        result.put("page", data.optInt("page", 1));
        result.put("pagecount", data.optInt("pagecount", 1));
        result.put("limit", data.optInt("limit", 20));
        result.put("total", data.optInt("total", 0));

        return result.toString();
    }

    /**
     * 补全图片/链接路径
     */
    private String fixUrl(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        url = url.trim();
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return HOST + url;
        if (!url.startsWith("http")) return HOST + "/" + url;
        return url;
    }
}
