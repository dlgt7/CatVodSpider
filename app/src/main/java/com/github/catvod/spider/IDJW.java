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

/**
 * 短剧窝(IDJW)爬虫 - 2025年12月修复版
 * 网站: https://www.idjw.cc
 */
public class IDJW extends Spider {

    private static final String siteUrl = "https://www.idjw.cc";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl + "/");
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();

        String html = OkHttp.string(siteUrl + "/", getHeaders());
        Document doc = Jsoup.parse(html);

        // === 修复1：正确获取顶部导航分类 ===
        Elements navItems = doc.select("div.navbar-nav a.nav-link");
        for (Element item : navItems) {
            String href = item.attr("href");
            String name = item.text().trim();
            if (href.startsWith("/type/") && !name.isEmpty() && !name.contains("首页")) {
                String typeId = href.replace("/type/", "").replace(".html", "");
                classes.add(new Class(typeId, name));
            }
        }

        // === 修复2：获取首页推荐模块（多个模块合并）===
        Elements modules = doc.select("div.module");
        for (Element module : modules) {
            Elements items = module.select("div.module-items div.module-item");
            for (Element item : items) {
                Element a = item.selectFirst("a.module-item-cover");
                if (a == null) continue;

                String href = a.attr("href");
                String title = a.attr("title");
                String pic = a.selectFirst("img") != null ? a.selectFirst("img").attr("data-src") : "";
                if (pic.isEmpty()) pic = a.selectFirst("img").attr("src");
                if (pic.startsWith("//")) pic = "https:" + pic;
                if (pic.isEmpty()) continue;

                String remark = "";
                Element note = item.selectFirst("div.module-item-note");
                if (note != null) remark = note.text().trim();

                if (!href.isEmpty() && !title.isEmpty()) {
                    String vodId = href.replace("/vod/", "").replace(".html", "");
                    list.add(new Vod(vodId, title, pic, remark));
                }
            }
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "/type/" + tid + "-" + pg + ".html";
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements items = doc.select("div.module-items div.module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.module-item-cover");
            if (a == null) continue;

            String href = a.attr("href");
            String title = a.attr("title");
            String pic = a.selectFirst("img").attr("data-src");
            if (pic.isEmpty()) pic = a.selectFirst("img").attr("src");
            if (pic.startsWith("//")) pic = "https:" + pic;

            String remark = "";
            Element note = item.selectFirst("div.module-item-note");
            if (note != null) remark = note.text().trim();

            if (!href.isEmpty() && !title.isEmpty()) {
                String vodId = href.replace("/vod/", "").replace(".html", "");
                list.add(new Vod(vodId, title, pic, remark));
            }
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = siteUrl + "/vod/" + id + ".html";

        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Element titleEl = doc.selectFirst("h1.title");
        String vodName = titleEl != null ? titleEl.text().trim() : "";
        String pic = doc.selectFirst("a.module-item-cover img").attr("data-src");
        if (pic.startsWith("//")) pic = "https:" + pic;

        String typeName = "", year = "", area = "", actor = "", director = "", content = "";
        Elements infoItems = doc.select("div.module-info-item");
        for (Element item : infoItems) {
            String text = item.text();
            if (text.contains("类型：")) typeName = item.select("a").text();
            else if (text.contains("年份：")) year = item.ownText().replace("年份：", "").trim();
            else if (text.contains("地区：")) area = item.ownText().replace("地区：", "").trim();
            else if (text.contains("主演：")) actor = item.ownText().replace("主演：", "").trim();
            else if (text.contains("导演：")) director = item.ownText().replace("导演：", "").trim();
            else if (text.contains("简介：")) content = item.selectFirst("div.module-info-content").text();
        }

        // === 关键修复：提取播放列表 ===
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        Elements playTabs = doc.select("div.module-tab-item");
        Elements playLists = doc.select("div.module-play-list");

        for (int i = 0; i < playTabs.size() && i < playLists.size(); i++) {
            String fromName = playTabs.get(i).text().trim();
            if (fromName.isEmpty()) fromName = "线路" + (i + 1);

            List<Vod.VodPlayBuilder.PlayUrl> urls = new ArrayList<>();
            Elements aTags = playLists.get(i).select("a");
            for (Element a : aTags) {
                String name = a.text().trim();
                String playUrl = a.attr("href");
                if (!playUrl.startsWith("http")) {
                    playUrl = siteUrl + playUrl;
                }
                Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
                pu.name = name;
                pu.url = playUrl;
                urls.add(pu);
            }

            if (!urls.isEmpty()) {
                builder.append(fromName, urls);
            }
        }

        Vod.VodPlayBuilder.BuildResult result = builder.build();

        Vod vod = new Vod(id, vodName, pic);
        vod.setVodRemarks(year + " " + area);
        vod.setVodActor(actor);
        vod.setVodDirector(director);
        vod.setVodContent(content);
        vod.setVodPlayFrom(result.vodPlayFrom);
        vod.setVodPlayUrl(result.vodPlayUrl);

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "/search/" + URLEncoder.encode(key, "UTF-8") + "----------" + pg + "---.html";
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements items = doc.select("div.module-items div.module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.module-item-cover");
            if (a == null) continue;

            String href = a.attr("href");
            String title = a.attr("title");
            String pic = a.selectFirst("img").attr("data-src");
            if (pic.isEmpty()) pic = a.selectFirst("img").attr("src");
            if (pic.startsWith("//")) pic = "https:" + pic;

            String remark = "";
            Element note = item.selectFirst("div.module-item-note");
            if (note != null) remark = note.text().trim();

            if (!href.isEmpty() && !title.isEmpty()) {
                String vodId = href.replace("/vod/", "").replace(".html", "");
                list.add(new Vod(vodId, title, pic, remark));
            }
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id 是剧集播放页，如 /play/xxx.html
        String playUrl = siteUrl + id;

        String html = OkHttp.string(playUrl, getHeaders());
        Document doc = Jsoup.parse(html);

        // 提取真实播放地址（通常在 player_data 或 player_aaaa 中）
        String script = doc.select("script:contains(player_aaaa)").html();
        if (script.isEmpty()) {
            script = doc.select("script:contains(player_data)").html();
        }

        String realUrl = Util.getVar(script, "url");
        if (realUrl.isEmpty() || !realUrl.startsWith("http")) {
            return Result.get().url(playUrl).header(getHeaders()).parse(1).string();
        }

        return Result.get().url(realUrl).header(getHeaders()).string();
    }
}
