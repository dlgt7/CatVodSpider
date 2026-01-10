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
 * 秀儿影视 - 2026.01 终极版
 * 修复：1. 编译路径符号丢失 2. 详情页线路抓取不到 3. 首页伪模块过滤
 */
public class Xiuer extends Spider {

    private static final String HOST = "https://www.xiuer.pro";

    @Override
    public String homeContent(boolean filter) {
        try {
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
                if (!typeId.isEmpty() && !typeName.isEmpty() && classes.stream().noneMatch(c -> c.getTypeId().equals(typeId))) {
                    classes.add(new Class(typeId, typeName));
                }
            }

            // 首页视频模块提取
            List<Vod> videos = new ArrayList<>();
            Elements modules = doc.select(".module");
            for (Element module : modules) {
                Element title = module.selectFirst(".module-title, .title, h2");
                Elements items = module.select(".module-item");
                // 过滤掉轮播图（通常无标题）或 item 过少的模块
                if (title == null || items.size() < 4) continue;

                for (Element item : items) {
                    Element a = item.selectFirst("a[href*=/detail/]");
                    if (a == null) continue;
                    String id = a.attr("href").replaceAll(".*/detail/|\\.html.*", "").trim();
                    String name = firstNonEmpty(a.attr("title"), item.select(".module-item-title").text());
                    String pic = firstNonEmpty(item.selectFirst("img").attr("data-original"), item.selectFirst("img").attr("src"));
                    String remark = item.select(".module-item-note").text().trim();
                    videos.add(new Vod(id, name, fixUrl(pic), remark));
                }
            }
            return Result.string(classes, videos);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().msg("首页解析异常").string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String url = HOST + "/detail/" + ids.get(0) + ".html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            if (TextUtils.isEmpty(html)) return Result.get().msg("详情页加载失败").string();
            Document doc = Jsoup.parse(html);

            Vod vod = new Vod();
            vod.setVodId(ids.get(0));
            
            // 基础信息提取
            Element titleNode = doc.selectFirst(".module-info-item-title strong, h1");
            vod.setVodName(titleNode != null ? titleNode.text().trim() : "未知");

            Element picNode = doc.selectFirst(".module-info-poster img");
            if (picNode != null) {
                vod.setVodPic(fixUrl(firstNonEmpty(picNode.attr("data-original"), picNode.attr("src"))));
            }

            Element contentNode = doc.selectFirst(".module-info-item-content, .vod_content");
            vod.setVodContent(contentNode != null ? contentNode.text().trim() : "");

            // 播放源解析 - 深度加固
            Elements tabs = doc.select(".module-tab-item");
            Elements lists = doc.select(".module-play-list, .module-list");

            List<String> fromList = new ArrayList<>();
            List<String> urlList = new ArrayList<>();

            for (int i = 0; i < Math.min(tabs.size(), lists.size()); i++) {
                // 清理线路名中的括号和集数数字，如 "蓝光(120)" -> "蓝光"
                String from = tabs.get(i).text().replaceAll("\\(\\d+\\)|\\d+集|\\s+", "").trim();
                if (from.isEmpty() || from.matches("\\d+")) from = "线路" + (i + 1);

                List<String> eps = new ArrayList<>();
                Elements aLinks = lists.get(i).select("a");
                for (Element a : aLinks) {
                    String name = a.text().trim();
                    String href = a.attr("href");
                    // 核心过滤：排除无效脚本链接，仅保留 play 路径
                    if (!name.isEmpty() && (href.contains("/play/") || href.contains("/video/"))) {
                        eps.add(name + "$" + fixUrl(href));
                    }
                }

                if (!eps.isEmpty()) {
                    fromList.add(from);
                    urlList.add(TextUtils.join("#", eps));
                }
            }

            // 如果上述常规解析失败，尝试备用单线路方案（针对某些特殊页面布局）
            if (fromList.isEmpty() && !lists.isEmpty()) {
                fromList.add("默认线路");
                List<String> eps = new ArrayList<>();
                for (Element a : lists.select("a")) {
                    if (a.attr("href").contains("/play/")) {
                        eps.add(a.text().trim() + "$" + fixUrl(a.attr("href")));
                    }
                }
                urlList.add(TextUtils.join("#", eps));
            }

            vod.setVodPlayFrom(TextUtils.join("$$$", fromList));
            vod.setVodPlayUrl(TextUtils.join("$$$", urlList));

            return Result.get().vod(vod).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().msg("详情解析失败").string();
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
