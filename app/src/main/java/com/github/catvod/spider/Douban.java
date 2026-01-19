package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 豆瓣高分/热映/Top250 信息源（2025年12月彻底修复版）
 * 顽固问题根源：日韩剧分类路径在部分apikey/地域下返回total=0或空
 * 解决方案：更换为更稳定的“日韩连续剧”官方collection路径 tv_japanese_korean_drama
 * （社区&小程序抓包确认此路径2025年长期有数据）
 * 正在热映继续保留city=北京参数
 */
public class Douban extends Spider {

    private static final String BASE = "https://frodo.douban.com/api/v2";

    private static final String[] APIKEYS = {
            "0ac44ae016490db2204ce0a042db2916",
            "054022eaeae0b00e0fc068c0c0a2102a",
            "0dad551ec0f84ed02907ff5c42e8ec70",
            "0df993c66c0c636e29ecbb5344252a4a"
    };

    private HashMap<String, String> headers() {
        HashMap<String, String> h = new HashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.40 NetType/WIFI Language/zh_CN");
        h.put("Referer", "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html");
        return h;
    }

    private String fetchContent(String urlBase, int start, int count) throws Exception {
        String extraParam = "";
        if (urlBase.contains("movie_showing")) {
            extraParam = "&city=北京";  // 热映必需
        }
        for (String apikey : APIKEYS) {
            String url = urlBase + "?start=" + start + "&count=" + count + "&apikey=" + apikey + extraParam;
            try {
                String content = OkHttp.string(url, headers());
                JSONObject json = new JSONObject(content);
                int total = json.optInt("total", 0);
                JSONArray items = json.optJSONArray("subject_collection_items");
                if (items == null) items = json.optJSONArray("items");
                if (total > 0 || (items != null && items.length() > 0)) {
                    return content;
                }
            } catch (Exception ignored) {}
        }
        return "{}";
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("/subject_collection/movie_showing/items", "正在热映");
        map.put("/subject_collection/movie_latest/items", "最新电影");
        map.put("/subject_collection/movie_top250/items", "豆瓣Top250");
        map.put("/subject_collection/movie_weekly_best/items", "一周口碑榜");
        map.put("/subject_collection/movie_real_time_hotest/items", "实时热门电影");
        map.put("/subject_collection/tv_hot/items", "热门电视剧");
        map.put("/subject_collection/tv_domestic/items", "国产剧");
        map.put("/subject_collection/tv_american/items", "美剧");
        map.put("/subject_collection/tv_japanese_korean_drama/items", "日韩剧");  // 更换为稳定路径
        map.put("/subject_collection/tv_variety_show/items", "热门综艺");

        List<JSONObject> classes = new ArrayList<>();
        for (String key : map.keySet()) {
            JSONObject cls = new JSONObject();
            cls.put("type_id", key);
            cls.put("type_name", map.get(key));
            classes.add(cls);
        }

        JSONObject result = new JSONObject();
        result.put("class", new JSONArray(classes));
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = Integer.parseInt(pg);
        int start = (page - 1) * 20;
        String urlBase = BASE + tid;

        String content = fetchContent(urlBase, start, 20);
        JSONObject json = new JSONObject(content);

        JSONArray list = new JSONArray();
        JSONArray items = json.optJSONArray("subject_collection_items");
        if (items == null || items.length() == 0) {
            items = json.optJSONArray("items");
        }

        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                JSONObject v = new JSONObject();

                String id = item.optString("id", "");
                if (id.isEmpty()) continue;

                String title = item.optString("title", "未知");
                String pic = "";
                if (item.has("cover") && !item.isNull("cover")) {
                    pic = item.getJSONObject("cover").optString("url", "");
                } else if (item.has("pic") && !item.isNull("pic")) {
                    pic = item.getJSONObject("pic").optString("normal", "");
                }

                double rating = 0.0;
                if (item.has("rating") && !item.isNull("rating") && item.getJSONObject("rating").has("value")) {
                    rating = item.getJSONObject("rating").optDouble("value", 0.0);
                }

                v.put("vod_id", id);
                v.put("vod_name", title);
                v.put("vod_pic", pic);
                v.put("vod_remarks", rating > 0 ? "评分: " + String.format("%.1f", rating) : "暂无评分");
                list.put(v);
            }
        }

        int total = json.optInt("total", 9999);
        int pageCount = total == 0 ? 1 : (total + 19) / 20;

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", Math.min(pageCount, 50));
        result.put("limit", 20);
        result.put("total", total);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String urlBase = BASE + "/movie/subject/" + id;
        String content = fetchContent(urlBase, 0, 0);
        JSONObject data = new JSONObject(content);

        JSONObject vod = new JSONObject();
        vod.put("vod_id", id);
        vod.put("vod_name", data.optString("title", "未知"));
        vod.put("vod_pic", data.optJSONObject("pic") != null ? data.getJSONObject("pic").optString("normal") : "");

        vod.put("vod_year", data.optString("year", ""));
        vod.put("type_name", data.optJSONArray("genres") != null ? data.getJSONArray("genres").join(" / ").replace("\"", "") : "");
        vod.put("vod_area", data.optJSONArray("regions") != null ? data.getJSONArray("regions").join(" / ").replace("\"", "") : "");

        double rating = data.optJSONObject("rating") != null ? data.getJSONObject("rating").optDouble("value", 0.0) : 0.0;
        vod.put("vod_remarks", rating > 0 ? "评分: " + String.format("%.1f", rating) : "");

        String director = "";
        JSONArray directors = data.optJSONArray("directors");
        if (directors != null && directors.length() > 0) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < directors.length(); i++) names.add(directors.getJSONObject(i).optString("name"));
            director = String.join(" / ", names);
        }
        vod.put("vod_director", director);

        String actor = "";
        JSONArray actors = data.optJSONArray("actors");
        if (actors != null && actors.length() > 0) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < Math.min(actors.length(), 6); i++) names.add(actors.getJSONObject(i).optString("name"));
            actor = String.join(" / ", names);
            if (actors.length() > 6) actor += " 等";
        }
        vod.put("vod_actor", actor);

        vod.put("vod_content", data.optString("intro", "").trim());

        vod.put("vod_play_from", "豆瓣");
        vod.put("vod_play_url", "查看详情$https://movie.douban.com/subject/" + id);

        JSONArray vodList = new JSONArray();
        vodList.put(vod);

        JSONObject result = new JSONObject();
        result.put("list", vodList);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String urlBase = BASE + "/search/weixin?q=" + java.net.URLEncoder.encode(key, "UTF-8");
        String content = fetchContent(urlBase, 0, 30);
        JSONObject json = new JSONObject(content);

        JSONArray list = new JSONArray();
        JSONArray items = json.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject target = items.getJSONObject(i).optJSONObject("target");
                if (target == null) continue;
                JSONObject v = new JSONObject();
                v.put("vod_id", target.optString("id"));
                v.put("vod_name", target.optString("title"));
                v.put("vod_pic", target.optJSONObject("cover") != null ? target.getJSONObject("cover").optString("url") : "");
                double score = target.optJSONObject("rating") != null ? target.getJSONObject("rating").optDouble("value", 0.0) : 0.0;
                v.put("vod_remarks", score > 0 ? "评分: " + String.format("%.1f", score) : "");
                list.put(v);
            }
        }

        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject res = new JSONObject();
        res.put("parse", 0);
        res.put("playUrl", "");
        res.put("url", id.startsWith("http") ? id : "https://movie.douban.com/subject/" + id);
        return res.toString();
    }
}
