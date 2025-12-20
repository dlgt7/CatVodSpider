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

public class Qwqw818 extends Spider {

    private static final String SITE_URL = "https://qwqw818.sbs";
    private static final String DETAIL_URL = SITE_URL + "/index.php/vod/detail/id/%s.html";
    private static final String PLAY_URL = SITE_URL + "/index.php/vod/play/id/%s/sid/1/nid/1.html"; // 假设默认第一集第一源
    private static final String SEARCH_URL = SITE_URL + "/index.php/vod/search/wd/%s.html";

    // 手动定义分类（因为网站无明确分类菜单，使用常见海螺模板分类）
    private static final List<Class> CLASSES = Arrays.asList(
            new Class("1", "电影"),
            new Class("2", "电视剧"),
            new Class("3", "综艺"),
            new Class("4", "动漫"),
            new Class("46", "短剧"),
            new Class("47", "体育赛事")
    );

    // 简单过滤器（网站可能支持class/year/area，但未确认，保守提供常见）
    private final LinkedHashMap<String, List<Filter>> FILTERS = new LinkedHashMap<>();

    public Qwqw818() {
        List<Filter.Value> all = Arrays.asList(new Filter.Value("全部", ""));
        List<Filter.Value> areas = new ArrayList<>(all);
        areas.addAll(Arrays.asList(
                new Filter.Value("大陆", "大陆"),
                new Filter.Value("香港", "香港"),
                new Filter.Value("台湾", "台湾"),
                new Filter.Value("美国", "美国"),
                new Filter.Value("韩国", "韩国"),
                new Filter.Value("日本", "日本")
        ));

        List<Filter.Value> years = new ArrayList<>(all);
        for (int i = 2025; i >= 2010; i--) {
            years.add(new Filter.Value(String.valueOf(i), String.valueOf(i)));
        }

        List<Filter.Value> sorts = Arrays.asList(
                new Filter.Value("最新", "time"),
                new Filter.Value("最热", "hits"),
                new Filter.Value("评分", "score")
        );

        List<Filter> common = Arrays.asList(
                new Filter("area", "地区", areas),
                new Filter("year", "年份", years),
                new Filter("by", "排序", sorts)
        );

        for (Class cls : CLASSES) {
            FILTERS.put(cls.getTypeId(), common);
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

        // 首页推荐（今日更新 + 热门模块）
        Elements items = doc.select(".module-row .module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.module-item-pic");
            if (a == null) continue;
            String vodId = a.attr("href").replace("/index.php/vod/detail/id/", "").replace(".html", "");
            String vodName = a.attr("title");
            String vodPic = a.selectFirst("img").attr("data-original");
            if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
            String remarks = item.selectFirst(".module-item-note").text().trim();
            list.add(new Vod(vodId, vodName, vodPic, remarks));
        }

        return Result.string(CLASSES, list, FILTERS);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = SITE_URL + "/index.php/vod/show/id/" + tid;
        if (extend != null) {
            String area = extend.getOrDefault("area", "");
            String year = extend.getOrDefault("year", "");
            String by = extend.getOrDefault("by", "time");
            if (!area.isEmpty()) url += "/area/" + area;
            if (!year.isEmpty()) url += "/year/" + year;
            if (!by.isEmpty()) url += "/by/" + by;
        }
        url += "/page/" + pg + ".html";

        Document doc = Jsoup.parse(OkHttp.string(url));
        List<Vod> list = new ArrayList<>();

        Elements items = doc.select(".module-item");
        for (Element item : items) {
            Element a = item.selectFirst(".module-item-pic a");
            if (a == null) continue;
            String vodId = a.attr("href").replace("/index.php/vod/detail/id/", "").replace(".html", "");
            String vodName = a.attr("title");
            String vodPic = item.selectFirst("img").attr("data-original");
            if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
            String remarks = item.selectFirst(".module-item-note").text().trim();
            list.add(new Vod(vodId, vodName, vodPic, remarks));
        }

        // 分页估算（常见每页24条）
        int page = Integer.parseInt(pg);
        int total = page * 24 + 24; // 粗估
        return Result.get().vod(list).page(page, page + 1, 24, total).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = String.format(DETAIL_URL, id);
        Document doc = Jsoup.parse(OkHttp.string(url));

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(doc.selectFirst(".module-info-heading h1").text().trim());
        vod.setVodPic(doc.selectFirst(".module-item-pic img").attr("data-original"));
        vod.setTypeName(doc.selectFirst(".module-info-tag-link").text().trim());
        vod.setVodYear(doc.select(".module-info-item:contains(年份)").text().replace("年份：", "").trim());
        vod.setVodArea(doc.select(".module-info-item:contains(地区)").text().replace("地区：", "").trim());
        vod.setVodActor(doc.select(".module-info-item:contains(主演)").text().replace("主演：", "").trim());
        vod.setVodDirector(doc.select(".module-info-item:contains(导演)").text().replace("导演：", "").trim());
        vod.setVodContent(doc.selectFirst(".module-info-content").text().trim());

        // 播放源
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        Elements tabs = doc.select(".module-tab-item");
        Elements lists = doc.select(".module-play-list");
        for (int i = 0; i < tabs.size() && i < lists.size(); i++) {
            String from = tabs.get(i).text().trim();
            List<Vod.VodPlayBuilder.PlayUrl> urls = new ArrayList<>();
            Elements eps = lists.get(i).select("a");
            int nid = 1;
            for (Element ep : eps) {
                Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
                pu.name = ep.text().trim();
                pu.url = id + "/sid/" + (i + 1) + "/nid/" + nid;
                urls.add(pu);
                nid++;
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
        // id here is like "7521/sid/1/nid/1"
        String playUrl = PLAY_URL.replace("%s", id);
        Document doc = Jsoup.parse(OkHttp.string(playUrl));

        // 常见海螺播放页有 player_aaaa 或 iframe
        String script = doc.select("script:contains(player_aaaa)").html();
        if (!script.isEmpty()) {
            String json = script.split("player_aaaa=")[1].split("}")[0] + "}";
            // 需进一步解析 json 中的 url
            // 简化：假设直接提取 url
            String realUrl = json.split("\"url\":\"")[1].split("\"")[0].replace("\\/", "/");
            return Result.get().url(realUrl).parse(1).string(); // 多数需解析
        }

        // 备选 iframe
        String iframe = doc.selectFirst("iframe").attr("src");
        if (!iframe.isEmpty()) {
            return Result.get().url(iframe).parse(1).string();
        }

        return Result.get().url("").string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String url = SEARCH_URL.replace("%s", key) + (pg.equals("1") ? "" : "/page/" + pg);

        Document doc = Jsoup.parse(OkHttp.string(url));
        List<Vod> list = new ArrayList<>();

        Elements items = doc.select(".module-search-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.video-serial");
            if (a == null) continue;
            String vodId = a.attr("href").replace("/index.php/vod/detail/id/", "").replace(".html", "");
            String vodName = item.selectFirst(".video-name").text().trim();
            String vodPic = item.selectFirst("img").attr("data-original");
            if (vodPic.startsWith("//")) vodPic = "https:" + vodPic;
            String remarks = item.selectFirst(".video-tag").text().trim();
            list.add(new Vod(vodId, vodName, vodPic, remarks));
        }

        return Result.string(list);
    }
}
