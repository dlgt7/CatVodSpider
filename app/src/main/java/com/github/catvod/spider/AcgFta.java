package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;  // 关键：正确导入父类
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ACG饭团爬虫（2025年12月20日最新适配版）
 * 网站: https://acgfta.com/
 * 
 * 当前网站特点：
 * - 列表页（首页、分类、搜索）：纯文本格式，如 [标题](/anime/xxxx.html) 更新至xx集
 * - 无图片、无卡片、无复杂HTML结构
 * - 详情页：极简，仅标题、年份等元信息，无封面、无播放源tab、无集数按钮
 * - 播放：实际视频可能嵌入剧集页或需嗅探
 */
public class AcgFta extends Spider {

    private static String siteUrl = "https://acgfta.com";
    private static final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    // 正则：匹配 [标题](/anime/xxxx.html)
    private static final Pattern ITEM_PATTERN = Pattern.compile("\\[(.*?)\\]\\(/anime/(\\d+\\.html)\\)(?:\\s*(.*))?");
    // 提取备注的备用正则（更新至、全、已完结等）
    private static final Pattern REMARK_PATTERN = Pattern.compile("(更新至第?\\d+集|全\\d+集|更新第?\\d+集|已完结|.*)");

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
            siteUrl = extend.trim();
            if (!siteUrl.endsWith("/")) siteUrl += "/";
        }
    }

    private List<Vod> parseTextList(String text) {
        List<Vod> list = new ArrayList<>();
        Matcher matcher = ITEM_PATTERN.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String vodId = "/anime/" + matcher.group(2);
            String rawRemark = matcher.group(3);
            String remark = (rawRemark != null) ? rawRemark.trim() : "";

            if (remark.isEmpty()) {
                // 备用提取备注
                Matcher rem = REMARK_PATTERN.matcher(text.substring(matcher.end()));
                if (rem.find()) remark = rem.group(1).trim();
            }

            if (!name.isEmpty()) {
                list.add(new Vod(vodId, name, "", remark)); // 无图片
            }
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();

        classes.add(new Class("ft/recent.html", "最近更新"));
        classes.add(new Class("ft/leaderboard.html", "榜单"));
        classes.add(new Class("ft/top-movie.html", "剧场版"));
        classes.add(new Class("ft/archive.html", "新番归档"));

        String html = OkHttp.string(siteUrl, getHeaders());
        Document doc = Jsoup.parse(html);
        String text = doc.body().text(); // 纯文本解析

        list.addAll(parseTextList(text));

        // fallback: 如果正则没抓到，用a标签
        if (list.isEmpty()) {
            Elements links = doc.select("a[href*=/anime/]");
            for (Element a : links) {
                String name = a.text().trim();
                if (!name.isEmpty() && !name.contains("饭团动漫")) {
                    list.add(new Vod(a.attr("href"), name, "", ""));
                }
            }
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + tid;
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);
        String text = doc.body().text();

        list.addAll(parseTextList(text));

        // fallback 同首页
        if (list.isEmpty()) {
            Elements links = doc.select("a[href*=/anime/]");
            for (Element a : links) {
                String name = a.text().trim();
                Element parent = a.parent();
                String remark = parent != null ? parent.ownText().trim() : "";
                if (!name.isEmpty()) {
                    list.add(new Vod(a.attr("href"), name, "", remark));
                }
            }
        }

        return Result.string(list);
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

        // 标题（h1 或 title）
        String name = doc.selectFirst("h1") != null ? doc.selectFirst("h1").text().trim() : doc.title().replace("- 在线观看 - 饭团动漫", "").trim();
        vod.setVodName(name);

        vod.setVodPic(""); // 无图片
        vod.setVodContent(""); // 简介极少或无

        // 年份等（从p标签）
        Elements ps = doc.select("p");
        for (Element p : ps) {
            String t = p.text();
            if (t.startsWith("年份：")) vod.setVodYear(t.replace("年份：", "").trim());
        }

        // 播放：由于无明确集数，直接设为单集或伪造
        vod.setVodPlayFrom("饭团播放");
        vod.setVodPlayUrl("播放$" + url);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = id.startsWith("http") ? id : siteUrl + id;

        // 直接返回剧集页URL，启用嗅探（TVBox会自动找video/m3u8等）
        return Result.get().url(url).parse(1).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "search.html?wd=" + URLEncoder.encode(key, "UTF-8");
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);
        String text = doc.body().text();

        list.addAll(parseTextList(text));

        if (list.isEmpty()) {
            Elements links = doc.select("a[href*=/anime/]");
            for (Element a : links) {
                String name = a.text().trim();
                if (name.contains(key)) {
                    list.add(new Vod(a.attr("href"), name, "", ""));
                }
            }
        }

        return Result.string(list);
    }
}
