package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class Qwqw818 extends Spider {

    private static final String SITE_URL = "https://qwqw818.sbs";
    private static final String CATE_URL = SITE_URL + "/type/%s.html?page=%s";
    private static final String DETAIL_URL = SITE_URL + "/detail/%s.html";
    private static final String PLAY_URL = SITE_URL + "/play/%s.html";
    private static final String SEARCH_URL = SITE_URL + "/search.html?wd=%s&page=%s";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        Document doc = Jsoup.parse(OkHttp.string(SITE_URL));
        Elements types = doc.select(".navbar-nav > li > a[href^='/type/']");
        for (Element ele : types) {
            String typeId = ele.attr("href").replace("/type/", "").replace(".html", "");
            String typeName = ele.text().trim();
            classes.add(new Class(typeId, typeName));
        }

        // 首页推荐视频
        Elements items = doc.select(".module-items > .module-item");
        for (Element item : items) {
            Element pic = item.selectFirst(".module-item-pic > a > img");
            if (pic == null) continue;
            String vodId = item.selectFirst(".module-item-pic > a").attr("href").replace("/detail/", "").replace(".html", "");
            String vodName = pic.attr("alt").trim();
            String vodPic = pic.attr("data-src");
            if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
            String vodRemarks = item.selectFirst(".module-item-text").text().trim();
            list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
        }

        // 过滤器（基于网站分析，假设有年份、地区等筛选）
        List<Filter> areaList = new ArrayList<>();
        areaList.add(new Filter.Value("全部"));
        areaList.add(new Filter.Value("大陆"));
        areaList.add(new Filter.Value("香港"));
        areaList.add(new Filter.Value("台湾"));
        areaList.add(new Filter.Value("美国"));
        areaList.add(new Filter.Value("韩国"));
        areaList.add(new Filter.Value("日本"));
        // ... 其他地区根据网站添加

        List<Filter> yearList = new ArrayList<>();
        yearList.add(new Filter.Value("全部"));
        for (int i = 2025; i >= 2000; i--) {
            yearList.add(new Filter.Value(String.valueOf(i)));
        }

        List<Filter.Value> sortList = Arrays.asList(
                new Filter.Value("时间排序", "time"),
                new Filter.Value("人气排序", "hits"),
                new Filter.Value("评分排序", "score")
        );

        for (Class cls : classes) {
            List<Filter> cateFilters = new ArrayList<>();
            cateFilters.add(new Filter("area", "地区", areaList));
            cateFilters.add(new Filter("year", "年份", yearList));
            cateFilters.add(new Filter("by", "排序", sortList));
            filters.put(cls.getTypeId(), cateFilters);
        }

        return Result.string(classes, list, filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(SITE_URL));
        Elements items = doc.select(".module-blocklist > .scroll-content > a");
        for (Element item : items) {
            String vodId = item.attr("href").replace("/detail/", "").replace(".html", "");
            String vodName = item.selectFirst(".video-name").text().trim();
            String vodPic = item.selectFirst("img").attr("data-src");
            if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
            String vodRemarks = item.selectFirst(".video-info-aux").text().trim();
            list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
        }
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (extend == null) extend = new HashMap<>();
        String area = extend.getOrDefault("area", "");
        String year = extend.getOrDefault("year", "");
        String by = extend.getOrDefault("by", "time");
        String cateUrl = String.format(CATE_URL, tid, pg);
        if (!area.isEmpty()) cateUrl += "&area=" + area;
        if (!year.isEmpty()) cateUrl += "&year=" + year;
        if (!by.equals("time")) cateUrl += "&by=" + by;

        Document doc = Jsoup.parse(OkHttp.string(cateUrl));
        List<Vod> list = new ArrayList<>();
        Elements items = doc.select(".module-items > .module-item");
        for (Element item : items) {
            Element pic = item.selectFirst(".module-item-pic > a > img");
            if (pic == null) continue;
            String vodId = item.selectFirst(".module-item-pic > a").attr("href").replace("/detail/", "").replace(".html", "");
            String vodName = pic.attr("alt").trim();
            String vodPic = pic.attr("data-src");
            if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
            String vodRemarks = item.selectFirst(".module-item-text").text().trim();
            list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
        }

        int total = list.size() + Integer.parseInt(pg) * 18; // 假设每页18条，实际根据网站调整
        return Result.string(list).page(Integer.parseInt(pg), 999, 18, total);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String detailUrl = String.format(DETAIL_URL, id);
        Document doc = Jsoup.parse(OkHttp.string(detailUrl));

        String vodName = doc.selectFirst(".page-title").text().trim();
        String vodPic = doc.selectFirst(".video-cover img").attr("data-src");
        if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
        String vodType = doc.select(".video-info-items").get(0).select("a").text().trim();
        String vodYear = doc.select(".video-info-items").get(1).select("a").text().trim();
        String vodArea = doc.select(".video-info-items").get(2).select("a").text().trim();
        String vodActor = doc.select(".video-info-items").get(3).text().replace("主演：", "").trim();
        String vodDirector = doc.select(".video-info-items").get(4).text().replace("导演：", "").trim();
        String vodContent = doc.selectFirst(".video-info-content").text().trim();

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(vodName);
        vod.setVodPic(vodPic);
        vod.setTypeName(vodType);
        vod.setVodYear(vodYear);
        vod.setVodArea(vodArea);
        vod.setVodActor(vodActor);
        vod.setVodDirector(vodDirector);
        vod.setVodContent(vodContent);

        // 播放源
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        Elements sources = doc.select(".module-tab-content > .module-tab-item");
        Elements episodes = doc.select(".module-player-list > .scroll-content");
        for (int i = 0; i < sources.size(); i++) {
            String playFrom = sources.get(i).text().trim();
            List<Vod.VodPlayBuilder.PlayUrl> playUrls = new ArrayList<>();
            Elements eps = episodes.get(i).select("a");
            for (Element ep : eps) {
                Vod.VodPlayBuilder.PlayUrl url = new Vod.VodPlayBuilder.PlayUrl();
                url.name = ep.text().trim();
                url.url = ep.attr("href").replace("/play/", "").replace(".html", "");
                playUrls.add(url);
            }
            builder.append(playFrom, playUrls);
        }
        Vod.VodPlayBuilder.BuildResult playResult = builder.build();
        vod.setVodPlayFrom(playResult.vodPlayFrom);
        vod.setVodPlayUrl(playResult.vodPlayUrl);

        return Result.string(Arrays.asList(vod));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = String.format(PLAY_URL, id);
        Document doc = Jsoup.parse(OkHttp.string(playUrl));
        String url = doc.selectFirst("script:contains(player_aaaa)").data().split("\"url\":\"")[1].split("\"")[0].replace("\\", "");
        if (Util.isMedia(url)) {
            return Result.get().url(url).string();
        } else {
            // 假设是m3u8或其他，实际解析iframe或ajax
            // 这里简化，假设直接是m3u8
            return Result.get().m3u8().url(url).string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String searchUrl = String.format(SEARCH_URL, key, pg);
        Document doc = Jsoup.parse(OkHttp.string(searchUrl));
        List<Vod> list = new ArrayList<>();
        Elements items = doc.select(".module-search-item");
        for (Element item : items) {
            String vodId = item.selectFirst(".video-serial").attr("href").replace("/detail/", "").replace(".html", "");
            String vodName = item.selectFirst(".video-info-title a").text().trim();
            String vodPic = item.selectFirst("img").attr("data-src");
            if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
            String vodRemarks = item.selectFirst(".video-info-aux").text().trim();
            list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
        }
        return Result.string(list);
    }

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        return Util.isMedia(url);
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        return false;
    }
}
