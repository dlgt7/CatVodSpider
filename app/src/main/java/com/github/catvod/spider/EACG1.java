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

/**
 * EACG1动漫网站爬虫（2025年12月20日最终版 - 编译通过）
 * 网址：https://eacg1.com
 */
public class EACG1 extends Spider {

    private static final String siteUrl = "https://eacg1.com";
    private static final String detailBase = "/voddetails-";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl);
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {  // 必须 throws Exception
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));

        // 常见分类（手动维护，网站稳定）
        classes.add(new Class("21", "日漫"));
        classes.add(new Class("20", "国语动漫"));
        classes.add(new Class("24", "劇場"));
        classes.add(new Class("50", "女频"));
        classes.add(new Class("43", "日韩剧"));
        classes.add(new Class("33", "高清原碟"));

        Elements items = doc.select(".fed-list-info .fed-list-item, .module-items .module-item, .lpic li");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;

            String href = a.attr("href");
            if (!href.contains(detailBase)) continue;

            String id = href.replace(detailBase, "").replace(".html", "");
            String name = a.attr("title");
            if (TextUtils.isEmpty(name)) {
                Element titleEl = item.selectFirst(".fed-list-title, .module-item-title");
                if (titleEl != null) name = titleEl.text().trim();
            }

            String pic = a.attr("data-original");
            if (TextUtils.isEmpty(pic)) {
                Element img = a.selectFirst("img");
                if (img != null) pic = img.attr("src");
            }
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
        if (!"1".equals(pg)) {
            url = siteUrl + "/vodclassification/" + tid + "-" + pg + ".html";
        }

        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        Elements items = doc.select(".fed-list-info .fed-list-item, .module-items .module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.fed-list-pics, a.module-item-cover, a");
            if (a == null) continue;

            String href = a.attr("href");
            String id = href.replace(detailBase, "").replace(".html", "");
            String name = a.attr("title");
            if (TextUtils.isEmpty(name)) {
                Element titleEl = item.selectFirst(".fed-list-title, .module-item-title");
                if (titleEl != null) name = titleEl.text().trim();
            }

            String pic = a.attr("data-original");
            if (TextUtils.isEmpty(pic)) {
                Element img = a.selectFirst("img");
                if (img != null) pic = img.attr("src");
            }
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

        Element titleEl = doc.selectFirst("h1, .module-item-title, .fed-deta-info h1");
        vod.setVodName(titleEl != null ? titleEl.text().trim() : "未知标题");

        String pic = doc.selectFirst("img.fed-list-pics, .module-item-pic img").attr("data-original");
        if (TextUtils.isEmpty(pic)) pic = doc.selectFirst("img").attr("src");
        if (!pic.startsWith("http")) pic = siteUrl + pic;
        vod.setVodPic(pic);

        // 简介、年份等可选
        Element contentEl = doc.selectFirst(".module-info-introduction-content, .fed-tabs-boxs");
        if (contentEl != null) vod.setVodContent(contentEl.text().trim());

        // 简单处理播放（实际靠嗅探）
        vod.setVodPlayFrom("EACG线路");
        vod.setVodPlayUrl("立即播放$" + url);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = id.startsWith("http") ? id : siteUrl + id;

        // 启用嗅探 + 带header（实测2025年12月多数线路可直接播放）
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
            if (a == null) continue;

            String href = a.attr("href");
            String id = href.replace(detailBase, "").replace(".html", "");
            String name = a.attr("title");
            if (TextUtils.isEmpty(name)) {
                Element titleEl = item.selectFirst(".fed-list-title");
                if (titleEl != null) name = titleEl.text().trim();
            }

            String pic = a.attr("data-original");
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            String remark = "";
            Element remarkEl = item.selectFirst(".fed-list-remarks");
            if (remarkEl != null) remark = remarkEl.text().trim();

            if (!TextUtils.isEmpty(name) && name.contains(key)) {
                list.add(new Vod(id, name, pic, remark));
            }
        }

        return Result.string(list);
    }
}
