package com.github.catvod.spider;

import android.content.Context;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;
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

public class DuanJuTJ extends Spider {

    private static final String siteUrl = "https://www.duanjutj.com";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl + "/");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // 分类（从首页直接获取导航栏）
        Document doc = Jsoup.parse(OkHttp.string(siteUrl + "/", getHeaders()));
        Elements navItems = doc.select("ul.navbar-nav li a");
        for (Element item : navItems) {
            String href = item.attr("href");
            if (!href.startsWith("/type/") || href.equals("/type/1.html")) continue; // 跳过首页
            String typeId = href.replace("/type/", "").replace(".html", "");
            String typeName = item.text().trim();
            if (typeName.isEmpty()) continue;
            classes.add(new Class(typeId, typeName));

            // 每个分类下暂时不提供筛选（该站无复杂筛选）
            List<Filter> value = new ArrayList<>();
            value.add(new Filter.Value("全部", ""));
            filters.put(typeId, Arrays.asList(new Filter("order", "排序", value)));
        }

        // 推荐（首页热门）
        List<Vod> vodList = new ArrayList<>();
        Elements items = doc.select("div.module-item");
        for (Element item : items) {
            String pic = item.selectFirst("img.lazyload").attr("data-original");
            if (pic.isEmpty()) pic = item.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            Element titleEle = item.selectFirst("div.module-item-title a");
            String title = titleEle.attr("title");
            String vodId = titleEle.attr("href").replace("/voddetail/", "").replace("/", "");
            String remark = item.selectFirst("div.module-item-text").text();

            vodList.add(new Vod(vodId, title, pic, remark));
        }

        return Result.string(classes, vodList, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = siteUrl + "/type/" + tid + (pg.equals("1") ? "" : "-" + pg) + ".html";
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        List<Vod> list = new ArrayList<>();
        Elements items = doc.select("div.module-item");
        for (Element item : items) {
            String pic = item.selectFirst("img.lazyload").attr("data-original");
            if (pic.isEmpty()) pic = item.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            Element a = item.selectFirst("div.module-item-title a");
            String title = a.attr("title");
            String vodId = a.attr("href").replace("/voddetail/", "").replace("/", "");
            String remark = item.selectFirst("div.module-item-text").text();

            list.add(new Vod(vodId, title, pic, remark));
        }

        // 分页
        int page = Integer.parseInt(pg);
        int limit = 24;
        int total = limit * 20; // 估算
        int pageCount = page + 1;
        Elements pages = doc.select("ul.pagination a");
        if (!pages.isEmpty()) {
            String lastHref = pages.get(pages.size() - 2).attr("href");
            if (lastHref.contains("-")) {
                try {
                    pageCount = Integer.parseInt(lastHref.split("-")[1].replace(".html", ""));
                } catch (Exception ignored) {}
            }
        }

        return Result.get().vod(list).page(page, pageCount, limit, total).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = siteUrl + "/voddetail/" + id + "/";
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        String title = doc.selectFirst("h1.title").text();
        String pic = doc.selectFirst("img.lazyload").attr("data-original");
        if (!pic.startsWith("http")) pic = siteUrl + pic;
        String year = "", area = "", actor = "", director = "", desc = "";
        Elements infos = doc.select("div.module-info-item");
        for (Element info : infos) {
            String text = info.text();
            if (text.contains("年份")) year = text.replace("年份：", "");
            else if (text.contains("地区")) area = text.replace("地区：", "");
            else if (text.contains("主演")) actor = text.replace("主演：", "");
            else if (text.contains("导演")) director = text.replace("导演：", "");
            else if (text.contains("简介")) desc = info.selectFirst("span.module-info-item-content").text();
        }

        Vod vod = new Vod(id, title, pic);
        vod.setVodYear(year);
        vod.setVodArea(area);
        vod.setVodActor(actor);
        vod.setVodDirector(director);
        vod.setVodContent(desc);

        // 播放列表
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        Elements tabs = doc.select("div.module-tab-items-box div.module-tab-item");
        Elements playlists = doc.select("div.module-blocklist");

        for (int i = 0; i < tabs.size() && i < playlists.size(); i++) {
            String playFrom = tabs.get(i).text().trim();
            Elements eps = playlists.get(i).select("div.module-play-list a");
            List<Vod.VodPlayBuilder.PlayUrl> playUrls = new ArrayList<>();

            for (Element ep : eps) {
                String epName = ep.text().trim();
                String epUrl = ep.attr("href");
                playUrls.add(new Vod.VodPlayBuilder.PlayUrl(epName, siteUrl + epUrl));
            }

            builder.append(playFrom, playUrls);
        }

        Vod.VodPlayBuilder.BuildResult result = builder.build();
        vod.setVodPlayFrom(result.vodPlayFrom);
        vod.setVodPlayUrl(result.vodPlayUrl);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = siteUrl + id;
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        // 提取播放地址
        String playerData = doc.selectFirst("script:contains(player_)").html();
        if (playerData != null && playerData.contains("url")) {
            try {
                int start = playerData.indexOf("{");
                int end = playerData.lastIndexOf("}") + 1;
                String jsonStr = playerData.substring(start, end);
                JSONObject player = new JSONObject(jsonStr);
                String playUrl = player.optString("url");

                if (playUrl.startsWith("http")) {
                    if (playUrl.contains("m3u8")) {
                        return Result.get().url(playUrl).m3u8().string();
                    } else {
                        return Result.get().url(playUrl).string();
                    }
                }

                // 部分链接可能是 base64 加密
                if (playUrl.startsWith("/")) playUrl = siteUrl + playUrl;
                if (playUrl.contains("base64")) {
                    try {
                        String decoded = new String(Base64.decode(playUrl.split("base64,")[1], Base64.DEFAULT));
                        return Result.get().url(decoded).string();
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 兜底：直接跳转解析
        return Result.get().parse(1).url(url).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String searchUrl = siteUrl + "/search.php?searchword=" + URLEncoder.encode(key, "UTF-8");
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeaders()));

        List<Vod> list = new ArrayList<>();
        Elements items = doc.select("div.module-item");
        for (Element item : items) {
            String pic = item.selectFirst("img").attr("data-original");
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            Element a = item.selectFirst("a.module-item-cover");
            String title = a.attr("title");
            String vodId = a.attr("href").replace("/voddetail/", "").replace("/", "");
            String remark = item.selectFirst("div.module-item-text").text();

            list.add(new Vod(vodId, title, pic, remark));
        }

        return Result.string(list);
    }
}
