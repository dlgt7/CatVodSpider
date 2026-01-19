package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;
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

public class Hanju99 extends Spider {

    private static final String SITE_URL = "https://www.99hanju.top";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
        headers.put("Referer", SITE_URL + "/");
        return headers;
    }

    private String fetchPic(String picUrl) {
        if (picUrl == null || picUrl.isEmpty() || picUrl.equals("/") || picUrl.contains("common_pic_v.png")) {
            return "https://via.placeholder.com/300x450?text=No+Image"; // 占位图，避免空导致不显示
        }
        // 强制HTTPS（站点图片通常支持）
        if (picUrl.startsWith("http://")) {
            picUrl = "https://" + picUrl.substring(7);
        }
        return picUrl;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONArray classes = new JSONArray();
        String[] typeIds = {"movie", "tv", "cartoon", "variety", "shorts"};
        String[] typeNames = {"电影", "电视剧", "动漫", "综艺", "短剧"};
        for (int i = 0; i < typeIds.length; i++) {
            JSONObject cls = new JSONObject();
            cls.put("type_id", typeIds[i]);
            cls.put("type_name", typeNames[i]);
            classes.put(cls);
        }

        JSONObject result = new JSONObject();
        result.put("class", classes);
        result.put("filters", new JSONObject());

        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = pg.equals("1") ? SITE_URL + "/" + tid + "/" : SITE_URL + "/" + tid + "/index_" + pg + ".html";

        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        JSONArray list = new JSONArray();
        Elements items = doc.select(".stui-vodlist__box, ul.stui-vodlist li");

        for (Element item : items) {
            Element a = item.selectFirst("a.stui-vodlist__thumb");
            if (a == null) continue;

            String pic = a.attr("data-original");
            if (pic.isEmpty() || pic.equals("/") || pic.contains("common_pic_v.png")) {
                Element img = a.selectFirst("img");
                if (img != null) pic = img.attr("src");
            }
            if (!pic.startsWith("http")) pic = SITE_URL + pic;

            pic = fetchPic(pic);

            String title = a.attr("title");
            if (title.isEmpty()) {
                Element h4 = a.selectFirst("h4");
                if (h4 != null) title = h4.text();
            }

            String href = a.attr("href");
            String vid = href.startsWith("http") ? href.substring(SITE_URL.length()) : href;
            if (!vid.startsWith("/")) vid = "/" + vid;

            String remark = "";
            Element remarkEl = a.selectFirst("span.pic-text, span.pic-tag-text");
            if (remarkEl != null) remark = remarkEl.text();

            JSONObject vod = new JSONObject();
            vod.put("vod_id", vid);
            vod.put("vod_name", title.trim());
            vod.put("vod_pic", pic);
            vod.put("vod_remarks", remark.trim());
            list.put(vod);
        }

        int page = Integer.parseInt(pg);
        int pageCount = page + 10;
        Element lastEl = doc.selectFirst("ul.stui-page li a:containsOwn(尾页)");
        if (lastEl != null) {
            String lastHref = lastEl.attr("href");
            Matcher m = Pattern.compile("index_(\\d+)").matcher(lastHref);
            if (m.find()) {
                pageCount = Integer.parseInt(m.group(1));
            }
        }

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", pageCount);
        result.put("limit", list.length());
        result.put("total", Integer.MAX_VALUE);
        result.put("list", list);

        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vid = ids.get(0);
        String url = SITE_URL + vid;

        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        JSONObject vod = new JSONObject();

        String pic = "";
        Element picEl = doc.selectFirst(".stui-content__thumb img, .lazyload");
        if (picEl != null) {
            pic = picEl.attr("data-original");
            if (pic.isEmpty() || pic.contains("common_pic_v.png")) {
                pic = picEl.attr("src");
            }
            if (!pic.startsWith("http")) pic = SITE_URL + pic;
        }

        pic = fetchPic(pic);

        String title = "";
        Element titleEl = doc.selectFirst("h1.title");
        if (titleEl != null) title = titleEl.ownText().trim();

        String typeName = "";
        Element typeP = doc.selectFirst("p.data:contains(类型)");
        if (typeP != null) {
            Element typeA = typeP.selectFirst("a");
            if (typeA != null) typeName = typeA.text();
        }

        String actor = "";
        Elements actorAs = doc.select("p.data a[href^=/actor/]");
        for (Element a : actorAs) {
            if (!actor.isEmpty()) actor += " ";
            actor += a.text();
        }

        String content = "";
        Element descEl = doc.selectFirst(".detail-content, .stui-content__desc, .data[itemprop=description]");
        if (descEl != null) content = descEl.text().trim();

        vod.put("vod_id", vid);
        vod.put("vod_name", title);
        vod.put("vod_pic", pic);
        vod.put("type_name", typeName);
        vod.put("vod_actor", actor.trim());
        vod.put("vod_director", "");
        vod.put("vod_year", "");
        vod.put("vod_content", content);

        LinkedHashMap<String, List<String>> playMap = new LinkedHashMap<>();

        List<String> playUrls = new ArrayList<>();
        Elements uls = doc.select("ul.stui-content__playlist");
        if (!uls.isEmpty()) {
            Elements lis = uls.first().select("li");
            for (Element li : lis) {
                Element a = li.selectFirst("a");
                if (a == null) continue;
                String ep = a.text().trim();
                String playHref = a.attr("href");
                if (!playHref.startsWith("http")) playHref = SITE_URL + playHref;
                playUrls.add(ep + "$" + playHref);
            }
        } else {
            Element btn = doc.selectFirst("a.btn-primary[href^=/play/]");
            if (btn != null) {
                String playHref = btn.attr("href");
                if (!playHref.startsWith("http")) playHref = SITE_URL + playHref;
                playUrls.add("正片$" + playHref);
            }
        }

        if (!playUrls.isEmpty()) {
            playMap.put("默认", playUrls);
        }

        vod.put("vod_play_from", String.join("$$$", playMap.keySet()));
        vod.put("vod_play_url", String.join("$$$", playMap.values().stream()
                .map(urls -> String.join("#", urls))
                .toArray(String[]::new)));

        JSONArray list = new JSONArray();
        list.put(vod);

        JSONObject result = new JSONObject();
        result.put("list", list);

        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("playUrl", "");
        result.put("url", id);
        result.put("header", new JSONObject(getHeaders()).toString());

        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String searchUrl = SITE_URL + "/e/search/index.php";

        HashMap<String, String> params = new HashMap<>();
        params.put("keyboard", URLEncoder.encode(key, "UTF-8"));
        params.put("show", "title");
        params.put("tempid", "1");
        params.put("tbname", "news");
        params.put("mid", "1");
        params.put("dopost", "search");

        String html = OkHttp.post(searchUrl, params, getHeaders()).getBody();
        Document doc = Jsoup.parse(html);

        JSONArray list = new JSONArray();
        Elements items = doc.select(".stui-vodlist__box, ul.stui-vodlist li");

        for (Element item : items) {
            Element a = item.selectFirst("a.stui-vodlist__thumb");
            if (a == null) continue;

            String pic = a.attr("data-original");
            if (pic.isEmpty() || pic.contains("common_pic_v.png")) {
                Element img = a.selectFirst("img");
                if (img != null) pic = img.attr("src");
            }
            if (!pic.startsWith("http")) pic = SITE_URL + pic;

            pic = fetchPic(pic);

            String title = a.attr("title");
            if (title.isEmpty()) {
                Element h4 = a.selectFirst("h4");
                if (h4 != null) title = h4.text();
            }

            String href = a.attr("href");
            String vid = href.startsWith("http") ? href.substring(SITE_URL.length()) : href;
            if (!vid.startsWith("/")) vid = "/" + vid;

            String remark = "";
            Element remarkEl = a.selectFirst("span.pic-text, span.pic-tag-text");
            if (remarkEl != null) remark = remarkEl.text();

            JSONObject vod = new JSONObject();
            vod.put("vod_id", vid);
            vod.put("vod_name", title.trim());
            vod.put("vod_pic", pic);
            vod.put("vod_remarks", remark.trim());
            list.put(vod);
        }

        JSONObject result = new JSONObject();
        result.put("list", list);

        return result.toString();
    }
}
