// 通用 CatVodSpider Java 模板（严格匹配你提供的最新源码）
// 已修正所有低级错误，可直接复制使用
// 使用时：修改类名为站点名（如 Ali.java），填写站点特定逻辑即可

package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.os.SystemClock;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Danmaku;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Misc;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UniversalSpider extends Spider {

    protected String siteUrl = "https://example.com"; // 站点URL，替换为实际站点
    protected String defaultUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"; // 默认User-Agent

    protected Map<String, String> headers; // 请求头
    protected String ext; // 扩展参数
    protected JSONObject extend; // 扩展JSON对象，用于存储配置
    protected JSONObject filterConfig; // 筛选配置
    protected JSONObject playerConfig; // 播放源配置
    protected JSONObject rule; // 规则配置，支持XPath或API

    @Override
    public void init(Context context, String ext) {
        super.init(context, ext);
        this.ext = ext;
        try {
            // 解析扩展参数，如果是JSON则转为JSONObject
            if (ext != null && !ext.isEmpty()) {
                if (ext.startsWith("http")) {
                    String json = fetch(ext, null);
                    extend = new JSONObject(json);
                } else {
                    extend = new JSONObject(ext);
                }
            }
            // 初始化请求头
            headers = new HashMap<>();
            headers.put("User-Agent", defaultUa);
            headers.put("Referer", siteUrl + "/");
            // 初始化筛选和播放配置（如果有）
            filterConfig = extend.optJSONObject("filters");
            playerConfig = extend.optJSONObject("players");
            rule = extend.optJSONObject("rule"); // 支持规则配置，如XPath
            // 如果有站点URL在ext中，覆盖默认
            if (extend.has("siteUrl")) {
                siteUrl = extend.optString("siteUrl");
            }
            // 处理siteUrl结尾斜杠
            if (!siteUrl.endsWith("/")) {
                siteUrl += "/";
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            List<com.github.catvod.bean.Class> classes = new ArrayList<>();
            List<Filter> filters = new ArrayList<>();
            // 示例：从首页HTML或API获取分类
            String homeUrl = siteUrl + (rule != null && rule.has("homeUrl") ? rule.optString("homeUrl") : "/");
            String content = fetch(homeUrl, headers);
            if (rule != null && rule.has("cateSelector")) {
                // 使用Jsoup解析
                Document doc = Jsoup.parse(content);
                Elements elements = doc.select(rule.optString("cateSelector"));
                for (Element ele : elements) {
                    String id = ele.attr(rule.optString("cateIdAttr", "href")).replaceAll(rule.optString("cateIdR", ""), "");
                    String name = ele.text();
                    com.github.catvod.bean.Class cls = new com.github.catvod.bean.Class(id, name);
                    classes.add(cls);

                    // 如果支持筛选，添加筛选选项
                    if (filter && filterConfig != null) {
                        JSONArray filterArray = filterConfig.optJSONArray(cls.getTypeId());
                        if (filterArray != null) {
                            for (int j = 0; j < filterArray.length(); j++) {
                                JSONObject fObj = filterArray.getJSONObject(j);
                                String key = fObj.optString("key");
                                String fname = fObj.optString("name");
                                JSONArray values = fObj.optJSONArray("values");
                                List<Filter.Value> drops = new ArrayList<>();
                                for (int k = 0; k < values.length(); k++) {
                                    JSONObject val = values.getJSONObject(k);
                                    drops.add(new Filter.Value(val.optString("n"), val.optString("v")));
                                }
                                filters.add(new Filter(key, fname, drops));
                            }
                        }
                    }
                }
            } else {
                // 假设API
                JSONObject data = new JSONObject(content);
                JSONArray array = data.optJSONArray("classes");
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        classes.add(new com.github.catvod.bean.Class(obj.optString("type_id"), obj.optString("type_name")));
                    }
                }
            }

            Result result = new Result();
            result.classes(classes);
            if (filter) {
                result.filters(filters);
            }
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String homeVideoContent() {
        try {
            List<Vod> list = new ArrayList<>();
            // 示例：从首页获取推荐视频
            String homeUrl = siteUrl + (rule != null && rule.has("homeUrl") ? rule.optString("homeUrl") : "/");
            String content = fetch(homeUrl, headers);
            if (rule != null && rule.has("recommendSelector")) {
                Document doc = Jsoup.parse(content);
                Elements elements = doc.select(rule.optString("recommendSelector"));
                for (Element ele : elements) {
                    String id = ele.select(rule.optString("vodIdSelector")).attr("href").replaceAll(rule.optString("vodIdR", ""), "");
                    String name = ele.select(rule.optString("vodNameSelector")).text();
                    String pic = fixPicUrl(ele.select(rule.optString("vodPicSelector")).attr("src"));
                    String remarks = ele.select(rule.optString("vodRemarksSelector")).text();
                    list.add(new Vod(id, name, pic, remarks));
                }
            } else {
                // 假设API
                JSONObject data = new JSONObject(content);
                JSONArray array = data.optJSONArray("videos");
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject v = array.getJSONObject(i);
                        String pic = fixPicUrl(v.optString("vod_pic"));
                        list.add(new Vod(v.optString("vod_id"), v.optString("vod_name"), pic, v.optString("vod_remarks")));
                    }
                }
            }

            Result result = new Result();
            result.list(list);
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
            // 构建分类URL，支持筛选
            String cateUrl = siteUrl + (rule != null && rule.has("cateUrl") ? rule.optString("cateUrl") : "/category/{cateId}/{catePg}");
            if (filter && extend != null && !extend.isEmpty()) {
                for (Map.Entry<String, String> entry : extend.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (value.length() > 0) {
                        cateUrl = cateUrl.replace("{" + key + "}", URLEncoder.encode(value, "UTF-8"));
                    }
                }
            }
            cateUrl = cateUrl.replace("{cateId}", tid).replace("{catePg}", pg);
            // 移除未替换的占位符
            Matcher m = Pattern.compile("\\{(.*?)\\}").matcher(cateUrl);
            while (m.find()) {
                String n = m.group(0).replace("{", "").replace("}", "");
                cateUrl = cateUrl.replace(m.group(0), "").replace("/" + n + "/", "");
            }
            String content = fetch(cateUrl, headers);
            if (rule != null && rule.has("listSelector")) {
                Document doc = Jsoup.parse(content);
                Elements elements = doc.select(rule.optString("listSelector"));
                for (Element ele : elements) {
                    String id = ele.select(rule.optString("vodIdSelector")).attr("href").replaceAll(rule.optString("vodIdR", ""), "");
                    String name = ele.select(rule.optString("vodNameSelector")).text();
                    String pic = fixPicUrl(ele.select(rule.optString("vodPicSelector")).attr("src"));
                    String remarks = ele.select(rule.optString("vodRemarksSelector")).text();
                    list.add(new Vod(id, name, pic, remarks));
                }
            } else {
                // 假设API
                JSONObject data = new JSONObject(content);
                JSONArray array = data.optJSONArray("list");
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject v = array.getJSONObject(i);
                        String pic = fixPicUrl(v.optString("vod_pic"));
                        list.add(new Vod(v.optString("vod_id"), v.optString("vod_name"), pic, v.optString("vod_remarks")));
                    }
                }
            }

            Result result = new Result();
            result.list(list);
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
            // 构建详情URL
            String detailUrl = siteUrl + (rule != null && rule.has("detailUrl") ? rule.optString("detailUrl").replace("{vid}", id) : "/detail/" + id);
            String content = fetch(detailUrl, headers);
            Document doc = Jsoup.parse(content);
            Vod vod = new Vod();
            vod.setVodId(id);
            if (rule != null && rule.has("detailNameSelector")) {
                vod.setVodName(doc.select(rule.optString("detailNameSelector")).text());
                vod.setVodPic(fixPicUrl(doc.select(rule.optString("detailPicSelector")).attr("src")));
                vod.setTypeName(doc.select(rule.optString("detailTypeSelector")).text());
                vod.setVodYear(doc.select(rule.optString("detailYearSelector")).text());
                vod.setVodArea(doc.select(rule.optString("detailAreaSelector")).text());
                vod.setVodRemarks(doc.select(rule.optString("detailRemarksSelector")).text());
                vod.setVodActor(doc.select(rule.optString("detailActorSelector")).text());
                vod.setVodDirector(doc.select(rule.optString("detailDirectorSelector")).text());
                vod.setVodContent(doc.select(rule.optString("detailContentSelector")).text());
            } else {
                // 假设API
                JSONObject data = new JSONObject(content).optJSONObject("detail");
                vod.setVodName(data.optString("vod_name"));
                vod.setVodPic(fixPicUrl(data.optString("vod_pic")));
                vod.setTypeName(data.optString("type_name"));
                vod.setVodYear(data.optString("vod_year"));
                vod.setVodArea(data.optString("vod_area"));
                vod.setVodRemarks(data.optString("vod_remarks"));
                vod.setVodActor(data.optString("vod_actor"));
                vod.setVodDirector(data.optString("vod_director"));
                vod.setVodContent(data.optString("vod_content"));
            }

            // 播放源
            Map<String, String> sites = new LinkedHashMap<>();
            if (rule != null && rule.has("playFromSelector")) {
                Elements sources = doc.select(rule.optString("playFromSelector"));
                Elements episodeGroups = doc.select(rule.optString("playUrlSelector"));
                int size = Math.min(sources.size(), episodeGroups.size());
                for (int i = 0; i < size; i++) {
                    Element source = sources.get(i);
                    if (source.attr("style").contains("display:none") || source.attr("class").contains("hidden")) continue; // 跳过隐藏线路
                    String siteName = source.text();
                    String playList = "";
                    List<String> urls = new ArrayList<>();
                    Elements eps = episodeGroups.get(i).select("a");
                    for (Element ep : eps) {
                        String epName = ep.text();
                        String epUrl = ep.attr("href");
                        urls.add(epName + "$" + epUrl);
                    }
                    if (!urls.isEmpty()) {
                        playList = TextUtils.join("#", urls);
                    }
                    sites.put(siteName, playList);
                }
            } else {
                // 假设API
                JSONArray sitesArray = new JSONObject(content).optJSONArray("play_sources");
                if (sitesArray != null) {
                    for (int i = 0; i < sitesArray.length(); i++) {
                        JSONObject site = sitesArray.getJSONObject(i);
                        String siteName = site.optString("name");
                        String playList = "";
                        List<String> urls = new ArrayList<>();
                        JSONArray playArray = site.optJSONArray("plays");
                        for (int j = 0; j < playArray.length(); j++) {
                            JSONObject play = playArray.getJSONObject(j);
                            urls.add(play.optString("title") + "$" + play.optString("url"));
                        }
                        if (!urls.isEmpty()) {
                            playList = TextUtils.join("#", urls);
                        }
                        sites.put(siteName, playList);
                    }
                }
            }
            if (!sites.isEmpty()) {
                vod.setVodPlayFrom(TextUtils.join("$$$", sites.keySet()));
                vod.setVodPlayUrl(TextUtils.join("$$$", sites.values()));
            }

            Result result = new Result();
            result.list(vod);
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            // 添加随机延迟以避免频率限制
            SystemClock.sleep(Misc.random(200, 500));
            List<Vod> list = new ArrayList<>();
            // 构建搜索URL
            String searchUrl = siteUrl + (rule != null && rule.has("searchUrl") ? rule.optString("searchUrl") : "/search?key=" + URLEncoder.encode(key, "UTF-8"));
            String content = fetch(searchUrl, headers);
            if (rule != null && rule.has("searchListSelector")) {
                Document doc = Jsoup.parse(content);
                Elements elements = doc.select(rule.optString("searchListSelector"));
                for (Element ele : elements) {
                    String id = ele.select(rule.optString("vodIdSelector")).attr("href").replaceAll(rule.optString("vodIdR", ""), "");
                    String name = ele.select(rule.optString("vodNameSelector")).text();
                    String pic = fixPicUrl(ele.select(rule.optString("vodPicSelector")).attr("src"));
                    String remarks = ele.select(rule.optString("vodRemarksSelector")).text();
                    list.add(new Vod(id, name, pic, remarks));
                }
            } else {
                // 假设API
                JSONObject data = new JSONObject(content);
                JSONArray array = data.optJSONArray("results");
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject v = array.getJSONObject(i);
                        String pic = fixPicUrl(v.optString("vod_pic"));
                        list.add(new Vod(v.optString("vod_id"), v.optString("vod_name"), pic, v.optString("vod_remarks")));
                    }
                }
            }

            Result result = new Result();
            result.list(list);
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // 构建播放URL
            if (Misc.isVip(id) || Misc.isVideoFormat(id)) {
                // 直接播放
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("playUrl", "");
                result.put("url", id);
                // 添加弹幕支持
                addDanmaku(result, id);
                return result.toString();
            }
            String playUrl = siteUrl + (rule != null && rule.has("playUrl") ? rule.optString("playUrl").replace("{playUrl}", id) : id);
            String content = fetch(playUrl, headers);
            // 处理可能的转义
            content = content.replace("\\", "");
            String parseUrl = "";
            String regexStr = rule != null && rule.has("playRegex") ? rule.optString("playRegex") : "(http\\S+?m3u8)";
            Pattern regex = Pattern.compile(regexStr);
            Matcher matcher = regex.matcher(content);
            if (matcher.find()) {
                parseUrl = matcher.group(1);
            }
            if (!TextUtils.isEmpty(parseUrl) && (parseUrl.contains(".m3u8") || parseUrl.contains(".mp4"))) {
                // 直接播放
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("playUrl", "");
                result.put("url", parseUrl);
                // 添加弹幕支持
                addDanmaku(result, id);
                return result.toString();
            } else {
                // 需要嗅探或代理
                JSONObject result = new JSONObject();
                result.put("parse", 1);
                result.put("playUrl", "");
                result.put("url", playUrl);
                // 添加header
                JSONObject h = new JSONObject();
                h.put("User-Agent", defaultUa);
                result.put("header", h.toString());
                // 添加弹幕支持
                addDanmaku(result, id);
                return result.toString();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    private void addDanmaku(JSONObject result, String id) throws Exception {
        if (rule != null && rule.has("danmakuUrl")) {
            // 优化：确保id是正确的占位符值，如果id是URL，可能需要提取部分
            String danId = id;
            if (rule.has("danmakuIdR")) {
                danId = id.replaceAll(rule.optString("danmakuIdR"), "");
            }
            String danmakuUrl = rule.optString("danmakuUrl").replace("{id}", danId);
            // 假设danmakuUrl是弹幕文件URL
            JSONArray danmakuArray = new JSONArray();
            JSONObject dm = new JSONObject();
            dm.put("name", "弹幕");
            dm.put("url", danmakuUrl);
            danmakuArray.put(dm);
            result.put("danmaku", danmakuArray);
        }
        // 或如果需要从内容中提取
        // String danmakuContent = fetch(danmakuUrl, headers);
        // 处理danmakuContent
    }

    protected String fixPicUrl(String pic) {
        if (TextUtils.isEmpty(pic)) return "";
        if (pic.startsWith("//")) {
            pic = "https:" + pic;
        } else if (pic.startsWith("/")) {
            pic = siteUrl + pic.substring(1);
        } else if (!pic.startsWith("http")) {
            pic = siteUrl + pic;
        }
        return pic;
    }

    // 带重试的请求工具
    protected String fetch(String url, Map<String, String> h) {
        Map<String, String> reqHeaders = h != null ? h : headers;
        int retry = 3;
        while (retry-- > 0) {
            try {
                return OkHttp.string(url, reqHeaders);
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        return "";
    }
}
