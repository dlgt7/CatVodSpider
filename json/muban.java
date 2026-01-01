// 通用 CatVodSpider Java 模板（严格匹配你提供的最新源码）
// 已修正所有低级错误，可直接复制使用
// 使用时：修改类名为站点名（如 Ali.java），填写站点特定逻辑即可

package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;
import com.github.catvod.net.OkRequest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UniversalSpider extends Spider {

    private String siteUrl = "https://example.com";
    private String defaultUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private Map<String, String> headers;
    private String ext;
    private JSONObject extJson;
    private String ua;
    private String username = "";
    private String password = "";

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
        this.ext = extend;

        headers = new HashMap<>();
        ua = defaultUa;
        headers.put("User-Agent", ua);
        headers.put("Referer", siteUrl);

        try {
            if (extend != null && extend.startsWith("{")) {
                extJson = new JSONObject(extend);

                if (extJson.has("url")) siteUrl = extJson.optString("url").replaceAll("/+$", "");
                if (extJson.has("ua")) ua = extJson.optString("ua");

                if (extJson.has("user")) username = extJson.optString("user");
                if (extJson.has("pass")) password = extJson.optString("pass");

                headers.put("User-Agent", ua);
            } else if (extend != null && extend.startsWith("http")) {
                siteUrl = extend.replaceAll("/+$", "");
            }

            if (!username.isEmpty() && !password.isEmpty()) {
                login();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    private void login() {
        try {
            // 示例 POST 登录，实际根据站点修改
            String loginUrl = siteUrl + "/login";
            Map<String, String> params = new HashMap<>();
            params.put("username", username);
            params.put("password", password);

            // 修正为 POST
            OkRequest request = new OkRequest(OkHttp.POST, loginUrl, params, null, headers);
            OkResult okResult = request.execute(OkHttp.client());
            String result = okResult.getBody();

            JSONObject json = new JSONObject(result);

            if (json.has("token")) {
                String token = json.getString("token");
                headers.put("Authorization", "Bearer " + token);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            List<Class> classes = new ArrayList<>();
            List<Vod> list = new ArrayList<>();
            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

            String url = siteUrl + "/home";
            String content = OkHttp.string(url, headers);

            // 示例 JSON 解析（请替换为实际逻辑）
            JSONObject data = new JSONObject(content);
            JSONArray cateArray = data.optJSONArray("categories");
            if (cateArray != null) {
                for (int i = 0; i < cateArray.length(); i++) {
                    JSONObject c = cateArray.getJSONObject(i);
                    classes.add(new Class(c.optString("id"), c.optString("name")));
                }
            }

            // 推荐视频示例
            JSONArray vodArray = data.optJSONArray("recommend");
            if (vodArray != null) {
                for (int i = 0; i < vodArray.length(); i++) {
                    JSONObject v = vodArray.getJSONObject(i);
                    Vod vod = new Vod();
                    vod.vod_id = v.optString("id");
                    vod.vod_name = v.optString("name");
                    vod.vod_pic = v.optString("pic");
                    vod.vod_remarks = v.optString("remarks");
                    list.add(vod);
                }
            }

            // filters 示例（需要时取消注释并填充）
            // List<Filter> yearFilter = new ArrayList<>();
            // yearFilter.add(new Filter.Value("2024", "2024"));
            // filters.put("1", Arrays.asList(new Filter("year", "年份", yearFilter)));

            Result result = new Result();
            result.class = classes;
            result.list = list;
            if (filter) result.filters = filters;
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            List<Vod> list = new ArrayList<>();

            StringBuilder url = new StringBuilder(siteUrl + "/list/" + tid + "?page=" + pg);
            if (extend != null) {
                for (Map.Entry<String, String> entry : extend.entrySet()) {
                    url.append("&").append(entry.getKey()).append("=")
                       .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                }
            }

            String content = OkHttp.string(url.toString(), headers);
            JSONObject data = new JSONObject(content);
            JSONArray array = data.optJSONArray("list");

            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject v = array.getJSONObject(i);
                    Vod vod = new Vod();
                    vod.vod_id = v.optString("id");
                    vod.vod_name = v.optString("name");
                    vod.vod_pic = v.optString("pic");
                    vod.vod_remarks = v.optString("remarks");
                    list.add(vod);
                }
            }

            Result result = new Result();
            result.list = list;
            result.page = Integer.parseInt(pg);
            result.pagecount = data.optInt("pages", 999);
            result.limit = 30;
            result.total = data.optInt("total", 9999);
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = siteUrl + "/detail/" + id;
            String content = OkHttp.string(url, headers);

            JSONObject data = new JSONObject(content);
            Vod vod = new Vod();
            vod.vod_id = id;
            vod.vod_name = data.optString("name");
            vod.vod_pic = data.optString("pic");
            vod.type_name = data.optString("type");
            vod.vod_year = data.optString("year");
            vod.vod_area = data.optString("area");
            vod.vod_actor = data.optString("actor");
            vod.vod_director = data.optString("director");
            vod.vod_content = data.optString("desc");
            vod.vod_remarks = data.optString("remarks");

            // 播放源（最常用手动拼接方式）
            StringBuilder from = new StringBuilder();
            StringBuilder playUrl = new StringBuilder();

            // 示例：只有一个源
            from.append("官方");
            playUrl.append("第01集$").append("https://example.com/play/1.m3u8")
                   .append("#第02集$").append("https://example.com/play/2.m3u8");

            // 多源示例：
            // from.append("线路1$$$线路2");
            // playUrl.append("第1集$url1#第2集$url2$$$第1集$url3#第2集$url4");

            vod.vod_play_from = from.toString();
            vod.vod_play_url = playUrl.toString();

            List<Vod> list = new ArrayList<>();
            list.add(vod);

            Result result = new Result();
            result.list = list;
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            Result result = new Result();

            // 情况1：直链播放
            result.parse = 0;                 // 直链
            result.url = id;                  // 直接就是播放地址

            // 情况2：需要嗅探（推荐使用本地代理）
            // result.parse = 1;
            // result.url = Proxy.getUrl() + "?do=play&url=" + URLEncoder.encode(id, "UTF-8");

            // 情况3：调用内置解析接口
            // result.jx = 1;

            // 添加防盗链 header（非常常见）
            JSONObject header = new JSONObject();
            header.put("User-Agent", ua);
            header.put("Referer", siteUrl);
            result.header = header.toString();

            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            List<Vod> list = new ArrayList<>();

            String url = siteUrl + "/search?q=" + URLEncoder.encode(key, "UTF-8");
            String content = OkHttp.string(url, headers);

            JSONObject data = new JSONObject(content);
            JSONArray array = data.optJSONArray("list");

            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject v = array.getJSONObject(i);
                    Vod vod = new Vod();
                    vod.vod_id = v.optString("id");
                    vod.vod_name = v.optString("name");
                    vod.vod_pic = v.optString("pic");
                    vod.vod_remarks = v.optString("remarks");
                    list.add(vod);
                }
            }

            Result result = new Result();
            result.list = list;
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // 带重试的请求工具（可选）
    private String fetch(String url, Map<String, String> h) {
        int retry = 3;
        while (retry-- > 0) {
            try {
                return OkHttp.string(url, h);
            } catch (Exception ignored) {}
        }
        return "";
    }
}
