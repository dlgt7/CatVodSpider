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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 秀儿影视 - 2026.01 终极增强版
 * 优化点：
 * 1. 完善 fixUrl 协议转换
 * 2. 增强详情页年份/地区正则匹配
 * 3. 优化播放列表清洗逻辑
 */
public class Xiuer extends Spider {

    private static final String HOST = "https://www.xiuer.pro";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    @Override
    public String homeContent(boolean filter) {
        try {
            String html = AntiCrawlerEnhancer.get().enhancedGet(HOST, null);
            if (TextUtils.isEmpty(html)) return Result.get().msg("首页加载失败").string();
            Document doc = Jsoup.parse(html);

            // 1. 分类提取
            List<Class> classes = new ArrayList<>();
            Elements navLinks = doc.select("a[href*=/show/], a[href*=/type/]");
            for (Element a : navLinks) {
                String href = a.attr("href");
                String typeId = href.replaceAll(".*/(show|type)/|\\.html.*", "").trim();
                String typeName = a.ownText().trim();
                if (typeName.isEmpty() && a.selectFirst("span, strong") != null) {
                    typeName = a.selectFirst("span, strong").ownText().trim();
                }
                if (!typeId.isEmpty() && !typeName.isEmpty() && !isDuplicate(classes, typeId)) {
                    classes.add(new Class(typeId, typeName));
                }
            }

            // 2. 首页数据
            return Result.string(classes, parseVodList(doc));
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().msg("首页解析异常").string();
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
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = HOST + "/detail/" + id + ".html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            Document doc = Jsoup.parse(html);

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(doc.selectFirst("h1.title, .page-title").text().trim());
            vod.setVodPic(fixUrl(doc.selectFirst(".video-cover img, .module-item-pic img").attr("data-original")));
            vod.setVodRemarks(doc.select(".video-info-aux").text().trim());
            
            // 年份与地区提取优化
            Elements tags = doc.select(".tag-link");
            for (Element tag : tags) {
                String text = tag.text().trim();
                if (text.matches("\\d{4}")) vod.setVodYear(text);
                else if (text.length() <= 4) vod.setVodArea(text);
            }

            vod.setVodActor(doc.select(".video-info-actor").text().trim());
            vod.setVodDirector(doc.select(".video-info-director").text().trim());
            vod.setVodContent(doc.select(".video-info-content").text().trim());

            // 线路与集数提取
            Elements tabs = doc.select(".module-tab-item");
            Elements lists = doc.select(".module-play-list, .sort-item");
            
            List<String> fromList = new ArrayList<>();
            List<String> urlList = new ArrayList<>();

            for (int i = 0; i < tabs.size(); i++) {
                String from = tabs.get(i).text().trim();
                if (from.isEmpty()) from = "线路" + (i + 1);
                
                Elements eps = lists.get(i).select("a");
                List<String> epList = new ArrayList<>();
                for (Element a : eps) {
                    String epName = a.text().trim();
                    String epId = a.attr("href").replaceAll(".*/play/|\\.html.*", "").trim();
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
            String url = HOST + "/search/" + key + "----------.html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            return Result.string(parseVodList(Jsoup.parse(html)));
        } catch (Exception e) {
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        // 默认返回播放 ID，交给壳子解析
        return Result.get().url(id).parse(0).header(getHeaders()).string();
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
            String remark = item.select(".module-item-note").text().trim();
            
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
        for (String s : strs) if (!TextUtils.isEmpty(s)) return s;
        return "";
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return HOST + url;
        if (url.startsWith("http://")) return url.replace("http://", "https://");
        return url;
    }

    private boolean isDuplicate(List<Class> classes, String id) {
        for (Class c : classes) if (c.getTypeId().equals(id)) return true;
        return false;
    }
}
