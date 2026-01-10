package com.github.catvod.spider;

import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 秀儿影视 - 2026.01 终极加固版
 * 修复：首页非内容模块自动过滤、播放列表静态/动态兼容校验、路径深度修复
 */
public class Xiuer extends Spider {

    private static final String HOST = "https://www.xiuer.pro";

    @Override
    public String homeContent(boolean filter) {
        try {
            String html = AntiCrawlerEnhancer.get().enhancedGet(HOST, null);
            Document doc = Jsoup.parse(html);

            // 1. 分类：兼容 show 和 type 路径
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

            // 2. 首页视频：通过标题+特征组合精准定位
            List<Vod> videos = new ArrayList<>();
            Elements modules = doc.select(".module");
            for (Element module : modules) {
                // 必须满足：有标题文本 + 不是轮播图容器(scroll-content) + item数量足够
                Element title = module.selectFirst(".module-title, .title, h2");
                Elements items = module.select(".module-item");
                
                // 过滤机制：小于5个通常是广告或推荐位；无标题通常是布局填充
                if (title == null || items.size() < 5 || module.hasClass("module-main")) continue;

                for (Element item : items) {
                    Element a = item.selectFirst("a[href*=/detail/]");
                    if (a == null) continue;

                    String id = a.attr("href").replaceAll(".*/detail/|\\.html.*", "").trim();
                    String name = firstNonEmpty(a.attr("title"), item.select(".module-item-title").text());
                    if (TextUtils.isEmpty(name)) {
                        Element img = a.selectFirst("img");
                        name = img != null ? img.attr("alt").trim() : "未知";
                    }

                    String pic = firstNonEmpty(
                            item.selectFirst("img").attr("data-original"),
                            item.selectFirst("img").attr("data-src"),
                            item.selectFirst("img").attr("src")
                    );

                    String remark = item.select(".module-item-note").text().trim();
                    videos.add(new Vod(id, name, fixUrl(pic), remark));
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
            Document doc = Jsoup.parse(html);

            Vod vod = new Vod();
            vod.setVodId(ids.get(0));
            vod.setVodName(doc.selectFirst(".module-info-item-title strong").text().trim());
            vod.setVodPic(fixUrl(doc.selectFirst(".module-info-poster img").attr("data-original")));
            vod.setVodContent(doc.select(".module-info-item-content").text().trim());

            // 3. 播放列表加固
            Elements tabs = doc.select(".module-tab-item");
            Elements lists = doc.select(".module-play-list");
            List<String> fromList = new ArrayList<>();
            List<String> urlList = new ArrayList<>();

            for (int i = 0; i < Math.min(tabs.size(), lists.size()); i++) {
                String from = tabs.get(i).text().replaceAll("\\(\\d+\\)|\\s+|线路", "").trim();
                from = "线路" + (from.isEmpty() ? (i + 1) : from);

                List<String> eps = new ArrayList<>();
                for (Element a : lists.get(i).select("a")) {
                    String name = a.text().trim();
                    String href = a.attr("href");
                    
                    // 深度过滤：排除 JS 脚本及空链接，只保留 play 路径或媒体后缀
                    if (name.isEmpty() || href.startsWith("javascript") || href.isEmpty()) continue;
                    
                    if (href.contains("/play/") || href.contains("/video/") || href.endsWith(".m3u8")) {
                        eps.add(name + "$" + fixUrl(href));
                    }
                }

                // 阈值控制：2026年不少影视站会放“假线路”占位，通过集数长度过滤
                if (eps.size() >= 1) { 
                    fromList.add(from);
                    urlList.add(TextUtils.join("#", eps));
                }
            }

            vod.setVodPlayFrom(TextUtils.join("$$$", fromList));
            vod.setVodPlayUrl(TextUtils.join("$$$", urlList));

            return Result.get().vod(vod).string();
        } catch (Exception e) {
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
