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

public class Xiuer extends Spider {

    private static final String HOST = "https://www.xiuer.pro";

    @Override
    public String homeContent(boolean filter) {
        try {
            String html = AntiCrawlerEnhancer.get().enhancedGet(HOST, null);
            if (TextUtils.isEmpty(html)) return Result.get().msg("获取首页源码为空").string();
            Document doc = Jsoup.parse(html);

            List<Class> classes = new ArrayList<>();
            Elements navLinks = doc.select("a[href*=/show/], a[href*=/type/]");
            for (Element a : navLinks) {
                String href = a.attr("href");
                String typeId = href.replaceAll(".*/(show|type)/|\\.html.*", "").trim();
                String typeName = a.ownText().trim();
                if (typeName.isEmpty() && a.selectFirst("span, strong") != null) {
                    typeName = a.selectFirst("span, strong").ownText().trim();
                }
                if (!typeId.isEmpty() && !typeName.isEmpty() && classes.stream().noneMatch(c -> c.getTypeId().equals(typeId))) {
                    classes.add(new Class(typeId, typeName));
                }
            }

            List<Vod> videos = new ArrayList<>();
            Elements modules = doc.select(".module");
            for (Element module : modules) {
                Element title = module.selectFirst(".module-title, .title, h2");
                Elements items = module.select(".module-item");
                if (title == null || items.size() < 4) continue;

                for (Element item : items) {
                    Element a = item.selectFirst("a[href*=/detail/]");
                    if (a == null) continue;
                    String id = a.attr("href").replaceAll(".*/detail/|\\.html.*", "").trim();
                    String name = firstNonEmpty(a.attr("title"), item.select(".module-item-title").text());
                    String pic = firstNonEmpty(item.selectFirst("img").attr("data-original"), item.selectFirst("img").attr("src"));
                    videos.add(new Vod(id, name, fixUrl(pic), item.select(".module-item-note").text()));
                }
            }
            return Result.string(classes, videos);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().msg("首页加载异常").string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String url = HOST + "/detail/" + ids.get(0) + ".html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            if (TextUtils.isEmpty(html)) return Result.get().msg("获取详情页失败").string();
            Document doc = Jsoup.parse(html);

            Vod vod = new Vod();
            vod.setVodId(ids.get(0));
            
            // 详情选择器加固：秀儿影视部分页面 strong 可能不在 title-item 里
            Element nameNode = doc.selectFirst(".module-info-item-title strong, h1, .page-title");
            vod.setVodName(nameNode != null ? nameNode.text().trim() : "未知标题");

            Element picNode = doc.selectFirst(".module-info-poster img, .video-pic img");
            if (picNode != null) {
                vod.setVodPic(fixUrl(firstNonEmpty(picNode.attr("data-original"), picNode.attr("src"))));
            }

            Element contentNode = doc.selectFirst(".module-info-item-content, .vod_content");
            vod.setVodContent(contentNode != null ? contentNode.text().trim() : "暂无简介");

            // 播放列表加固
            Elements tabs = doc.select(".module-tab-item");
            Elements lists = doc.select(".module-play-list");
            
            // 如果 tabs 抓不到，尝试兼容另一种常见的网盘/单线路结构
            if (tabs.isEmpty()) {
                tabs = doc.select(".module-tab-content .module-tab-item, .layui-tab-title li");
            }

            List<String> fromList = new ArrayList<>();
            List<String> urlList = new ArrayList<>();

            for (int i = 0; i < Math.min(tabs.size(), lists.size()); i++) {
                String from = tabs.get(i).text().replaceAll("\\(\\d+\\)|\\s+|线路|集", "").trim();
                from = "线路" + (from.isEmpty() ? (i + 1) : from);

                List<String> eps = new ArrayList<>();
                Elements aList = lists.get(i).select("a");
                for (Element a : aList) {
                    String name = a.text().trim();
                    String href = a.attr("href");
                    if (href.startsWith("javascript") || href.isEmpty()) continue;
                    eps.add(name + "$" + fixUrl(href));
                }

                if (!eps.isEmpty()) {
                    fromList.add(from);
                    urlList.add(TextUtils.join("#", eps));
                }
            }

            vod.setVodPlayFrom(TextUtils.join("$$$", fromList));
            vod.setVodPlayUrl(TextUtils.join("$$$", urlList));

            return Result.get().vod(vod).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().msg("详情页解析异常: " + e.getMessage()).string();
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            String url = HOST + "/show/" + tid + "/page/" + pg + ".html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            return Result.get().page(Integer.parseInt(pg), 100, 24, 2400).vod(parseVodList(Jsoup.parse(html))).string();
        } catch (Exception e) {
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            String url = HOST + "/search/" + key + "----------.html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            return Result.string(parseVodList(Jsoup.parse(html)));
        } catch (Exception e) {
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        // 直接返回 ID (URL)，让壳子自带的解析器处理或直接播放
        return Result.get().url(id).parse(0).string();
    }

    private List<Vod> parseVodList(Document doc) {
        List<Vod> list = new ArrayList<>();
        Elements items = doc.select(".module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a[href*=/detail/]");
            if (a == null) continue;
            String id = a.attr("href").replaceAll(".*/detail/|\\.html.*", "").trim();
            String name = firstNonEmpty(a.attr("title"), item.select(".module-item-title").text());
            String pic = firstNonEmpty(item.selectFirst("img").attr("data-original"), item.selectFirst("img").attr("src"));
            list.add(new Vod(id, name, fixUrl(pic), item.select(".module-item-note").text()));
        }
        return list;
    }

    private String firstNonEmpty(String... strs) {
        for (String s : strs) if (!TextUtils.isEmpty(s)) return s;
        return "";
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return HOST + url;
        return url;
    }
}
