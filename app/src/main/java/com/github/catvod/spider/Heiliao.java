package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.*;

/**
 * Heiliao Spider for TVBox
 * 已修正与 Spider 基类参数不匹配导致的编译错误
 */
public class Heiliao extends Spider {

    private String siteUrl = "https://heiliao43.com"; 
    private Map<String, String> headers;

    private Map<String, String> getHeaders() {
        if (headers == null) {
            headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            headers.put("Referer", siteUrl + "/");
        }
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (extend != null && !extend.trim().isEmpty()) {
            siteUrl = extend.trim();
            if (!siteUrl.startsWith("http")) siteUrl = "https://" + siteUrl;
            if (siteUrl.endsWith("/")) siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
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
        return Result.string(classes, new ArrayList<>(), new LinkedHashMap<>());
    }

    // 重点修复：将 Map 改为 HashMap 以匹配 Spider.java 的定义
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (pg == null || pg.isEmpty()) pg = "1";
        
        String url;
        if (tid.contains("?s=")) {
            url = siteUrl + (tid.startsWith("/") ? tid : "/" + tid) + (pg.equals("1") ? "" : "&page=" + pg);
        } else {
            String path = tid.isEmpty() ? "" : (tid.startsWith("/") ? tid : "/" + tid + "/");
            url = siteUrl + path + (pg.equals("1") ? "" : "page/" + pg + "/");
        }

        String content = fetch(url);
        if (content.isEmpty()) return Result.get().vod(new ArrayList<>()).string();

        Document doc = Jsoup.parse(content, url);
        List<Vod> list = new ArrayList<>();

        Elements items = doc.select("div.archive-item, article, .post-item");
        for (Element item : items) {
            Element link = item.selectFirst("a");
            if (link == null) continue;

            String vodId = link.attr("href"); 
            String vodName = "";
            Element titleEl = item.selectFirst("h2, h3, .title");
            if (titleEl != null) vodName = titleEl.text().trim();

            if (vodName.isEmpty()) vodName = link.attr("title");
            if (vodName.isEmpty()) continue;

            Element img = item.selectFirst("img");
            String vodPic = img != null ? img.absUrl("src") : "";
            if (vodPic.isEmpty() && img != null) vodPic = img.absUrl("data-src");

            String vodRemarks = "";
            Element dateEl = item.selectFirst(".date, .time, .meta");
            if (dateEl != null) vodRemarks = dateEl.text().trim();

            // 使用 Vod.java 里的四参数构造函数
            list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
        }

        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, page + 1, 20, list.size() + 100).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0);
        if (!url.startsWith("http")) url = siteUrl + (url.startsWith("/") ? "" : "/") + url;

        String content = fetch(url);
        if (content.isEmpty()) return Result.get().vod(new ArrayList<>()).string();

        Document doc = Jsoup.parse(content, url);
        Vod vod = new Vod();
        
        // 修正为 Vod.java 对应的 Setter 方法
        vod.setVodId(url);

        Element titleEl = doc.selectFirst("h1.title, h1.entry-title, .article-title");
        vod.setVodName(titleEl != null ? titleEl.text().trim() : "未知视频");

        Element picEl = doc.selectFirst("meta[property=og:image]");
        vod.setVodPic(picEl != null ? picEl.attr("content") : "");

        Element contentEl = doc.selectFirst(".entry-content, .article-content, #content");
        vod.setVodContent(contentEl != null ? contentEl.text().trim() : "");

        Map<String, String> playMap = new LinkedHashMap<>();
        
        Elements videos = doc.select("video source, video[src]");
        int count = 1;
        for (Element v : videos) {
            String src = v.absUrl("src");
            if (src.contains(".mp4") || src.contains(".m3u8")) {
                playMap.put("播放源 " + count++, "立即播放$" + src);
            }
        }

        Elements iframes = doc.select("iframe[src]");
        for (Element f : iframes) {
            String src = f.absUrl("src");
            if (src.contains("url=") || src.contains(".mp4") || src.contains(".m3u8") || src.contains("share")) {
                playMap.put("外链源 " + count++, "解析播放$" + src);
            }
        }

        if (playMap.isEmpty()) {
            String regex = "(https?://[^\"]+\\.(?:m3u8|mp4))";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String matchUrl = matcher.group(1);
                if (!playMap.containsValue("立即播放$" + matchUrl)) {
                    playMap.put("探测源 " + count++, "立即播放$" + matchUrl);
                }
            }
        }

        if (!playMap.isEmpty()) {
            // 修正驼峰式方法名
            vod.setVodPlayFrom(String.join("$$$", playMap.keySet()));
            vod.setVodPlayUrl(String.join("$$$", playMap.values()));
        }

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return Result.get().url(id).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String searchTid = "/?s=" + URLEncoder.encode(key, "UTF-8");
        // 这里也要传递 null 或者 new HashMap<>()
        return categoryContent(searchTid, "1", false, null);
    }

    private String fetch(String url) {
        try {
            return OkHttp.string(url, getHeaders());
        } catch (Exception e) {
            return "";
        }
    }
}
