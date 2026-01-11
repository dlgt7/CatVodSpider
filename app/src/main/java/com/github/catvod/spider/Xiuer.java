package com.github.catvod.spider;

import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
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
            String html = AntiCrawlerEnhancer.get().enhancedGet(HOST, null);
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
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
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
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
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

            for (int i = 0; i < tabs.size(); i++) {
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
            // 重点 3：根据 JS 修改搜索 URL 路径
            String url = HOST + "/vod/search/wd/" + key + ".html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            Document doc = Jsoup.parse(html);
            
            List<Vod> list = new ArrayList<>();
            // 搜索结果通常在 .module-search-item
            Elements items = doc.select(".module-search-item, .module-item");
            for (Element item : items) {
                Element a = item.selectFirst("a[href*=/detail/]");
                if (a == null) continue;
                
                String id = a.attr("href").replaceAll(".*/detail/|\\.html.*", "").trim();
                String name = firstNonEmpty(item.select("h3").text(), item.select(".module-item-title").text(), a.attr("title")).trim();
                String pic = firstNonEmpty(item.selectFirst("img").attr("data-src"), item.selectFirst("img").attr("data-original"), item.selectFirst("img").attr("src"));
                String remark = item.select(".video-serial, .module-item-note").text().trim();
                
                if (!id.isEmpty() && !name.isEmpty()) {
                    list.add(new Vod(id, name, fixUrl(pic), remark));
                }
            }
            return Result.string(list);
        } catch (Exception e) {
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        // 这里的 id 是 /play/ 后面那一串，壳子会自动尝试解析该 HTML 页面里的播放地址
        return Result.get().url(HOST + "/play/" + id + ".html").parse(1).header(getHeaders()).string();
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
}
