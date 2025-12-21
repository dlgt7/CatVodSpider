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

        Document doc = Jsoup.parse(OkHttp.string(siteUrl + "/", getHeaders()));

        // 分类（导航栏）
        Elements navItems = doc.select("ul.type-slide a");
        for (Element item : navItems) {
            String href = item.attr("href");
            if (!href.startsWith("/type/") || href.endsWith("/type/1.html")) continue;
            String typeId = href.split("/type/")[1].replace(".html", "");
            String typeName = item.text().trim();
            if (typeName.isEmpty()) continue;
            classes.add(new Class(typeId, typeName));

            // 简单添加一个伪筛选（站点无真实筛选，可去掉）
            List<Filter.Value> values = new ArrayList<>();
            values.add(new Filter.Value("全部", ""));
            List<Filter> filterList = Arrays.asList(new Filter("order", "排序", values));
            filters.put(typeId, filterList);
        }

        // 首页推荐
        List<Vod> vodList = new ArrayList<>();
        Elements items = doc.select("div.module-items div.module-item");
        for (Element item : items) {
            Element img = item.selectFirst("img");
            String pic = img.attr("data-src");
            if (pic.isEmpty()) pic = img.attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            Element a = item.selectFirst("a.module-item-cover");
            if (a == null) continue;
            String title = a.attr("title");
            String vodId = a.attr("href").replace("/vod/", "").replace(".html", "");
            String remark = item.selectFirst("div.module-item-note").text();

            vodList.add(new Vod(vodId, title, pic, remark));
        }

        return Result.string(classes, vodList, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = siteUrl + "/type/" + tid + (pg.equals("1") ? ".html" : "-" + pg + ".html");
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        List<Vod> list = new ArrayList<>();
        Elements items = doc.select("div.module-items div.module-item");
        for (Element item : items) {
            Element img = item.selectFirst("img");
            String pic = img.attr("data-src");
            if (pic.isEmpty()) pic = img.attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            Element a = item.selectFirst("a.module-item-cover");
            if (a == null) continue;
            String title = a.attr("title");
            String vodId = a.attr("href").replace("/vod/", "").replace(".html", "");
            String remark = item.selectFirst("div.module-item-note").text();

            list.add(new Vod(vodId, title, pic, remark));
        }

        int page = Integer.parseInt(pg);
        int pageCount = page + 1; // 默认下一页
        // 分页逻辑可进一步完善

        return Result.get().vod(list).page(page, pageCount, 24, 9999).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = siteUrl + "/vod/" + id + ".html";
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        String title = doc.selectFirst("h1").text();
        String pic = doc.selectFirst("img.lazy").attr("data-src");
        if (!pic.startsWith("http")) pic = siteUrl + pic;

        Vod vod = new Vod(id, title, pic);

        // 播放源
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        Elements tabs = doc.select("div.module-tab div.module-tab-item");
        Elements playLists = doc.select("div.module-blocklist");

        for (int i = 0; i < tabs.size() && i < playLists.size(); i++) {
            String playFrom = tabs.get(i).text().trim();
            Elements eps = playLists.get(i).select("a");

            List<Vod.VodPlayBuilder.PlayUrl> playUrls = new ArrayList<>();
            for (Element ep : eps) {
                String epName = ep.text().trim();
                String epHref = ep.attr("href");

                Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
                pu.name = epName;
                pu.url = siteUrl + epHref;
                playUrls.add(pu);
            }
            builder.append(playFrom, playUrls);
        }

        Vod.VodPlayBuilder.BuildResult br = builder.build();
        vod.setVodPlayFrom(br.vodPlayFrom);
        vod.setVodPlayUrl(br.vodPlayUrl);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = siteUrl + id;
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        String script = doc.select("script").toString();
        if (script.contains("player_")) {
            // 提取 player_xxx = {...}
            // 简单正则或字符串处理，框架常见方式
            try {
                int start = script.indexOf("{", script.indexOf("player_"));
                int end = script.indexOf("}", start) + 1;
                String json = script.substring(start, end);
                // 这里用简单字符串提取 url（实际可更精确）
                String playUrl = json.split("\"url\":\"")[1].split("\"")[0].replace("\\/", "/");

                if (playUrl.startsWith("http")) {
                    return Result.get().url(playUrl).m3u8().string();
                }
            } catch (Exception ignored) {}
        }

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
        Elements items = doc.select("div.module-items div.module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.module-item-cover");
            if (a == null) continue;
            String title = a.attr("title");
            String vodId = a.attr("href").replace("/vod/", "").replace(".html", "");
            String pic = item.selectFirst("img").attr("data-src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = item.selectFirst("div.module-item-note").text();

            list.add(new Vod(vodId, title, pic, remark));
        }

        return Result.string(list);
    }
}
