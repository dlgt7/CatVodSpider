package com.github.catvod.spider;

import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 秀儿影视 - 2026.01 修复增强版
 * 修复：图片加载 (data-src)、播放列表匹配 (module-player-list)、搜索路径
 */
public class Xiuer extends Spider {

    private static final String HOST = "https://www.xiuer.pro";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    @Override
    public String homeContent(boolean filter) {
        try {
            // 这里假设 AntiCrawlerEnhancer 是你环境中的反爬增强类
            String html = fetchContent(HOST, null);
            if (TextUtils.isEmpty(html)) return Result.get().msg("首页加载失败").string();
            Document doc = Jsoup.parse(html);

            List<Class> classes = new ArrayList<>();
            // 根据 JS 规则手动配置或从导航栏提取
            String[] names = {"电影", "电视剧", "综艺", "动漫", "短剧", "纪录片"};
            String[] ids = {"dianying", "dianshiju", "zongyi", "dongman", "duanju", "jilupian"};
            for (int i = 0; i < names.length; i++) {
                classes.add(new Class(ids[i], names[i]));
            }

            return Result.string(classes, parseVodList(doc));
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().msg("首页解析异常").string();
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            // URL 格式：/show/tid/page/pg.html
            String url = HOST + "/show/" + tid + "/page/" + pg + ".html";
            String html = fetchContent(url, null);
            return Result.get().page(Integer.parseInt(pg), 100, 24, 2400).vod(parseVodList(Jsoup.parse(html))).string();
        } catch (Exception e) {
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = HOST + "/detail/" + id + ".html";
            String html = fetchContent(url, null);
            Document doc = Jsoup.parse(html);

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(doc.selectFirst("h1").text().trim());
            
            // 重点 1：图片取 data-src
            Element picElement = doc.selectFirst(".video-cover img, .module-item-pic img");
            if (picElement != null) {
                String pic = firstNonEmpty(picElement.attr("data-src"), picElement.attr("data-original"), picElement.attr("src"));
                vod.setVodPic(fixUrl(pic));
            }

            vod.setVodRemarks(doc.select(".video-info-aux").text().replaceAll("\\s+", " ").trim());
            vod.setVodContent(doc.select(".video-info-content").text().trim());

            // 详情信息提取
            Elements items = doc.select(".video-info-item");
            for (Element item : items) {
                String text = item.text();
                if (text.contains("导演：")) vod.setVodDirector(text.replace("导演：", "").trim());
                else if (text.contains("主演：")) vod.setVodActor(text.replace("主演：", "").trim());
                else if (text.contains("地区：")) vod.setVodArea(text.replace("地区：", "").trim());
                else if (text.contains("年份：")) vod.setVodYear(text.replace("年份：", "").trim());
            }

// 线路与集数提取
            Elements tabs = doc.select(".module-tab-item");
            Elements lists = doc.select(".module-player-list, .module-play-list");
            
            List<String> fromList = new ArrayList<>();
            List<String> urlList = new ArrayList<>();

            int size = Math.min(tabs.size(), lists.size());
            for (int i = 0; i < size; i++) {
                String from = tabs.get(i).text().trim();
                
                Elements eps = lists.get(i).select("a");
                List<String> epList = new ArrayList<>();
                for (Element a : eps) {
                    String href = a.attr("href");
                    
                    // 过滤掉不含播放路径的按钮（如：排序、下拉菜单等）
                    if (!href.contains("/play/")) continue;
                    
                    String epName = a.text().trim();
                    String epId = href.split("/play/")[1].replace(".html", "").trim();
                    epList.add(epName + "$" + epId);
                }
                
                if (!epList.isEmpty()) {
                    fromList.add(from);
                    urlList.add(TextUtils.join("#", epList));
                }
            }

            vod.setVodPlayFrom(TextUtils.join("$$$", fromList));
            vod.setVodPlayUrl(TextUtils.join("$$$", urlList));

            return Result.get().vod(vod).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().msg("详情页解析失败").string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            // 路径匹配：/vod/search/wd/**.html，对搜索关键词进行URL编码
            String encodedKey = java.net.URLEncoder.encode(key, "UTF-8");
            String url = HOST + "/vod/search/wd/" + encodedKey + ".html";
            String html = fetchContent(url, null);
            Document doc = Jsoup.parse(html);
            List<Vod> list = new ArrayList<>();
            
            // 选择器匹配：.module-search-item
            Elements items = doc.select(".module-search-item");
            for (Element item : items) {
                Element a = item.selectFirst("a[href*=/detail/]");
                if (a == null) continue;

                String id = a.attr("href").replaceAll(".*/detail/|\\.html.*", "").trim();
                // 标题匹配：h3
                String name = item.select("h3").text().trim();
                // 图片匹配：data-src
                Element img = item.selectFirst("img");
                String pic = img != null ? firstNonEmpty(img.attr("data-src"), img.attr("data-original"), img.attr("src")) : "";
                // 备注匹配：.video-serial
                String remark = item.select(".video-serial").text().trim();

                if (!id.isEmpty() && !name.isEmpty()) {
                    list.add(new Vod(id, name, fixUrl(pic), remark));
                }
            }
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // 解析播放页面以获取真实播放地址
            String playUrl = HOST + "/play/" + id + ".html";
            String html = fetchContent(playUrl, getHeaders());
            
            if (TextUtils.isEmpty(html)) {
                return Result.get().url("").string();
            }
            
            // 尝试从页面中提取播放地址
            Document doc = Jsoup.parse(html);
            
            // 查找播放器相关的元素
            String videoUrl = extractVideoUrl(doc, html);
            
            if (!TextUtils.isEmpty(videoUrl)) {
                // 如果找到真实播放地址，返回该地址
                return Result.get().url(videoUrl).parse(0).header(getHeaders()).string();
            } else {
                // 如果没找到真实播放地址，返回页面地址让前端解析
                return Result.get().url(playUrl).parse(1).header(getHeaders()).string();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().url("").string();
        }
    }

    private List<Vod> parseVodList(Document doc) {
        List<Vod> list = new ArrayList<>();
        Elements items = doc.select(".module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a[href*=/detail/]");
            if (a == null) continue;
            
            String id = a.attr("href").replaceAll(".*/detail/|\\.html.*", "").trim();
            String name = firstNonEmpty(a.attr("title"), item.select(".module-item-title").text());
            // 重点：优先使用 data-src
            String pic = firstNonEmpty(item.selectFirst("img").attr("data-src"), item.selectFirst("img").attr("data-original"), item.selectFirst("img").attr("src"));
            String remark = firstNonEmpty(item.select(".module-item-note").text(), item.select(".module-item-text").text()).trim();
            
            if (!id.isEmpty() && !name.isEmpty()) {
                list.add(new Vod(id, name, fixUrl(pic), remark));
            }
        }
        return list;
    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Referer", HOST + "/");
        return headers;
    }

