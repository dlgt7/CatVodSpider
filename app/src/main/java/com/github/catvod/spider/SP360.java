package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 去看吧动漫网(Qkan8)爬虫 - 2025年更新版
 * 当前有效域名：https://www.qkan8.com (或通过extend参数配置)
 * 已适配当前Fedora模板结构，优化播放解析（直接提取m3u8直链）
 */
public class Qkan8 extends Spider {

    private static String siteUrl = "https://www.qkan8.com";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        return headers;
    }

    @Override
    public void init(Context context, String extend) {
        if (extend != null && !extend.isEmpty()) {
            siteUrl = extend.endsWith("/") ? extend.substring(0, extend.length() - 1) : extend;
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();

        // 动态获取分类（优先从导航）
        String homeUrl = siteUrl + "/";
        String html = OkHttp.string(homeUrl, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements navItems = doc.select(".fed-menu-info li a, .fed-pops-list li a");
        for (Element item : navItems) {
            String href = item.attr("href");
            String name = item.text().trim();
            if (!href.contains("/type/id/") || name.isEmpty() || name.equals("首页")) continue;

            Pattern p = Pattern.compile("/type/id/(\\d+)");
            Matcher m = p.matcher(href);
            if (m.find()) {
                String tid = m.group(1);
                classes.add(new Class(tid, name));
            }
        }

        // 兜底常用分类（防止导航变化）
        if (classes.isEmpty()) {
            classes.add(new Class("33", "高清原碟"));
            classes.add(new Class("21", "日漫"));
            classes.add(new Class("20", "国语动漫"));
            classes.add(new Class("24", "剧场"));
        }

        // 首页推荐视频（可选，可扩展为hot列表）
        List<Vod> vods = new ArrayList<>();
        // 这里可添加首页推荐解析，暂略

        return Result.string(classes, vods, new LinkedHashMap<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        String url = siteUrl + "/index.php/vod/type/id/" + tid + (pg.equals("1") ? "" : "/page/" + pg) + ".html";
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements items = doc.select(".fed-list-item");
        for (Element item : items) {
            Element a = item.selectFirst(".fed-list-title a");
            if (a == null) continue;
            String vodId = a.attr("href");
            String name = a.text().trim();
            String pic = item.selectFirst(".fed-list-pics").attr("data-original");
            String remark = item.selectFirst(".fed-list-remarks").text().trim();

            if (pic.startsWith("//")) pic = "https:" + pic;
            else if (pic.startsWith("/")) pic = siteUrl + pic;

            list.add(new Vod(vodId, name, pic, remark));
        }

        // 分页
        int page = Integer.parseInt(pg);
        int pageCount = page;
        Element pageInfo = doc.selectFirst(".fed-page-info");
        if (pageInfo != null) {
            Elements links = pageInfo.select("a");
            for (Element link : links) {
                String href = link.attr("href");
                Matcher m = Pattern.compile("/page/(\\d+)").matcher(href);
                if (m.find()) {
                    int pNum = Integer.parseInt(m.group(1));
                    if (pNum > pageCount) pageCount = pNum;
                }
            }
        }

        return Result.get().page(page, pageCount, 24, list.size()).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String url = siteUrl + vodId;
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodPic(doc.selectFirst(".fed-list-pics").attr("data-original"));
        vod.setVodName(doc.selectFirst("h1.fed-part-eone").text().trim());
        vod.setVodRemarks(doc.selectFirst(".fed-list-remarks").text().trim());
        vod.setVodActor(doc.selectFirst("li:contains(声优)").text().replace("声优：", "").trim());
        vod.setVodContent(doc.selectFirst(".fed-part-both").text().trim());

        // 播放源与剧集
        LinkedHashMap<String, List<String>> playMap = new LinkedHashMap<>();
        Elements sourceTabs = doc.select(".fed-drop-btns a");
        Elements playItems = doc.select(".fed-play-item");

        for (int i = 0; i < sourceTabs.size() && i < playItems.size(); i++) {
            String from = sourceTabs.get(i).text().trim();
            if (from.contains("EDD")) from = "EDD"; // 主线路
            else if (from.contains("极速")) from = "极速在线";

            List<String> urls = new ArrayList<>();
            Elements eps = playItems.get(i).select("a");
            for (Element ep : eps) {
                String epName = ep.text().trim();
                String epUrl = ep.attr("href");
                urls.add(epName + "$" + epUrl);
            }
            playMap.put(from, urls);
        }

        // 构建播放字符串
        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();
        for (String key : playMap.keySet()) {
            if (playFrom.length() > 0) playFrom.append("$$$");
            playFrom.append(key);
            if (playUrl.length() > 0) playUrl.append("$$$");
            playUrl.append(String.join("#", playMap.get(key)));
        }

        vod.setVodPlayFrom(playFrom.toString());
        vod.setVodPlayUrl(playUrl.toString());

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id 为 /index.php/vod/play/id/9205/sid/1/nid/1.html
        String playUrl = siteUrl + id;
        String html = OkHttp.string(playUrl, getHeaders());
        Document doc = Jsoup.parse(html);

        // 提取 iframe 中的 data-play（base64编码的m3u8）
        Element iframe = doc.selectFirst("#fed-play-iframe");
        if (iframe != null) {
            String dataPlay = iframe.attr("data-play");
            if (!dataPlay.isEmpty()) {
                try {
                    // data-play 是 base64 编码的 m3u8 地址（如 NGaaHR0cHM6Ly92aXAuZHl0dC1ob3QuY29tLz... ）
                    String realUrl = new String(android.util.Base64.decode(dataPlay, android.util.Base64.DEFAULT), "UTF-8");
                    return Result.get().url(realUrl).parse(0).header(getHeaders()).string();
                } catch (Exception ignored) {}
            }
        }

        // 兜底：返回播放页，让客户端嗅探（多数情况下也能播放）
        return Result.get().url(playUrl).parse(1).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();
        String url = siteUrl + "/index.php/vod/search/page/" + pg + "/wd/" + URLEncoder.encode(key, "UTF-8") + ".html";
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements items = doc.select(".fed-list-item");
        for (Element item : items) {
            Element a = item.selectFirst(".fed-list-title a");
            if (a == null) continue;
            String vodId = a.attr("href");
            String name = a.text().trim();
            String pic = item.selectFirst(".fed-list-pics").attr("data-original");
            String remark = item.selectFirst(".fed-list-remarks").text().trim();

            if (pic.startsWith("//")) pic = "https:" + pic;
            else if (pic.startsWith("/")) pic = siteUrl + pic;

            list.add(new Vod(vodId, name, pic, remark));
        }

        int page = Integer.parseInt(pg);
        int pageCount = page;
        Element pageInfo = doc.selectFirst(".fed-page-info");
        if (pageInfo != null) {
            Elements links = pageInfo.select("a");
            for (Element link : links) {
                Matcher m = Pattern.compile("/page/(\\d+)").matcher(link.attr("href"));
                if (m.find()) {
                    int pNum = Integer.parseInt(m.group(1));
                    if (pNum > pageCount) pageCount = pNum;
                }
            }
        }

        return Result.get().page(page, pageCount, 24, list.size()).vod(list).string();
    }
}
