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
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heiliao Spider for TVBox
 * 基于通用模板重构，适配黑料网结构
 */
public class Heiliao extends Spider {

    protected String siteUrl = "https://o19qz.ouvfodey.xyz/"; // 主域名 - 发布页: https://heiliao43.com
    // 备用域名: https://o19qz.ouvfodey.xyz/, https://nrsgw.otikxku.xyz/, https://d2007jccyfwjlg.cloudfront.net/
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
            List<Class> classes = new ArrayList<>();
            LinkedHashMap<String, List<Filter>> filtersMap = new LinkedHashMap<>();
            List<Filter> filters = new ArrayList<>();
            
            // 从网站获取分类
            String homeUrl = siteUrl;
            String content = fetch(homeUrl, headers);
            Document doc = Jsoup.parse(content);
            
            // 根据HTML结构提取导航菜单
            Elements navItems = doc.select("nav a[href]");
            for (Element navItem : navItems) {
                String href = navItem.attr("href");
                String text = navItem.text();
                
                // 过滤出有意义的分类链接
                if (href.contains("/hlcg/") || href.contains("/jrrs/") || 
                    href.contains("/lsdg/") || href.contains("/mxl/") || 
                    href.contains("/whhl/") || href.contains("/qyhl/") ||
                    href.contains("/xycg/")) {
                    
                    String typeId = href.replace(siteUrl, "").replaceAll("/", "");
                    if (typeId.isEmpty()) typeId = "hlcg"; // 默认分类
                    classes.add(new Class(typeId, text));
                }
            }
            
            // 如果没有从网站获取到分类，使用默认分类
            if (classes.isEmpty()) {
                String[][] nav = {
                    {"", "最新黑料"},
                    {"hlcg", "黑料吃瓜"},
                    {"jrrs", "今日热点"},
                    {"rmhl", "热门黑料"},
                    {"jdh", "经典黑料"},
                    {"xycg", "校园专区"},
                    {"whhl", "网红黑料"},
                    {"mxl", "明星八卦"},
                    {"qyhl", "反差专区"}
                };
                for (String[] item : nav) {
                    classes.add(new Class(item[0], item[1]));
                }
            }
            
