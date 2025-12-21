package com.github.catvod.spider;

import android.content.Context;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class Auete extends Spider {

    private static final String SITE_URL = "https://auete.top";  // 当前最新稳定域名（2025年12月）

    private final List<Class> classes = Arrays.asList(
            new Class("1", "电影"),
            new Class("2", "电视剧"),
            new Class("3", "综艺"),
            new Class("4", "动漫")
    );

    @Override
    public void init(Context context, String extend) throws Exception {
        // 无需额外初始化，直接留空即可（移除 super 调用以兼容旧版）
    }

    private HashMap<String, String> headers() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        String url = SITE_URL + "/";
        String html = OkHttp.string(url, headers());
        Document doc = Jsoup.parse(html);

        Elements items = doc.select("div.stui-vodlist__box, div.stui-vodlist li a.stui-vodlist__thumb");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String pic = a.selectFirst("img").attr("data-original");
            if (pic.isEmpty()) pic = a.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = SITE_URL + pic;

            String name = a.attr("title");
            if (name.isEmpty()) name = a.text();

            String href = a.attr("href");
            String id = href.replace("/detail/index.php?", "").replace(".html", "");

            String remarks = item.selectFirst("span.pic-tag, span.pic-text") != null ? item.selectFirst("span.pic-tag, span.pic-text").text() : "";

            list.add(new Vod(id, name, pic, remarks));
        }

        return Result.string(classes, list, new LinkedHashMap<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        String url = SITE_URL + "/list/index.php?" + tid + "-" + pg + ".html";
        String html = OkHttp.string(url, headers());
        Document doc = Jsoup.parse(html);

        Elements items = doc.select("div.stui-vodlist__box");
        for (Element item : items) {
            Element a = item.selectFirst("a.stui-vodlist__thumb");
            String pic = a.selectFirst("img").attr("data-original");
            if (pic.isEmpty()) pic = a.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = SITE_URL + pic;

            String name = a.attr("title");
            String href = a.attr("href");
            String id = href.replace("/detail/index.php?", "").replace(".html", "");

            String remarks = item.selectFirst("span.pic-tag, span.pic-text") != null ? item.selectFirst("span.pic-tag, span.pic-text").text() : "";

            list.add(new Vod(id, name, pic, remarks));
        }

        int page = Integer.parseInt(pg);
        int limit = 24;
        int total = page * limit + (list.size() > 0 ? limit : 0);

        return Result.get()
                .classes(classes)
                .vod(list)
                .page(page, page + 1, limit, total)
                .string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = SITE_URL + "/detail/index.php?" + id + ".html";
        String html = OkHttp.string(url, headers());
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(doc.selectFirst("h1.title").ownText().trim());
        
        Element thumb = doc.selectFirst("div.stui-content__thumb img");
        String pic = thumb.attr("data-original");
        if (pic.isEmpty()) pic = thumb.attr("src");
        if (!pic.startsWith("http")) pic = SITE_URL + pic;
        vod.setVodPic(pic);

        vod.setTypeName(doc.selectFirst("div.stui-content__detail p:contains(类型)").text().replace("类型：", "").trim());
        vod.setVodYear(doc.selectFirst("div.stui-content__detail p:contains(年份)").text().replace("年份：", "").trim());
        vod.setVodArea(doc.selectFirst("div.stui-content__detail p:contains(地区)").text().replace("地区：", "").trim());
        vod.setVodActor(doc.selectFirst("div.stui-content__detail p:contains(主演)").text().replace("主演：", "").trim());
        vod.setVodDirector(doc.selectFirst("div.stui-content__detail p:contains(导演)").text().replace("导演：", "").trim());
        vod.setVodContent(doc.selectFirst("div.stui-content__desc span.detail-content").text().trim());

        StringBuilder fromBuilder = new StringBuilder();
        StringBuilder urlBuilder = new StringBuilder();

        Elements tabs = doc.select("div.stui-pannel__head h3.title");
        Elements playlists = doc.select("ul.stui-content__playlist");

        for (int i = 0; i < tabs.size() && i < playlists.size(); i++) {
            String from = tabs.get(i).text().trim();
            if (fromBuilder.length() > 0) fromBuilder.append("$$$");
            fromBuilder.append(from);

            List<String> epList = new ArrayList<>();
            Elements eps = playlists.get(i).select("li a");
            for (Element ep : eps) {
                String epName = ep.text().trim();
                String epUrl = ep.attr("href");
                if (!epUrl.startsWith("http")) epUrl = SITE_URL + epUrl;
                epList.add(epName + "$" + epUrl);
            }
            if (urlBuilder.length() > 0) urlBuilder.append("$$$");
            urlBuilder.append(String.join("#", epList));
        }

        vod.setVodPlayFrom(fromBuilder.toString());
        vod.setVodPlayUrl(urlBuilder.toString());

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = id.startsWith("http") ? id : SITE_URL + id;

        // 兼容项目中 Result 的 jx() 无参数版本（开启内置解析）
        return Result.get().parse(1).jx().url(playUrl).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        try {
            String encodedKey = URLEncoder.encode(key, "UTF-8");
            String url = SITE_URL + "/search.php?searchword=" + encodedKey;
            String html = OkHttp.string(url, headers());
            if (html.contains("404") || html.isEmpty()) return Result.string(list);

            Document doc = Jsoup.parse(html);
            Elements items = doc.select("div.stui-vodlist__box");
            for (Element item : items) {
                Element a = item.selectFirst("a.stui-vodlist__thumb");
                String pic = a.selectFirst("img").attr("data-original");
                if (pic.isEmpty()) pic = a.selectFirst("img").attr("src");
                if (!pic.startsWith("http")) pic = SITE_URL + pic;

                String name = a.attr("title");
                String href = a.attr("href");
                String id = href.replace("/detail/index.php?", "").replace(".html", "");

                String remarks = item.selectFirst("span.pic-tag, span.pic-text") != null ? item.selectFirst("span.pic-tag, span.pic-text").text() : "";

                list.add(new Vod(id, name, pic, remarks));
            }
        } catch (Exception ignored) {}

        return Result.string(list);
    }
}
