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
import java.util.List;
import java.util.Map;

/**
 * EACG1动漫网站爬虫（2025年12月适配版）
 * 网址：https://eacg1.com
 * 
 * 当前特点：
 * - 列表：.fed-list-item 等选择器有效
 * - 分类：/vodclassification/XX.html
 * - 详情：voddetails-XXXX.html，有立即播放链接跳转子页
 * - 播放：无 player_aaaa JSON，直接嗅探详情页或播放子页（推荐嗅探详情页，大部分线路可抓）
 */
public class EACG1 extends Spider {

    private static final String siteUrl = "https://eacg1.com";
    private static final String searchUrl = siteUrl + "/vodsearch/";
    private static final String detailBase = "/voddetails-";

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl);
        return headers;
    }

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));

        // 分类（从导航或常见分类提取）
        classes.add(new Class("21", "日漫"));
        classes.add(new Class("20", "国语动漫"));
        classes.add(new Class("24", "劇場"));
        classes.add(new Class("50", "女频"));
        classes.add(new Class("43", "日韩剧"));
        classes.add(new Class("33", "高清原碟"));

        // 首页推荐列表
        Elements items = doc.select(".fed-list-info .fed-list-item, .lpic li");
        for (Element item : items) {
            Element a = item.selectFirst("a.fed-list-pics, a");
            if (a == null) continue;
            String href = a.attr("href");
            if (!href.contains(detailBase)) continue;

            String id = href.replace(detailBase, "").replace(".html", "");
            String name = a.attr("title");
            if (name.isEmpty()) name = a.text().trim();

            String pic = a.attr("data-original");
            if (pic.isEmpty()) pic = a.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            String remark = "";
            Element remarkEl = item.selectFirst(".fed-list-remarks, .remark");
            if (remarkEl != null) remark = remarkEl.text().trim();

            list.add(new Vod(id, name, pic, remark));
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, Map<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "/vodclassification/" + tid + ".html";
        if (!pg.equals("1")) url = url.replace(".html", "-" + pg + ".html");

        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        Elements items = doc.select(".fed-list-info .fed-list-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.fed-list-pics");
            if (a == null) continue;

            String href = a.attr("href");
            String id = href.replace(detailBase, "").replace(".html", "");
            String name = a.attr("title");
            if (name.isEmpty()) name = item.selectFirst(".fed-list-title").text().trim();

            String pic = a.attr("data-original");
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            String remark = "";
            Element remarkEl = item.selectFirst(".fed-list-remarks");
            if (remarkEl != null) remark = remarkEl.text().trim();

            list.add(new Vod(id, name, pic, remark));
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = siteUrl + detailBase + id + ".html";
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(doc.selectFirst("h1, .fed-part-rows").text().trim());
        vod.setVodPic(doc.selectFirst(".fed-list-pics, img").attr("data-original"));
        vod.setVodYear(doc.selectFirst("p:contains(年份)").text().replace("年份：", "").trim());
        vod.setVodContent(doc.selectFirst(".fed-part-esan, .fed-tabs-boxs").text().trim());

        // 播放源（多线路）
        StringBuilder from = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();

        Elements tabs = doc.select(".fed-play-item .fed-drop-btns, .nav-tabs li");
        Elements panes = doc.select(".fed-play-item .fed-drop-boxs, .tab-content");

        for (int i = 0; i < Math.min(tabs.size(), panes.size()); i++) {
            String sourceName = tabs.get(i).text().trim();
            if (from.length() > 0) {
                from.append("$$$");
                playUrl.append("$$$");
            }
            from.append(sourceName);

            Elements eps = panes.get(i).select("a");
            StringBuilder epsSb = new StringBuilder();
            for (Element ep : eps) {
                if (epsSb.length() > 0) epsSb.append("#");
                epsSb.append(ep.text().trim()).append("$").append(ep.attr("href"));
            }
            playUrl.append(epsSb);
        }

        // 如果无线路，伪造单集（跳转播放子页）
        if (from.length() == 0) {
            from.append("EACG线路");
            playUrl.append("播放$").append(url);
        }

        vod.setVodPlayFrom(from.toString());
        vod.setVodPlayUrl(playUrl.toString());

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = id.startsWith("http") ? id : siteUrl + id;

        // 直接返回播放页（详情页或子页），启用嗅探 + header（当前网站线路多为m3u8，可嗅探）
        return Result.get().url(playUrl).parse(1).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = searchUrl + URLEncoder.encode(key, "UTF-8") + "-------------.html";
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        Elements items = doc.select(".fed-list-info .fed-list-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.fed-list-pics");
            if (a != null) {
                String href = a.attr("href");
                String id = href.replace(detailBase, "").replace(".html", "");
                String name = a.attr("title");
                if (name.isEmpty()) {
                    Element titleEl = item.selectFirst(".fed-list-title");
                    if (titleEl != null) name = titleEl.text().trim();
                }
                String pic = a.attr("data-original");
                if (!pic.startsWith("http")) pic = siteUrl + pic;

                String remark = "";
                Element remarkEl = item.selectFirst(".fed-list-remarks");
                if (remarkEl != null) remark = remarkEl.text().trim();

                if (name.contains(key)) {
                    list.add(new Vod(id, name, pic, remark));
                }
            }
        }

        return Result.string(list);
    }
}