    private String firstNonEmpty(String... strs) {
        for (String s : strs) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return "";
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return HOST + url;
        return url;
    }
    
    /**
     * 安全获取网页内容，优先使用AntiCrawlerEnhancer，失败时回退到OkHttp
     */
    private String fetchContent(String url, HashMap<String, String> headers) {
        try {
            // 首先尝试使用AntiCrawlerEnhancer
            // 检查AntiCrawlerEnhancer是否可用
            AntiCrawlerEnhancer enhancer = null;
            try {
                enhancer = AntiCrawlerEnhancer.get();
            } catch (Exception e) {
                SpiderDebug.log("AntiCrawlerEnhancer不可用: " + e.getMessage());
            }
            
            if (enhancer != null) {
                try {
                    String content = enhancer.enhancedGet(url, headers);
                    if (!TextUtils.isEmpty(content)) {
                        return content;
                    }
                } catch (Exception e) {
                    SpiderDebug.log("AntiCrawlerEnhancer获取失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("AntiCrawlerEnhancer获取失败: " + e.getMessage());
        }
        
        try {
            // 回退到OkHttp
            return OkHttp.string(url, headers != null ? headers : getHeaders());
        } catch (Exception e) {
            SpiderDebug.log("OkHttp获取失败: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 从播放页面中提取视频URL
     */
    private String extractVideoUrl(Document doc, String html) {
        // 方法1: 尝试从script标签中提取视频地址
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String scriptContent = script.html();
            if (!TextUtils.isEmpty(scriptContent)) {
                // 查找常见的视频URL模式
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(https?:\\/\\/[^\\\"]*?\\.(mp4|m3u8|flv|avi|mov|wmv|webm)[^\\\"']*)");
                java.util.regex.Matcher matcher = pattern.matcher(scriptContent);
                if (matcher.find()) {
                    return matcher.group(1).replace("\\", "");
                }
                
                // 查找player配置
                java.util.regex.Pattern playerPattern = java.util.regex.Pattern.compile("var\\s+\\(player_[^=]+=[^;]+\\)");
                java.util.regex.Matcher playerMatcher = playerPattern.matcher(scriptContent);
                if (playerMatcher.find()) {
                    String playerConfig = playerMatcher.group(1);
                    // 查找url字段
                    java.util.regex.Pattern simpleUrlPattern = java.util.regex.Pattern.compile("url\\s*:\\s*['\"]([^'\"]+)['\"]");
                    java.util.regex.Matcher simpleUrlMatcher = simpleUrlPattern.matcher(playerConfig);
                    if (simpleUrlMatcher.find()) {
                        return simpleUrlMatcher.group(1);
                    }
                    
                    // 查找mac_url字段（常见于苹果CMS系统）
                    java.util.regex.Pattern simpleMacUrlPattern = java.util.regex.Pattern.compile("mac_url\\s*:\\s*['\"]([^'\"]+)['\"]");
                    java.util.regex.Matcher simpleMacUrlMatcher = simpleMacUrlPattern.matcher(playerConfig);
                    if (simpleMacUrlMatcher.find()) {
                        String encodedUrl = simpleMacUrlMatcher.group(1);
                        // 尝试Base64解码
                        String decodedUrl = tryDecodeBase64(encodedUrl);
                        if (!TextUtils.isEmpty(decodedUrl) && isValidVideoUrl(decodedUrl)) {
                            return decodedUrl;
                        }
                        return encodedUrl;
                    }
                }
                
                // 查找可能包含Base64编码的视频地址
                java.util.regex.Pattern base64Pattern = java.util.regex.Pattern.compile("([A-Za-z0-9+/]{20,}={0,2})");
                java.util.regex.Matcher base64Matcher = base64Pattern.matcher(scriptContent);
                while (base64Matcher.find()) {
                    String base64Str = base64Matcher.group(1);
                    String decodedUrl = tryDecodeBase64(base64Str);
                    if (!TextUtils.isEmpty(decodedUrl) && isValidVideoUrl(decodedUrl)) {
                        return decodedUrl;
                    }
                }
            }
        }
        
        // 方法2: 查找video标签
        Elements videos = doc.select("video source[src]");
        if (!videos.isEmpty()) {
            return videos.first().attr("src");
        }
        
        // 方法3: 查找带有播放类名的元素
        Elements playerElements = doc.select(".player video, #player video, .play-video video, .video-player video");
        for (Element elem : playerElements) {
            String src = elem.attr("src");
            if (!TextUtils.isEmpty(src) && isValidVideoUrl(src)) {
                return fixUrl(src);
            }
            
            // 检查source子元素
            Elements sources = elem.select("source");
            if (!sources.isEmpty()) {
                String sourceSrc = sources.first().attr("src");
                if (!TextUtils.isEmpty(sourceSrc)) {
                    return fixUrl(sourceSrc);
                }
            }
        }
        
        // 方法4: 从数据属性中查找
        Elements dataElements = doc.select("[data-original*=.mp4], [data-original*=.m3u8], [data-src*=.mp4], [data-src*=.m3u8], [src*=.mp4], [src*=.m3u8]");
        for (Element elem : dataElements) {
            String src = elem.attr("src");
            if (isValidVideoUrl(src)) {
                return fixUrl(src);
            }
            
            String dataOriginal = elem.attr("data-original");
            if (isValidVideoUrl(dataOriginal)) {
                return fixUrl(dataOriginal);
            }
            
            String dataSrc = elem.attr("data-src");
            if (isValidVideoUrl(dataSrc)) {
                return fixUrl(dataSrc);
            }
        }
        
        // 方法5: 尝试从页面HTML文本中直接查找视频URL
        java.util.regex.Pattern directUrlPattern = java.util.regex.Pattern.compile("(https?:\\/\\/[^\\\"]*?\\.(mp4|m3u8|flv|avi|mov|wmv|webm)[^\\\"'&]*)");
        java.util.regex.Matcher directMatcher = directUrlPattern.matcher(html);
        if (directMatcher.find()) {
            return directMatcher.group(1);
        }
        
        return "";
    }
    
    /**
     * 尝试Base64解码
     */
    private String tryDecodeBase64(String str) {
        try {
            // 检查是否是Base64编码的字符串
            if (android.util.Base64.isBase64(str.getBytes())) {
                byte[] decodedBytes = android.util.Base64.decode(str, android.util.Base64.DEFAULT);
                return new String(decodedBytes);
            }
        } catch (Exception e) {
            SpiderDebug.log("Base64解码失败: " + e.getMessage());
        }
        return str;
    }
    
    /**
     * 检查是否为有效的视频URL
     */
    private boolean isValidVideoUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        return url.contains(".mp4") || url.contains(".m3u8") || url.contains(".flv") || 
               url.contains(".avi") || url.contains(".mov") || url.contains(".wmv") || 
               url.contains(".webm");
    }
}
