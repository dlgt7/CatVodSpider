package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EACG1动漫网站爬虫（2025年12月20日最终修复版）
 * 网址：https://eacg1.com
 * 
 * 修复点：
 * - categoryContent 参数改为 HashMap<String, String> extend（匹配Spider基类签名）
 * - 分类URL使用 /vodclassification/{tid}.html
 * - 播放使用嗅探（实测有效）
 */
public class EACG1 extends Spider {

    private static final String siteUrl = "https://eacg1.com";
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

        // 手动添加分类（网站导航稳定）
        classes.add(new Class("21", "日漫"));
        classes.add(new Class("20", "国语动漫"));
        classes.add(new Class("24", "劇場"));
        classes.add(new Class("50", "女频"));
        classes.add(new Class("43", "日韩剧"));
        classes.add(new Class("33", "高清原碟"));

        // 首页推荐
        Elements items = doc.select(".fed-list-info .fed-list-item, .lpic li, .module-items .module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;

            String href = a.attr("href");
            if (!href.contains(detailBase)) continue;

            String id = href.replace(detailBase, "").replace(".html", "");
            String name = a.attr("title");
            if (TextUtils.isEmpty(name)) name = a.text().trim();

            String pic = a.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = a.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            String remark = "";
            Element remarkEl = item.selectFirst(".fed-list-remarks, .module-item-note");
            if (remarkEl != null) remark = remarkEl.text().trim();

            list.add(new Vod(id, name, pic, remark));
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "/vodclassification/" + tid + ".html";
        if (!pg.equals("1")) {
            url = siteUrl + "/vodclassification/" + tid + "-" + pg + ".html";
        }

        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        Elements items = doc.select(".fed-list-info .fed-list-item, .module-items .module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.fed-list-pics, a.module-item-cover");
            if (a == null) continue;

            String href = a.attr("href");
            String id = href.replace(detailBase, "").replace(".html", "");
            String name = a.attr("title");
            if (TextUtils.isEmpty(name)) name = item.selectFirst(".fed-list-title, .module-item-title").text().trim();

            String pic = a.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = a.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            String remark = "";
            Element remarkEl = item.selectFirst(".fed-list-remarks, .module-item-note");
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

        Element titleEl = doc.selectFirst("h1, .module-item-title");
        vod.setVodName(titleEl != null ? titleEl.text().trim() : "");

        String pic = doc.selectFirst(".fed-list-pics, .module-item-pic img").attr("data-original");
        if (!pic.startsWith("http")) pic = siteUrl + pic;
        vod.setVodPic(pic);

        Element yearEl = doc.selectFirst("text:contains(年份)");
        if (yearEl != null) vod.setVodYear(yearEl.text().replace("年份：", "").trim());

        Element contentEl = doc.selectFirst(".module-info-introduction-content, .fed-tabs-boxs");
        if (contentEl != null) vod.setVodContent(contentEl.text().trim());

        // 简单伪造播放源（实际靠嗅探）
        vod.setVodPlayFrom("EACG线路");
        vod.setVodPlayUrl("播放$" + url);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = id.startsWith("http") ? id : siteUrl + id;

        // 启用嗅探 + header（当前网站大部分线路可直接嗅探m3u8）
        return Result.get().url(playUrl).parse(1).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "/vodsearch/" + key + "-------------.html";
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        Elements items = doc.select(".fed-list-info .fed-list-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.fed-list-pics");
            if (a != null) {
                String href = a.attr("href");
                String id = href.replace(detailBase, "").replace(".html", "");
                String name = a.attr("title");
                if (TextUtils.isEmpty(name)) name = item.selectFirst(".fed-list-title").text().trim();

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
