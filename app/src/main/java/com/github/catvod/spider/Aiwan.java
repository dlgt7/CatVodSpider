package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class Aiwan extends Spider {

    private static final String SITE_URL = "https://www.22a5.com";
    private static final String HEADERS_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", HEADERS_UA);
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();
        LinkedHashMap<String, List<com.github.catvod.bean.Filter>> filters = new LinkedHashMap<>();

        String html = OkHttp.string(SITE_URL, getHeaders());
        Document doc = Jsoup.parse(html);

        // 分类从导航栏提取
        Elements navItems = doc.select("div.fed-navbar ul.fed-drop-info li a.fed-visible");
        for (Element item : navItems) {
            String name = item.selectFirst("span").text().trim();
            if (name.isEmpty()) continue;
            String href = item.attr("href");
            if (href.startsWith("/")) href = href.substring(1);
            String tid = href.replace(".html", "").replace("/", "");
            classes.add(new Class(tid, name));
        }

        // 首页推荐列表
        Elements items = doc.select("ul.fed-list-info li");
        for (Element item : items) {
            Element a = item.selectFirst("a.fed-list-pics");
            if (a == null) continue;
            String pic = a.selectFirst("img").attr("data-original");
            if (!pic.startsWith("http")) pic = SITE_URL + pic;
            String name = a.attr("title");
            String href = a.attr("href");
            String id = href.startsWith("/") ? href.substring(1).replace(".html", "") : href.replace(".html", "");
            String remarks = item.selectFirst("span.fed-list-remarks").text();
            list.add(new Vod(id, name, pic, remarks));
        }

        return Result.string(classes, list, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        String url = SITE_URL + "/" + tid;
        if (!pg.equals("1")) url += "-pg-" + pg;
        url += ".html";

        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements items = doc.select("ul.fed-list-info li");
        for (Element item : items) {
            Element a = item.selectFirst("a.fed-list-pics");
            if (a == null) continue;
            String pic = a.selectFirst("img").attr("data-original");
            if (!pic.startsWith("http")) pic = SITE_URL + pic;
            String name = a.attr("title");
            String href = a.attr("href");
            String id = href.startsWith("/") ? href.substring(1).replace(".html", "") : href.replace(".html", "");
            String remarks = item.selectFirst("span.fed-list-remarks").text();
            list.add(new Vod(id, name, pic, remarks));
        }

        int page = Integer.parseInt(pg);
        int limit = 20;
        int total = page * limit + (list.size() > 0 ? limit : 0);
        return Result.get().page(page, page + 1, limit, total).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = SITE_URL + "/" + id + ".html";

        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        String pic = doc.selectFirst("img.fed-part-pics").attr("src");
        if (!pic.startsWith("http")) pic = SITE_URL + pic;

        String title = doc.selectFirst("title").text();
        title = title.split("\\[")[0].trim(); // 去除 [Mp3_Lrc] 等后缀

        String actor = "", year = "", remarks = "", content = "";

        Elements infoLis = doc.select("ul.fed-deta-info li");
        for (Element li : infoLis) {
            String text = li.text();
            if (text.contains("歌手")) actor = li.selectFirst("a").text();
            if (text.contains("时间")) year = li.selectFirst("span.fed-text-hot").text();
            if (text.contains("专辑")) remarks = li.selectFirst("a").text();
        }

        content = doc.selectFirst("div.fed-arti-content").text();

        Vod vod = new Vod(id, title, pic);
        vod.setVodActor(actor);
        vod.setVodYear(year);
        vod.setVodRemarks(remarks);
        vod.setVodContent(content);

        // 播放源只有一个：爱玩
        vod.setVodPlayFrom("爱玩");
        vod.setVodPlayUrl("播放$" + id);

        List<Vod> vods = new ArrayList<>();
        vods.add(vod);
        return Result.string(vods);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();
        String url = SITE_URL + "/so/" + key;
        if (!pg.equals("1")) url += "-pg-" + pg;
        url += ".html";

        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements items = doc.select("ul.fed-list-info li");
        for (Element item : items) {
            Element a = item.selectFirst("a.fed-list-pics");
            if (a == null) continue;
            String pic = a.selectFirst("img").attr("data-original");
            if (!pic.startsWith("http")) pic = SITE_URL + pic;
            String name = a.attr("title");
            String href = a.attr("href");
            String id = href.startsWith("/") ? href.substring(1).replace(".html", "") : href.replace(".html", "");
            String remarks = item.selectFirst("span.fed-list-remarks").text();
            list.add(new Vod(id, name, pic, remarks));
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 下载页面
        String downUrl = SITE_URL + "/down.php?ac=music&id=" + id.split("/")[id.split("/").length - 1];

        String html = OkHttp.string(downUrl, getHeaders());
        Document doc = Jsoup.parse(html);

        // 实际播放链接（从 player 函数参数中提取）
        String playUrl = "";
        String script = doc.selectFirst("script:contains(player)").html();
        if (script.contains("player(")) {
            int start = script.indexOf("player(") + 8;
            int end = script.indexOf(")", start);
            String param = script.substring(start, end).replace("\"", "");
            // 实际音频链接通常是加密或重定向，这里直接使用 down.php 页面中的真实链接
            // 根据源码，player 函数会加载真实 mp3
            // 但多数情况下，直接找 audio src 或 downbtn href
            Element audio = doc.selectFirst("audio");
            if (audio != null) {
                playUrl = audio.attr("src");
            }
            if (playUrl.isEmpty()) {
                playUrl = doc.selectFirst("a#downbtn").attr("href");
            }
        }

        if (playUrl.isEmpty()) playUrl = downUrl; // 兜底

        // 歌词
        List<Sub> subs = new ArrayList<>();
        String lrcText = doc.selectFirst("div.lrc").text();
        if (!lrcText.isEmpty()) {
            String base64Lrc = java.util.Base64.getEncoder().encodeToString(lrcText.getBytes("UTF-8"));
            subs.add(Sub.create().name("中文歌词").url("data:text/lrc;base64," + base64Lrc).ext("lrc"));
        }

        return Result.get().url(playUrl).format("audio/mpeg").subs(subs).string();
    }

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        return Util.isMedia(url);
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        return true;
    }
}
