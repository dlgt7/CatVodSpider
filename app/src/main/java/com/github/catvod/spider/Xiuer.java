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
 * 秀儿影视 - 2026.01 修复版
 * 修复：Container Unsupported 问题（调整 playerContent 解析逻辑）
 */
public class Xiuer extends Spider {

    private static final String HOST = "https://www.xiuer.pro";

    @Override
    public String homeContent(boolean filter) {
        try {
            // 注意：AntiCrawlerEnhancer 需在您的工程中已实现，否则此处会编译报错
            String html = AntiCrawlerEnhancer.get().enhancedGet(HOST, null);
            if (TextUtils.isEmpty(html)) return Result.get().msg("首页源码为空").string();
            Document doc = Jsoup.parse(html);

            // 分类提取
            List<Class> classes = new ArrayList<>();
            Elements navLinks = doc.select("a[href*=/show/], a[href*=/type/]");
            for (Element a : navLinks) {
                String href = a.attr("href");
                String typeId = href.replaceAll(".*/(show|type)/|\\.html.*", "").trim();
                String typeName = a.ownText().trim();
                if (typeName.isEmpty() && a.selectFirst("span, strong") != null) {
                    typeName = a.selectFirst("span, strong").ownText().trim();
                }
                if (!typeId.isEmpty() && !typeName.isEmpty() && !typeName.equals("首页") && !typeName.equals("电影") && !typeName.equals("连续剧") && !typeName.equals("动漫") && !typeName.equals("综艺")) {
                    classes.add(new Class(typeId, typeName));
                }
            }

            // 首页视频
            List<Vod> list = new ArrayList<>();
            Elements modules = doc.select(".module:not([class*='search']):not([class*='block'])");
            for (Element module : modules) {
                if (module.select(".module-items").isEmpty()) continue;
                Elements items = module.select(".module-item");
                for (Element item : items) {
                    Element a = item.selectFirst("a[href*=/detail/]");
                    if (a == null) continue;
                    String id = a.attr("href").replaceAll(".*/detail/|\\.html.*", "").trim();
                    String name = firstNonEmpty(a.attr("title"), item.select(".module-item-title").text());
                    String pic = firstNonEmpty(item.selectFirst("img").attr("data-original"), item.selectFirst("img").attr("src"));
                    String remark = item.select(".module-item-note").text().trim();
                    list.add(new Vod(id, name, fixUrl(pic), remark));
                }
            }

            return Result.string(classes, list);
        } catch (Exception e) {
            return Result.get().msg("首页加载失败: " + e.getMessage()).string();
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            String url = HOST + "/" + (tid.contains("/") ? tid : "show/" + tid) + "----------" + pg + "---.html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            Document doc = Jsoup.parse(html);
            List<Vod> list = parseVodList(doc);
            int total = list.size() * Integer.parseInt(pg); 
            int limit = 24; 
            int pageCount = total / limit + 1;
            return Result.get().vod(list).page(Integer.parseInt(pg), pageCount, limit, total).string();
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

            // 基本信息
            String name = doc.selectFirst("h1.title, .video-info-title").text().trim();
            String picElement = doc.selectFirst(".video-cover img, .module-item-pic img").attr("data-src");
            if(TextUtils.isEmpty(picElement)) picElement = doc.selectFirst(".video-cover img, .module-item-pic img").attr("src");
            String pic = fixUrl(picElement);
            
            String type = doc.select(".video-info-items:contains(类型) a").text().trim();
            String year = doc.select(".video-info-items:contains(年份) a").text().trim();
            String area = doc.select(".video-info-items:contains(地区) a").text().trim();
            String actor = doc.select(".video-info-items:contains(主演) a").eachText().toString().replaceAll("[\\[\\]]", "").replace(",", " ");
            String director = doc.select(".video-info-items:contains(导演) a").eachText().toString().replaceAll("[\\[\\]]", "").replace(",", " ");
            String desc = doc.select(".video-info-content, .show-desc").text().trim();

            Vod vod = new Vod(id, name, pic);
            vod.setVodYear(year);
            vod.setVodArea(area);
            vod.setVodActor(actor);
            vod.setVodDirector(director);
            vod.setVodContent(desc);
            vod.setTypeName(type);

            // 播放源
            Elements sources = doc.select(".module-tab-item");
            Elements playlists = doc.select(".module-play-list");
            
            Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
            for (int i = 0; i < sources.size(); i++) {
                Element source = sources.get(i);
                String flag = source.selectFirst("span").text().trim();
                if (flag.isEmpty()) flag = "线路" + (i + 1);
                
                Element playlist = playlists.get(i);
                List<Vod.VodPlayBuilder.PlayUrl> playUrls = new ArrayList<>();
                Elements eps = playlist.select("a");
                for (Element ep : eps) {
                    String epName = ep.text().trim();
                    String playUrl = fixUrl(ep.attr("href"));
                    playUrls.add(new Vod.VodPlayBuilder.PlayUrl("", epName, playUrl));
                }
                builder.append(flag, playUrls);
            }

            Vod.VodPlayBuilder.BuildResult buildResult = builder.build();
            vod.setVodPlayFrom(buildResult.vodPlayFrom);
            vod.setVodPlayUrl(buildResult.vodPlayUrl);

            return Result.string(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().msg("详情加载失败").string();
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

    /**
     * 核心修复点：
     * 1. 显式调用 .parse() 设置 parse 为 1，让壳子通过嗅探获取视频地址。
     * 2. 确保返回的是完整的 URL 字符串。
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).parse().string(); 
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
