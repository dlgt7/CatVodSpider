package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * ACG饭团爬虫（2025年12月优化版）
 * 网站地址: https://acgfta.com/
 * 
 * 当前网站结构变化：
 * - 首页：按星期分组的卡片列表（有图片、标题、更新备注）
 * - 分类页（如最近更新、榜单、剧场版、归档）：纯文本列表，无图片、无卡片结构
 * - 详情页：结构基本稳定，有封面、简介、多线路播放列表
 * - 搜索页：类似分类页，纯文本列表（可能有少量卡片）
 * 
 * 本版优化：优先使用卡片选择器，备选文本列表解析（正则提取链接、标题、备注）
 * 图片优先data-src，兼容lozad懒加载
 */
public class AcgFta extends Spider {

    private static String siteUrl = "https://acgfta.com";
    private static final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Referer", siteUrl);
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend)) {
            siteUrl = extend;
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();

        // 分类（保持原样，网站导航一致）
        classes.add(new Class("ft/recent.html", "最近更新"));
        classes.add(new Class("ft/leaderboard.html", "榜单"));
        classes.add(new Class("ft/top-movie.html", "剧场版"));
        classes.add(new Class("ft/archive.html", "新番归档"));

        // 首页推荐：当前仍有卡片结构 + 图片
        String html = OkHttp.string(siteUrl, getHeaders());
        Document doc = Jsoup.parse(html);

        // 优先卡片解析（首页有效）
        Elements cards = doc.select(".anime-card");
        for (Element card : cards) {
            Element link = card.selectFirst("a.stretched-link");
            Element img = card.selectFirst("img.anime-cover");
            Element remarkEl = card.selectFirst(".anime-update-info p");

            if (link != null) {
                String vodId = link.attr("href");
                String name = link.text().trim();
                String pic = img != null ? img.attr("data-src") : "";
                if (TextUtils.isEmpty(pic)) pic = img != null ? img.attr("src") : "";
                pic = formatPic(pic);

                String remark = remarkEl != null ? remarkEl.text().trim() : "";

                list.add(new Vod(vodId, name, pic, remark));
            }
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "/" + tid;
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        // 优先尝试卡片结构（部分分类可能仍有）
        Elements cards = doc.select(".anime-card");
        if (!cards.isEmpty()) {
            for (Element card : cards) {
                addVodFromCard(card, list);
            }
        } else {
            // 分类页多为纯文本列表：格式如 "[标题](/anime/xxxx.html) 更新至xx集"
            // 或直接 "标题 更新至xx集"（无链接括号）
            String bodyText = doc.body().text();
            String[] lines = bodyText.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 提取链接（正则匹配 /anime/xxxx.html）
                String vodId = "";
                String name = line;
                String remark = "";

                int linkStart = line.indexOf("(/anime/");
                if (linkStart != -1) {
                    int linkEnd = line.indexOf(")", linkStart);
                    if (linkEnd != -1) {
                        vodId = line.substring(linkStart + 1, linkEnd);
                        name = line.substring(0, linkStart).replace("[", "").trim();
                        remark = line.substring(linkEnd + 1).trim();
                    }
                } else {
                    // 无链接格式，直接拆分标题和备注
                    if (line.contains("更新至") || line.contains("全") || line.contains("集")) {
                        // 简单拆分，最后部分为备注
                        int lastSpace = Math.max(line.lastIndexOf("更新至"), line.lastIndexOf("全"));
                        if (lastSpace > 0) {
                            remark = line.substring(lastSpace).trim();
                            name = line.substring(0, lastSpace).trim();
                        }
                    }
                    // 榜单有 #1 标题
                    if (line.startsWith("#")) {
                        line = line.replaceFirst("#\\d+\\s*", "").trim();
                        name = line;
                    }
                }

                if (!name.isEmpty()) {
                    // 对于纯文本列表，尝试从正文找对应链接（不完美，但能抓大部分）
                    Elements links = doc.select("a[href*=/anime/]");
                    for (Element a : links) {
                        if (a.text().contains(name)) {
                            vodId = a.attr("href");
                            break;
                        }
                    }
                    list.add(new Vod(vodId, name, "", remark)); // 无图片
                }
            }
        }

        return Result.string(list);
    }

    private void addVodFromCard(Element card, List<Vod> list) {
        Element link = card.selectFirst("a.stretched-link, a");
        Element img = card.selectFirst("img.anime-cover, img");
        Element remarkEl = card.selectFirst(".anime-update-info p, .remark");

        if (link != null) {
            String vodId = link.attr("href");
            String name = link.text().trim();
            String pic = img != null ? img.attr("data-src") : "";
            if (TextUtils.isEmpty(pic)) pic = img != null ? img.attr("src") : "";
            pic = formatPic(pic);

            String remark = remarkEl != null ? remarkEl.text().trim() : "";

            list.add(new Vod(vodId, name, pic, remark));
        }
    }

    private String formatPic(String pic) {
        if (pic == null) return "";
        if (pic.startsWith("//")) {
            pic = "https:" + pic;
        } else if (pic.startsWith("/")) {
            pic = siteUrl + pic;
        } else if (!pic.startsWith("http") && !pic.isEmpty()) {
            pic = siteUrl + "/" + pic;
        }
        return pic;
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids.isEmpty()) return "";

        String vodId = ids.get(0);
        String url = vodId.startsWith("http") ? vodId : siteUrl + vodId;
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(vodId);

        // 标题
        String name = doc.selectFirst("h6.mb-0.semibold, h1, .title") != null ?
                doc.selectFirst("h6.mb-0.semibold, h1, .title").text().trim() : "";
        vod.setVodName(name);

        // 封面（详情页仍有）
        String pic = doc.selectFirst("img.anime-cover.lozad, .ratio img") != null ?
                doc.selectFirst("img.anime-cover.lozad, .ratio img").attr("data-src") : "";
        if (TextUtils.isEmpty(pic)) {
            pic = doc.selectFirst("img.anime-cover.lozad, .ratio img") != null ?
                    doc.selectFirst("img.anime-cover.lozad, .ratio img").attr("src") : "";
        }
        vod.setVodPic(formatPic(pic));

        // 年份
        Element yearEl = doc.selectFirst(".detail-anime-info p:contains(年份)");
        String year = yearEl != null ? yearEl.text().replace("年份：", "").trim() : "";
        vod.setVodYear(year);

        // 简介
        String content = doc.selectFirst(".detail-anime-intro, .description") != null ?
                doc.selectFirst(".detail-anime-intro, .description").text().trim() : "";
        vod.setVodContent(content);

        // 播放源与集数
        Elements sources = doc.select(".nav-pills .nav-link, .nav-item button");
        Elements panes = doc.select(".tab-content .tab-pane");

        StringBuilder from = new StringBuilder();
        StringBuilder urlSb = new StringBuilder();

        for (int i = 0; i < Math.min(sources.size(), panes.size()); i++) {
            String sourceName = sources.get(i).text().trim();

            if (from.length() > 0) {
                from.append("$$$");
                urlSb.append("$$$");
            }
            from.append(sourceName);

            Element pane = panes.get(i);
            Elements eps = pane.select("a.btn-episode, a");
            StringBuilder epsSb = new StringBuilder();
            for (Element ep : eps) {
                String epName = ep.text().trim();
                String epUrl = ep.attr("href");

                if (!epUrl.startsWith("http")) {
                    epUrl = epUrl.startsWith("/") ? siteUrl + epUrl : siteUrl + "/" + epUrl;
                }

                if (epsSb.length() > 0) epsSb.append("#");
                epsSb.append(epName).append("$").append(epUrl);
            }
            urlSb.append(epsSb);
        }

        vod.setVodPlayFrom(from.toString());
        vod.setVodPlayUrl(urlSb.toString());

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = id.startsWith("http") ? id : siteUrl + (id.startsWith("/") ? id : "/" + id);
        // 直接返回播放页URL，由TVBox内置解析器或外部播放器处理
        return Result.get().parse().url(url).string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "/search.html?wd=" + URLEncoder.encode(key, "UTF-8");
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        // 搜索页可能混有卡片，也可能纯文本
        Elements cards = doc.select(".anime-card");
        if (!cards.isEmpty()) {
            for (Element card : cards) {
                addVodFromCard(card, list);
            }
        } else {
            //  fallback 纯文本解析，同分类页
            // （实际搜索页可能仍有少量卡片或链接，此处简化）
            Elements links = doc.select("a[href*=/anime/]");
            for (Element a : links) {
                String text = a.text().trim();
                if (text.contains(key)) {
                    list.add(new Vod(a.attr("href"), text, "", ""));
                }
            }
        }

        return Result.string(list);
    }
}