            Result result = new Result();
            result.classes(classes);
            if (filter) {
                filtersMap.put("default", filters);
                result.filters(filtersMap);
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
            // 获取首页推荐视频
            String homeUrl = siteUrl;
            String content = fetch(homeUrl, headers);
            Document doc = Jsoup.parse(content);
            
            // 根据HTML结构查找视频项
            Elements items = doc.select("article, div.video-item, div.archive-item, .post-item");
            for (Element item : items) {
                Element link = item.selectFirst("a[href]");
                if (link == null) continue;
                
                String vodId = link.attr("href");
                if (!vodId.startsWith("http")) vodId = siteUrl + (vodId.startsWith("/") ? "" : "/") + vodId;
                
                String vodName = "";
                Element titleEl = item.selectFirst("h2, h3, h1, .title, .post-title");
                if (titleEl != null) vodName = titleEl.text().trim();
                
                if (vodName.isEmpty()) vodName = link.attr("title");
                if (vodName.isEmpty()) continue;
                
                String vodPic = "";
                Element imgEl = item.selectFirst("img[src]");
                if (imgEl != null) {
                    vodPic = imgEl.attr("src");
                    if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
                    if (!vodPic.startsWith("http")) vodPic = siteUrl + (vodPic.startsWith("/") ? "" : "/") + vodPic;
                }
                
                String vodRemarks = "";
                Element remarkEl = item.selectFirst(".date, .time, .meta, .remarks, .post-meta");
                if (remarkEl != null) vodRemarks = remarkEl.text().trim();
                
                list.add(new Vod(vodId, vodName, fixUrl(vodPic), vodRemarks));
                
                // 限制首页视频数量
                if (list.size() >= 10) break;
            }

            Result result = new Result();
            result.vod(list);
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
            // 构建分类URL
            String cateUrl;
            if (tid.isEmpty() || tid.equals("")) {
                cateUrl = siteUrl + "hlcg/";
            } else {
                cateUrl = siteUrl + tid + "/";
            }
            
            // 添加分页参数
            if (pg != null && !pg.equals("1")) {
                if (cateUrl.endsWith("/")) {
                    cateUrl += "page/" + pg + "/";
                } else {
                    cateUrl += "/page/" + pg + "/";
                }
            }
            
            String content = fetch(cateUrl, headers);
            Document doc = Jsoup.parse(content);
            
            // 根据HTML结构查找视频项
            Elements items = doc.select("article, div.video-item, div.archive-item, .post-item");
            for (Element item : items) {
                Element link = item.selectFirst("a[href]");
                if (link == null) continue;
                
                String vodId = link.attr("href");
                if (!vodId.startsWith("http")) vodId = siteUrl + (vodId.startsWith("/") ? "" : "/") + vodId;
                
                String vodName = "";
                Element titleEl = item.selectFirst("h2, h3, h1, .title, .post-title");
                if (titleEl != null) vodName = titleEl.text().trim();
                
                if (vodName.isEmpty()) vodName = link.attr("title");
                if (vodName.isEmpty()) continue;
                
                String vodPic = "";
                Element imgEl = item.selectFirst("img[src]");
                if (imgEl != null) {
                    vodPic = imgEl.attr("src");
                    if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
                    if (!vodPic.startsWith("http")) vodPic = siteUrl + (vodPic.startsWith("/") ? "" : "/") + vodPic;
                }
                
                String vodRemarks = "";
                Element remarkEl = item.selectFirst(".date, .time, .meta, .remarks, .post-meta");
                if (remarkEl != null) vodRemarks = remarkEl.text().trim();
                
                list.add(new Vod(vodId, vodName, fixUrl(vodPic), vodRemarks));
            }

            Result result = new Result();
            result.vod(list);
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
            String content = fetch(id, headers);
            Document doc = Jsoup.parse(content);
            Vod vod = new Vod();
            
            vod.setVodId(id);
            
            // 提取标题
            Element titleEl = doc.selectFirst("h1.title, h1.entry-title, .article-title, .post-title");
            vod.setVodName(titleEl != null ? titleEl.text().trim() : "未知视频");
            
            // 提取图片
            Element picEl = doc.selectFirst("meta[property=og:image]");
            if (picEl != null) {
                vod.setVodPic(fixUrl(picEl.attr("content")));
            } else {
                Element imgEl = doc.selectFirst("img.wp-post-image, .entry-content img, .article-content img");
                if (imgEl != null) {
                    vod.setVodPic(fixUrl(imgEl.attr("src")));
                }
            }
            
            // 提取描述
            Element descEl = doc.selectFirst(".entry-content, .article-content, #content, .post-content");
            vod.setVodContent(descEl != null ? descEl.text().trim() : "");
            
            // 提取其他信息
            Element metaEl = doc.selectFirst(".post-meta, .entry-meta, .article-meta");
            if (metaEl != null) {
                String metaText = metaEl.text();
                // 尝试提取年份、地区等信息
                if (metaText.contains("年份") || metaText.contains("时间")) {
                    vod.setVodYear(extractInfo(metaText, "年份|时间|发布"));
                }
            }
            
            // 提取播放源
            Map<String, String> sites = new LinkedHashMap<>();
            
            // 查找视频标签
            Elements videos = doc.select("video source[src], video[src]");
            if (!videos.isEmpty()) {
                List<String> playUrls = new ArrayList<>();
                for (Element video : videos) {
                    String src = video.attr("src");
                    if (!src.isEmpty()) {
                        if (src.startsWith("//")) src = "https:" + src;
                        if (!src.startsWith("http")) src = siteUrl + (src.startsWith("/") ? "" : "/") + src;
                        playUrls.add("播放$" + src);
                    }
                    
                    // 查找source标签
                    Elements sources = video.select("source[src]");
                    for (Element source : sources) {
                        String sourceSrc = source.attr("src");
                        if (!sourceSrc.isEmpty()) {
                            if (sourceSrc.startsWith("//")) sourceSrc = "https:" + sourceSrc;
                            if (!sourceSrc.startsWith("http")) sourceSrc = siteUrl + (sourceSrc.startsWith("/") ? "" : "/") + sourceSrc;
                            playUrls.add("播放$" + sourceSrc);
                        }
                    }
                }
                
                if (!playUrls.isEmpty()) {
                    sites.put("直连", TextUtils.join("#", playUrls));
                }
            }
            
            // 查找iframe嵌入的视频
            Elements iframes = doc.select("iframe[src]");
            if (!iframes.isEmpty()) {
                List<String> iframeUrls = new ArrayList<>();
                for (Element iframe : iframes) {
                    String src = iframe.attr("src");
                    if (!src.isEmpty()) {
                        if (src.startsWith("//")) src = "https:" + src;
                        if (!src.startsWith("http")) src = siteUrl + (src.startsWith("/") ? "" : "/") + src;
                        iframeUrls.add("外部播放$" + src);
                    }
                }
                
                if (!iframeUrls.isEmpty()) {
                    sites.put("外链", TextUtils.join("#", iframeUrls));
                }
            }
            
            // 查找可能的视频链接
            if (sites.isEmpty()) {
                // 使用正则表达式查找可能的视频链接
                Pattern pattern = Pattern.compile("(https?://[^\\s\"<>]*\\.(?:mp4|m3u8|avi|flv|mov|wmv|webm|mkv))");
                Matcher matcher = pattern.matcher(content);
                List<String> matchedUrls = new ArrayList<>();
                
                while (matcher.find()) {
                    String url = matcher.group(1);
                    matchedUrls.add("检测$" + url);
                }
                
                if (!matchedUrls.isEmpty()) {
                    sites.put("检测", TextUtils.join("#", matchedUrls));
                }
            }
            
            if (!sites.isEmpty()) {
                vod.setVodPlayFrom(TextUtils.join("$$$", sites.keySet()));
                vod.setVodPlayUrl(TextUtils.join("$$$", sites.values()));
            }

            Result result = new Result();
            result.vod(Arrays.asList(vod));
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
            SystemClock.sleep(new Random().nextInt(301) + 200);
            List<Vod> list = new ArrayList<>();
            
            // 构建搜索URL
            String searchUrl = siteUrl + "?s=" + URLEncoder.encode(key, "UTF-8");
            String content = fetch(searchUrl, headers);
            Document doc = Jsoup.parse(content);
            
            // 根据搜索结果页面结构查找视频
            Elements items = doc.select("article, div.search-result, .archive-item, .post-item");
            for (Element item : items) {
                Element link = item.selectFirst("a[href]");
                if (link == null) continue;
                
                String vodId = link.attr("href");
                if (!vodId.startsWith("http")) vodId = siteUrl + (vodId.startsWith("/") ? "" : "/") + vodId;
                
                String vodName = "";
                Element titleEl = item.selectFirst("h2, h3, h1, .title, .post-title");
                if (titleEl != null) vodName = titleEl.text().trim();
                
                if (vodName.isEmpty()) vodName = link.attr("title");
                if (vodName.isEmpty()) continue;
                
                String vodPic = "";
                Element imgEl = item.selectFirst("img[src]");
                if (imgEl != null) {
                    vodPic = imgEl.attr("src");
                    if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
                    if (!vodPic.startsWith("http")) vodPic = siteUrl + (vodPic.startsWith("/") ? "" : "/") + vodPic;
                }
                
                String vodRemarks = "";
                Element remarkEl = item.selectFirst(".date, .time, .meta, .remarks, .post-meta");
                if (remarkEl != null) vodRemarks = remarkEl.text().trim();
                
                list.add(new Vod(vodId, vodName, fixUrl(vodPic), vodRemarks));
            }

            Result result = new Result();
            result.vod(list);
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (false || Util.isMedia(id)) {
                // 直接播放
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("playUrl", "");
                result.put("url", id);
                // 添加弹幕支持
                addDanmaku(result, id);
                return result.toString();
            }
            
            // 需要解析或代理
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("playUrl", "");
            result.put("url", id);
            // 添加header
            JSONObject h = new JSONObject();
            h.put("User-Agent", defaultUa);
            h.put("Referer", siteUrl);
            result.put("header", h.toString());
            // 添加弹幕支持
            addDanmaku(result, id);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    private void addDanmaku(JSONObject result, String id) throws Exception {
        // 暂时不需要弹幕功能
    }

    protected String fixUrl(String pic) {
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
    
    // 辅助方法：从文本中提取指定信息
    private String extractInfo(String text, String pattern) {
        Pattern p = Pattern.compile(pattern + "[^\\d]*(\\d+)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }
}
