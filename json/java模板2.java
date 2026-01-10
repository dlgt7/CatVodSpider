// 通用 CatVodSpider Java 模板（严格匹配你提供的最新源码）
// 已修正所有低级错误，可直接复制使用
// 使用时：修改类名为站点名（如 Ali.java），填写站点特定逻辑即可

package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UniversalSpider extends Spider {

    protected String siteUrl = "https://example.com"; // 站点URL，替换为实际站点
    protected String defaultUa = Util.CHROME; // 默认User-Agent，使用Util.CHROME

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
                // 初始化站点URL和headers
                siteUrl = extend.optString("siteUrl", siteUrl).trim();
                if (!siteUrl.endsWith("/")) {
                    siteUrl += "/";
                }
                headers = new HashMap<>();
                headers.put("User-Agent", extend.optString("ua", defaultUa));
                headers.put("Referer", siteUrl);
                // 其他配置
                filterConfig = extend.optJSONObject("filter");
                playerConfig = extend.optJSONObject("player");
                rule = extend.optJSONObject("rule");
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            List<Class> classes = new ArrayList<>();
            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
            if (rule != null && rule.has("homeApi")) {
                // API模式
                String homeApi = rule.optString("homeApi");
                String data = fetch(siteUrl + homeApi, headers);
                JSONObject json = new JSONObject(data);
                JSONArray types = json.optJSONArray("types"); // 假设API返回{"types": [...]}
                for (int i = 0; i < types.length(); i++) {
                    JSONObject type = types.getJSONObject(i);
                    String typeId = type.optString("id");
                    String typeName = type.optString("name");
                    classes.add(new Class(typeId, typeName));
                    // 添加筛选
                    if (filter && filterConfig != null && filterConfig.has(typeId)) {
                        filters.put(typeId, parseFilters(filterConfig.optJSONArray(typeId)));
                    }
                }
            } else {
                // XPath模式
                String homeHtml = fetch(siteUrl, headers);
                Document doc = Jsoup.parse(homeHtml);
                Elements typeElements = doc.select(rule.optString("typeSelector", ".nav a")); // 示例选择器
                for (Element el : typeElements) {
                    String typeId = el.attr("href").replaceAll(rule.optString("typeIdR", ""), "");
                    String typeName = el.text().trim();
                    classes.add(new Class(typeId, typeName));
                    // 添加筛选（如果有）
                    if (filter && filterConfig != null && filterConfig.has(typeId)) {
                        filters.put(typeId, parseFilters(filterConfig.optJSONArray(typeId)));
                    }
                }
            }
            return Result.string(classes, new ArrayList<Vod>(), filters);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    private List<Filter> parseFilters(JSONArray filterArray) throws Exception {
        List<Filter> filterList = new ArrayList<>();
        for (int i = 0; i < filterArray.length(); i++) {
            JSONObject f = filterArray.getJSONObject(i);
            String key = f.optString("key");
            String name = f.optString("name");
            List<Filter.Value> values = new ArrayList<>();
            JSONArray vals = f.optJSONArray("values");
            for (int j = 0; j < vals.length(); j++) {
                JSONObject v = vals.getJSONObject(j);
                values.add(new Filter.Value(v.optString("n"), v.optString("v")));
            }
            filterList.add(new Filter(key, name, values));
        }
        return filterList;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            List<Vod> list = new ArrayList<>();
            int page = Integer.parseInt(pg);
            String cateUrl = siteUrl + tid + "?page=" + page; // 示例分类URL
            if (extend != null && !extend.isEmpty()) {
                StringBuilder sb = new StringBuilder(cateUrl);
                sb.append("&");
                for (Map.Entry<String, String> entry : extend.entrySet()) {
                    sb.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue(), "UTF-8")).append("&");
                }
                cateUrl = sb.toString().substring(0, sb.length() - 1);
            }
            if (rule != null && rule.has("cateApi")) {
                // API模式
                String data = fetch(cateUrl, headers);
                JSONObject json = new JSONObject(data);
                JSONArray videos = json.optJSONArray("videos"); // 假设返回{"videos": [...]}
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject v = videos.getJSONObject(i);
                    String vodId = v.optString("id");
                    String vodName = v.optString("name");
                    String vodPic = fixPicUrl(v.optString("pic"));
                    String vodRemarks = v.optString("remarks");
                    list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
                }
            } else {
                // XPath模式
                String html = fetch(cateUrl, headers);
                Document doc = Jsoup.parse(html);
                Elements items = doc.select(rule.optString("vodSelector", ".vod-item")); // 示例
                for (Element item : items) {
                    String vodId = item.select("a").attr("href").replaceAll(rule.optString("vodIdR", ""), "");
                    String vodName = item.select("h3").text();
                    String vodPic = fixPicUrl(item.select("img").attr("src"));
                    String vodRemarks = item.select(".remarks").text();
                    list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
                }
            }
            // 分页信息（假设总页数从API或HTML中获取）
            int limit = 20;
            int total = Integer.MAX_VALUE; // 默认无限
            int pageCount = Integer.MAX_VALUE; // 默认无限
            // 尝试从页面解析总页数（示例）
            // 如果是HTML模式，可以从doc.select(".pagination .total").text() 获取
            // 这里假设无法获取，使用list.size()判断是否有下一页
            if (list.size() < limit) {
                pageCount = page;
                total = (page - 1) * limit + list.size();
            } else {
                total = (page) * limit + 1; // 假设还有更多
                pageCount = page + 1;
            }
            return Result.string(page, pageCount, limit, total, list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            if (ids == null || ids.isEmpty()) return "";
            String id = ids.get(0);
            String detailUrl = siteUrl + id; // 示例详情URL
            String html = fetch(detailUrl, headers);
            Document doc = Jsoup.parse(html);

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(doc.select(rule.optString("nameSelector", "h1")).text());
            vod.setVodPic(fixPicUrl(doc.select(rule.optString("picSelector", ".vod-pic img")).attr("src")));
            vod.setVodRemarks(doc.select(rule.optString("remarksSelector", ".remarks")).text());
            vod.setVodYear(doc.select(rule.optString("yearSelector")).text());
            vod.setVodArea(doc.select(rule.optString("areaSelector")).text());
            vod.setVodActor(doc.select(rule.optString("actorSelector")).text());
            vod.setVodDirector(doc.select(rule.optString("directorSelector")).text());
            vod.setVodContent(doc.select(rule.optString("contentSelector")).text());

            // 播放源加固版
            Elements tabs = doc.select(rule.optString("tabSelector", ".play-tabs li"));
            Elements lists = doc.select(rule.optString("listSelector", ".play-list"));
            StringBuilder playFrom = new StringBuilder();
            StringBuilder playUrl = new StringBuilder();
            for (int i = 0; i < tabs.size() && i < lists.size(); i++) {
                String from = tabs.get(i).text().trim();
                if (from.isEmpty()) from = "播放源 " + (i + 1); // 防止源名称为空
                playFrom.append(from).append("$$$");

                Elements episodes = lists.get(i).select("a");
                List<String> vodItems = new ArrayList<>();
                for (Element ep : episodes) {
                    String epName = ep.text().replace("$", "").replace("#", ""); // 过滤敏感字符
                    String epUrl = ep.attr("href");
                    vodItems.add(epName + "$" + epUrl);
                }
                playUrl.append(TextUtils.join("#", vodItems)).append("$$$");
            }
            if (playFrom.length() > 0) {
                playFrom.delete(playFrom.length() - 3, playFrom.length());
                playUrl.delete(playUrl.length() - 3, playUrl.length());
            }
            vod.setVodPlayFrom(playFrom.toString());
            vod.setVodPlayUrl(playUrl.toString());

            return Result.string(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            List<Vod> list = new ArrayList<>();
            String searchUrl = siteUrl + "/search?q=" + URLEncoder.encode(key, "UTF-8");
            String html = fetch(searchUrl, headers);
            Document doc = Jsoup.parse(html);
            Elements items = doc.select(rule.optString("searchSelector", ".search-item"));
            for (Element item : items) {
                String vodId = item.select("a").attr("href").replaceAll(rule.optString("vodIdR", ""), "");
                String vodName = item.select("h3").text();
                String vodPic = fixPicUrl(item.select("img").attr("src"));
                String vodRemarks = item.select(".remarks").text();
                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (id.startsWith("http")) {
                // 直接URL
                if (super.isVideoFormat(id)) { // 使用super.isVideoFormat
                    return Result.get().url(id).string();
                }
            }
            // 假设id是播放页URL
            String playUrl = siteUrl + id;
            String html = fetch(playUrl, headers);
            // 提取播放地址（示例使用正则或XPath）
            Pattern pattern = Pattern.compile(rule.optString("playUrlR", "var playerData = (.*?);"));
            Matcher matcher = pattern.matcher(html);
            String url = "";
            JSONArray subs = new JSONArray();
            JSONArray danmakuArray = new JSONArray();
            if (matcher.find()) {
                JSONObject playerData = new JSONObject(matcher.group(1));
                url = playerData.optString("url");
                if (url.startsWith("http")) {
                    return Result.get().url(url).string();
                }
            }
            // 嗅探或解析
            // 示例：如果需要解析
            String parseUrl = playerConfig.optString(flag, "");
            if (!TextUtils.isEmpty(parseUrl)) {
                if (parseUrl.startsWith("js:")) {
                    // 执行JS（但模板中无JS引擎，假设直接返回）
                    String js = parseUrl.substring(3);
                    // 这里可添加JS执行逻辑，如果有QuickJS
                } else {
                    // 请求解析接口
                    String parseContent = fetch(parseUrl + id, headers);
                    // 提取url（假设返回JSON{"url": "..."})
                    JSONObject parseJson = new JSONObject(parseContent);
                    url = parseJson.optString("url");
                }
            }
            // VIP解析检查
            // 假设根据parseUrl域名判断是否VIP，例如如果包含"vip"则视为VIP
            boolean isVip = parseUrl.contains("vip"); // 自定义逻辑，根据实际站点调整
            int parse = isVip ? 1 : 0;
            int jx = isVip ? 1 : 0;
            // 添加字幕
            addSubs(subs, html);
            // 添加headers
            JSONObject h = new JSONObject();
            h.put("User-Agent", headers.get("User-Agent"));
            // 添加弹幕支持
            addDanmaku(danmakuArray, id);
            return Result.get()
                .url(url)
                .header(h)
                .subs(subs)
                .danmaku(danmakuArray)
                .parse(parse)
                .jx(jx)
                .string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    private void addSubs(JSONArray subs, String html) throws Exception {
        // 示例：从html中提取字幕
        Pattern subPattern = Pattern.compile("<subtitle>(.*?)</subtitle>");
        Matcher subMatcher = subPattern.matcher(html);
        if (subMatcher.find()) {
            String subUrl = subMatcher.group(1);
            if (Util.isSub(Util.getExt(subUrl))) { // 使用Util.isSub和Util.getExt
                JSONObject sub = new JSONObject();
                sub.put("name", "字幕");
                sub.put("url", subUrl);
                sub.put("format", Util.getExt(subUrl));
                subs.put(sub);
            }
        }
    }

    private void addDanmaku(JSONArray danmakuArray, String id) throws Exception {
        if (rule != null && rule.has("danmakuUrl")) {
            // 优化：确保id是正确的占位符值，如果id是URL，可能需要提取部分
            String danId = id;
            if (rule.has("danmakuIdR")) {
                danId = id.replaceAll(rule.optString("danmakuIdR"), "");
            }
            String danmakuUrl = rule.optString("danmakuUrl").replace("{id}", danId);
            // 假设danmakuUrl是弹幕文件URL
            JSONObject dm = new JSONObject();
            dm.put("name", "弹幕");
            dm.put("url", danmakuUrl);
            danmakuArray.put(dm);
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
            if (siteUrl.endsWith("/")) {
                pic = siteUrl + pic.substring(1);
            } else {
                pic = siteUrl + pic;
            }
        } else if (!pic.startsWith("http")) {
            if (siteUrl.endsWith("/")) {
                pic = siteUrl + pic;
            } else {
                pic = siteUrl + "/" + pic;
            }
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
