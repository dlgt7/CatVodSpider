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
 * 秀儿影视 - 2026.01 修复播放地址加载失败版本
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
            Document doc = Jsoup.parse(html);
            return Result.get().page(Integer.parseInt(pg), 100, 24, 2400).vod(parseVodList(doc)).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String url = HOST + "/detail/" + ids.get(0) + ".html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            Document doc = Jsoup.parse(html);

            Element detail = doc.selectFirst(".module-info-item");
            if (detail == null) return Result.get().msg("详情页无数据").string();

            String name = doc.selectFirst(".module-info-item-title h1").ownText().trim();
            String pic = fixUrl(doc.selectFirst(".module-info-poster img").attr("data-original"));
            String remark = doc.selectFirst(".module-info-item-title span").text().trim();

            String typeName = "";
            String year = "";
            String area = "";
            String actor = "";
            String director = "";
            String content = "";

            Elements infos = doc.select(".module-info-item-content");
            for (Element info : infos) {
                String text = info.text().trim();
                if (text.startsWith("类型：")) typeName = text.replace("类型：", "").trim();
                else if (text.startsWith("年份：")) year = text.replace("年份：", "").trim();
                else if (text.startsWith("地区：")) area = text.replace("地区：", "").trim();
                else if (text.startsWith("主演：")) actor = text.replace("主演：", "").trim();
                else if (text.startsWith("导演：")) director = text.replace("导演：", "").trim();
                else if (text.startsWith("简介：") || text.startsWith("剧情：")) content = text.replaceFirst("^(简介|剧情)：", "").trim();
            }

            // 播放列表（简单提取，多数情况够用）
            String vodPlayFrom = "";
            String vodPlayUrl = "";

            Elements tabs = doc.select(".module-tab-item");
            List<String> fromList = new ArrayList<>();
            List<String> urlList = new ArrayList<>();

            for (Element tab : tabs) {
                String from = tab.text().trim();
                if (from.isEmpty()) continue;

                fromList.add(from);

                StringBuilder sb = new StringBuilder();
                Elements eps = doc.select(".module-play-list[data-id='" + tab.attr("data-id") + "'] a");
                for (Element ep : eps) {
                    String epName = ep.text().trim();
                    String epUrl = ep.attr("href");
                    if (epName.isEmpty() || epUrl.isEmpty()) continue;
                    if (sb.length() > 0) sb.append("#");
                    sb.append(epName).append("$").append(fixUrl(epUrl));
                }
                urlList.add(sb.toString());
            }

            if (!fromList.isEmpty()) {
                vodPlayFrom = String.join("$$$", fromList);
                vodPlayUrl = String.join("$$$", urlList);
            }

            Vod vod = new Vod(ids.get(0), name, pic, remark);
            vod.setTypeName(typeName);
            vod.setVodYear(year);
            vod.setVodArea(area);
            vod.setVodActor(actor);
            vod.setVodDirector(director);
            vod.setVodContent(content);
            vod.setVodPlayFrom(vodPlayFrom);
            vod.setVodPlayUrl(vodPlayUrl);

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
        try {
            // 修正播放地址格式
            String playUrl;
            if (id.startsWith("http")) {
                playUrl = id;
            } else if (id.startsWith("/")) {
                playUrl = HOST + id;
            } else {
                playUrl = HOST + "/" + id;
            }

            HashMap<String, String> headers = getHeaders();
            // 很多站点强烈检查 Referer
            headers.put("Referer", HOST + "/");

            // 方案1：最常用 - 直接把播放页面交给壳子万能解析（parse=1）
            // 2025~2026年大量站点此方式仍然有效
            Result result = Result.get()
                    .url(playUrl)
                    .parse(1)
                    .header(headers);

            // 如果上面无效，可尝试下面更激进的方案（取消注释逐个测试）
            /*
            String html = AntiCrawlerEnhancer.get().enhancedGet(playUrl, headers);
            if (TextUtils.isEmpty(html)) {
                return Result.get().msg("无法获取播放页面").string();
            }

            Document doc = Jsoup.parse(html);

            // 尝试找 iframe （目前最常见的情况）
            Element iframe = doc.selectFirst(
                "iframe[src*=/player]," +
                "iframe[src*=/api]," +
                "iframe[src*=/parse]," +
                "iframe[src*=/jx]," +
                "iframe[src*=/video]," +
                "iframe#videoiframe," +
                "iframe.player-frame"
            );

            if (iframe != null) {
                String realUrl = fixUrl(iframe.attr("abs:src"));
                if (!TextUtils.isEmpty(realUrl)) {
                    return Result.get()
                            .url(realUrl)
                            .parse(0)
                            .header(headers)
                            .string();
                }
            }
            */

            // 兜底使用方案1
            return result.string();

        } catch (Exception e) {
            SpiderDebug.log("playerContent 异常 → " + id + " : " + e);
            return Result.get().msg("播放地址解析失败").string();
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
