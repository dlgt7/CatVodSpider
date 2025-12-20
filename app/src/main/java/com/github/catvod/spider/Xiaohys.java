package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class Xiaohys extends Spider {

    private static final String SITE_URL = "https://www.xiaohys.com";

    // 标准海螺模板分类（小红影视常见）
    private static final List<Class> CLASSES = Arrays.asList(
            new Class("1", "电影"),
            new Class("2", "电视剧"),
            new Class("3", "综艺"),
            new Class("4", "动漫"),
            new Class("46", "短剧")  // 部分站点有短剧分类
    );

    // 通用过滤器
    private final LinkedHashMap<String, List<Filter>> FILTERS = new LinkedHashMap<>();

    public Xiaohys() {
        List<Filter.Value> areas = new ArrayList<>();
        areas.add(new Filter.Value("全部", ""));
        areas.addAll(Arrays.asList(
                new Filter.Value("大陆", "大陆"),
                new Filter.Value("香港", "香港"),
                new Filter.Value("台湾", "台湾"),
                new Filter.Value("美国", "美国"),
                new Filter.Value("法国", "法国"),
                new Filter.Value("英国", "英国"),
                new Filter.Value("日本", "日本"),
                new Filter.Value("韩国", "韩国"),
                new Filter.Value("德国", "德国"),
                new Filter.Value("泰国", "泰国"),
                new Filter.Value("印度", "印度")
        ));

        List<Filter.Value> years = new ArrayList<>();
        years.add(new Filter.Value("全部", ""));
        for (int i = 2025; i >= 2015; i--) {
            years.add(new Filter.Value(String.valueOf(i), String.valueOf(i)));
        }

        List<Filter.Value> sorts = Arrays.asList(
                new Filter.Value("时间排序", "time"),
                new Filter.Value("人气排序", "hits"),
                new Filter.Value("评分排序", "score")
        );

        List<Filter> commonFilters = new ArrayList<>();
        commonFilters.add(new Filter("area", "地区", areas));
        commonFilters.add(new Filter("year", "年份", years));
        commonFilters.add(new Filter("by", "排序", sorts));

        for (Class cls : CLASSES) {
            FILTERS.put(cls.getTypeId(), commonFilters);
        }
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();

        Document doc = Jsoup.parse(OkHttp.string(SITE_URL));

        // 首页推荐模块（多个模块如热门、最新等）
        Elements items = doc.select(".hl-list-item, .module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String href = a.attr("href");
            String vodId = href.replace("/voddetail/", "").replace(".html", "");
            String vodName = a.attr("title");
            String vodPic = a.selectFirst("img").attr("data-original");
            if (vodPic.isEmpty()) vodPic = a.selectFirst("img").attr("src");
            if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
            String remarks = item.selectFirst(".hl-item-note, .module-item-note").text().trim();
            list.add(new Vod(vodId, vodName, vodPic, remarks));
        }

        return Result.string(CLASSES, list, FILTERS);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        StringBuilder url = new StringBuilder(SITE_URL + "/vodshow/" + tid);
        if (extend != null) {
            String area = extend.getOrDefault("area", "");
            String year = extend.getOrDefault("year", "");
            String by = extend.getOrDefault("by", "time");
            if (!area.isEmpty()) url.append("/area/").append(area);
            if (!year.isEmpty()) url.append("/year/").append(year);
            if (!by.isEmpty()) url.append("/by/").append(by);
        }
        url.append("--------").append(pg).append("---.html");

        Document doc = Jsoup.parse(OkHttp.string(url.toString()));
        List<Vod> list = new ArrayList<>();

        Elements items = doc.select(".hl-list-item, .module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String href = a.attr("href");
            String vodId = href.replace("/voddetail/", "").replace(".html", "");
            String vodName = a.attr("title");
            String vodPic = a.selectFirst("img").attr("data-original");
            if (vodPic.isEmpty()) vodPic = a.selectFirst("img").attr("src");
            if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
            String remarks = item.selectFirst(".hl-item-note, .module-item-note").text().trim();
            list.add(new Vod(vodId, vodName, vodPic, remarks));
        }

        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, page + 1, 24, page * 24 + 24).string(); // 每页约24条
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String detailUrl = SITE_URL + "/voddetail/" + vodId + ".html";
        Document doc = Jsoup.parse(OkHttp.string(detailUrl));

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(doc.selectFirst(".hl-dc-title, .module-info-heading h1").text().trim());
        String pic = doc.selectFirst(".hl-lazy, .module-item-pic img").attr("data-original");
        if (pic.isEmpty()) pic = doc.selectFirst("img").attr("src");
        if (pic.startsWith("//")) pic = "https:" + pic;
        vod.setVodPic(pic);
        vod.setTypeName(doc.selectFirst(".hl-dc-tag a, .module-info-tag-link").text().trim());
        vod.setVodYear(doc.selectFirst(".hl-dc-info span:contains(年份)").ownText().trim());
        vod.setVodArea(doc.selectFirst(".hl-dc-info span:contains(地区)").ownText().trim());
        vod.setVodActor(doc.selectFirst(".hl-dc-info span:contains(主演)").ownText().trim());
        vod.setVodDirector(doc.selectFirst(".hl-dc-info span:contains(导演)").ownText().trim());
        vod.setVodContent(doc.selectFirst(".hl-content-text, .module-info-content").text().trim());

        // 播放源构建
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        Elements tabs = doc.select(".hl-plays-tab .hl-tab-item, .module-tab-item");
        Elements lists = doc.select(".hl-plays-list, .module-play-list");
        for (int i = 0; i < tabs.size() && i < lists.size(); i++) {
            String from = tabs.get(i).text().trim().replace("$", "");
            List<Vod.VodPlayBuilder.PlayUrl> urls = new ArrayList<>();
            Elements eps = lists.get(i).select("a");
            for (Element ep : eps) {
                Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
                pu.name = ep.text().trim();
                String playHref = ep.attr("href");
                pu.url = playHref.replace("/vodplay/", "").replace(".html", "");
                urls.add(pu);
            }
            builder.append(from, urls);
        }
        Vod.VodPlayBuilder.BuildResult res = builder.build();
        vod.setVodPlayFrom(res.vodPlayFrom);
        vod.setVodPlayUrl(res.vodPlayUrl);

        return Result.string(Arrays.asList(vod));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = SITE_URL + "/vodplay/" + id + ".html";
        Document doc = Jsoup.parse(OkHttp.string(playUrl));

        // 海螺模板常见 player_aaaa JSON
        String script = doc.select("script:contains(player_aaaa)").html();
        if (!script.isEmpty()) {
            try {
                String jsonStr = script.split("player_aaaa=")[1].split(";</script>")[0].trim();
                String realUrl = jsonStr.split("\"url\":\"")[1].split("\"")[0].replace("\\/", "/");
                if (realUrl.contains("m3u8") || realUrl.contains("mp4")) {
                    return Result.get().url(realUrl).chrome().string();
                }
                return Result.get().url(realUrl).parse(1).chrome().string();
            } catch (Exception ignored) {}
        }

        // 备选 iframe
        Element iframe = doc.selectFirst("iframe");
        if (iframe != null) {
            String src = iframe.attr("src");
            if (!src.isEmpty()) {
                return Result.get().url(src).parse(1).string();
            }
        }

        return Result.get().url("").string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String searchUrl = SITE_URL + "/vodsearch/" + key + "----------" + pg + "---.html";
        Document doc = Jsoup.parse(OkHttp.string(searchUrl));
        List<Vod> list = new ArrayList<>();

        Elements items = doc.select(".hl-list-item, .module-search-item");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String href = a.attr("href");
            String vodId = href.replace("/voddetail/", "").replace(".html", "");
            String vodName = a.attr("title");
            String vodPic = a.selectFirst("img").attr("data-original");
            if (vodPic.isEmpty()) vodPic = a.selectFirst("img").attr("src");
            if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
            String remarks = item.selectFirst(".hl-item-note, .video-tag").text().trim();
            list.add(new Vod(vodId, vodName, vodPic, remarks));
        }

        return Result.string(list);
    }
}
