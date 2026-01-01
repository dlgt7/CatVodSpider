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
 * 优化了分页逻辑、图片补全以及基础播放抓取
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
        // 这里的 tid 建议统一不带开头斜杠，在拼接时处理
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

    @Override
    public String categoryContent(String tid, String pg, boolean filter, Map<String, String> extend) throws Exception {
        if (pg == null || pg.isEmpty()) pg = "1";
        
        String url;
        if (tid.contains("?s=")) {
            // 搜索结果的分页逻辑通常是 &page= 或使用 WordPress 默认格式
            url = siteUrl + (tid.startsWith("/") ? tid : "/" + tid) + (pg.equals("1") ? "" : "&page=" + pg);
        } else {
            // 分类页分页逻辑
            String path = tid.isEmpty() ? "" : (tid.startsWith("/") ? tid : "/" + tid + "/");
            url = siteUrl + path + (pg.equals("1") ? "" : "page/" + pg + "/");
        }

        String content = fetch(url);
        if (content.isEmpty()) return Result.get().vod(new ArrayList<>()).string();

        Document doc = Jsoup.parse(content, url); // 传入 url 用于 absUrl 补全
        List<Vod> list = new ArrayList<>();

        // 这里的选择器需根据实际网页结构调整，archive-item 是通用 WP 结构
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
            // 使用 absUrl 自动补全域名
            String vodPic = img != null ? img.absUrl("src") : "";
            if (vodPic.isEmpty() && img != null) vodPic = img.absUrl("data-src");

            String vodRemarks = "";
            Element dateEl = item.selectFirst(".date, .time, .meta");
            if (dateEl != null) vodRemarks = dateEl.text().trim();

            list.add(new Vod().vod_id(vodId).vod_name(vodName).vod_pic(vodPic).vod_remarks(vodRemarks));
        }

        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, page + 1, 20, list.size() + 100).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0);
        // 如果 vod_id 存的是相对路径，则补全
        if (!url.startsWith("http")) url = siteUrl + (url.startsWith("/") ? "" : "/") + url;

        String content = fetch(url);
        if (content.isEmpty()) return Result.get().vod(new ArrayList<>()).string();

        Document doc = Jsoup.parse(content, url);
        Vod vod = new Vod();
        vod.vod_id(url);

        Element titleEl = doc.selectFirst("h1.title, h1.entry-title, .article-title");
        vod.vod_name(titleEl != null ? titleEl.text().trim() : "未知视频");

        Element picEl = doc.selectFirst("meta[property=og:image]");
        vod.vod_pic(picEl != null ? picEl.attr("content") : "");

        Element contentEl = doc.selectFirst(".entry-content, .article-content, #content");
        vod.vod_content(contentEl != null ? contentEl.text().trim() : "");

        // 播放源解析
        Map<String, String> playMap = new LinkedHashMap<>();
        
        // 1. 尝试直接抓取 video 标签
        Elements videos = doc.select("video source, video[src]");
        int count = 1;
        for (Element v : videos) {
            String src = v.absUrl("src");
            if (src.contains(".mp4") || src.contains(".m3u8")) {
                playMap.put("播放源 " + count++, "立即播放$" + src);
            }
        }

        // 2. 尝试抓取 iframe
        Elements iframes = doc.select("iframe[src]");
        for (Element f : iframes) {
            String src = f.absUrl("src");
            if (src.contains("url=") || src.contains(".mp4") || src.contains(".m3u8") || src.contains("share")) {
                playMap.put("外链源 " + count++, "解析播放$" + src);
            }
        }

        if (playMap.isEmpty()) {
            // 备选：如果都没有，尝试从 script 脚本中匹配地址
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
            vod.setVod_play_from(String.join("$$$", playMap.keySet()));
            vod.setVod_play_url(String.join("$$$", playMap.values()));
        }

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 如果是直链地址，直接返回；如果是第三方解析页面，TVBox 会根据内置规则解析
        return Result.get().url(id).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        // 构建 WordPress 标准搜索路径
        String searchTid = "/?s=" + URLEncoder.encode(key, "UTF-8");
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
