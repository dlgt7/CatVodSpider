package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;        // ← 正确包路径
import com.github.catvod.net.OkResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
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

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        Map<String, String> headers = getHeaders();
        String url = SEARCH_URL + "?kw=" + URLEncoder.encode(key, "UTF-8") + "&page=1&pagenum=30";

        String content = OkHttp.string(url, headers);  // 使用 com.github.catvod.net.OkHttp

        JSONObject json = new JSONObject(content);
        JSONArray list = new JSONArray();

        JSONArray items = json.optJSONArray("data");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);

                String title = item.optString("title");
                String vodId = item.optString("id");
                String pic = item.optString("cover");
                if (!pic.startsWith("http")) pic = "https:" + pic;
                String remark = item.optString("desc", item.optString("year", ""));

                JSONObject v = new JSONObject();
                v.put("vod_id", vodId);
                v.put("vod_name", title);
                v.put("vod_pic", pic);
                v.put("vod_remarks", remark);

                list.put(v);
            }
        }

        return Result.get().vod(list).string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        Map<String, String> headers = getHeaders();

        // 获取详情
        String detailUrl = DETAIL_URL + "?id=" + id;
        String detailContent = OkHttp.string(detailUrl, headers);
        JSONObject detailJson = new JSONObject(detailContent);
        JSONObject data = detailJson.optJSONObject("data");
        if (data == null) {
            return Result.error("无播放数据").string();
        }

        JSONObject playlinks = data.optJSONObject("playlinks");
        if (playlinks == null || playlinks.length() == 0) {
            return Result.error("无播放线路").string();
        }

        // 选择线路，优先 m3u8
        String playFlag = flag;
        if (playFlag.isEmpty() || !playlinks.has(playFlag)) {
            if (playlinks.has("m3u8")) playFlag = "m3u8";
            else if (playlinks.has("hd1")) playFlag = "hd1";
            else if (playlinks.has("hd2")) playFlag = "hd2";
            else playFlag = playlinks.keys().next();
        }

        // 默认第一集
        String nid = "1";
        if (id.contains("&nid=")) {
            nid = id.split("&nid=")[1];
        }

        // 播放请求
        JSONObject params = new JSONObject();
        params.put("id", data.optString("id"));
        params.put("flag", playFlag);
        params.put("nid", nid);

        OkResult playResult = OkHttp.post(PLAY_URL, params.toString(), headers);
        if (playResult.getCode() != 200) {
            return Result.error("播放请求失败").string();
        }

        JSONObject playJson = new JSONObject(playResult.getBody());
        String url = playJson.optString("real_url");
        if (url.isEmpty()) url = playJson.optString("url");

        if (url.isEmpty()) {
            return Result.error("获取播放地址失败").string();
        }

        return Result.get()
                .url(url)
                .header(headers)
                .parse(0)
                .string();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        // 360 主靠搜索，可返回空
        return Result.get().vod(new JSONArray()).string();
    }
}
