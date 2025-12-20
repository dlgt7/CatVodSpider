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
import java.util.Map;

public class Bbibi extends Spider {

    private static final String SITE_URL = "https://www.bbibi.cc";

    private static final List<Class> CLASSES = Arrays.asList(
            new Class("1", "电影"),
            new Class("2", "电视剧"),
            new Class("3", "综艺"),
            new Class("4", "动漫")
    );

    // 该站无明显过滤器，暂不提供（避免空过滤器问题）
    private final LinkedHashMap<String, List<Filter>> FILTERS = new LinkedHashMap<>();

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", SITE_URL);
        return header;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(SITE_URL, getHeader()));

        // 首页视频项（多个模块，如最新电影、电视剧等）
        Elements items = doc.select("a[href^=/detail/?]");
        for (Element a : items) {
            String href = a.attr("href");
            String vodId = href.replace("/detail/?", "").replace(".html", "");
            String vodName = a.text().trim();
            if (vodName.isEmpty()) vodName = a.nextSibling().toString().trim(); // 标题可能在文本节点

            // 备注：演员或更新信息（主演行或更新集数）
            String remarks = "";
            Element parent = a.parent();
            if (parent != null) {
                remarks = parent.text().replace(vodName, "").trim();
                if (remarks.startsWith("主演：")) remarks = remarks.substring(3);
            }

            // 图片：站点可能动态加载，无<img>标签，暂用占位图
            String vodPic = "https://via.placeholder.com/300x400?text=No+Image";

            list.add(new Vod(vodId, vodName, vodPic, remarks));
        }

        return Result.string(CLASSES, list, FILTERS);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 分类页：/list/?{tid}.html （分页未知，假设无或?page=）
        String url = SITE_URL + "/list/?" + tid + ".html";
        Document doc = Jsoup.parse(OkHttp.string(url, getHeader()));

        List<Vod> list = new ArrayList<>();
        Elements items = doc.select("a[href^=/detail/?]");
        for (Element a : items) {
            String href = a.attr("href");
            String vodId = href.replace("/detail/?", "").replace(".html", "");
            String vodName = a.text().trim();

            String remarks = "";
            Element parent = a.parent();
            if (parent != null) remarks = parent.text().replace(vodName, "").trim();

            String vodPic = "https://via.placeholder.com/300x400?text=No+Image";

            list.add(new Vod(vodId, vodName, vodPic, remarks));
        }

        int page = pg.equals("1") ? 1 : Integer.parseInt(pg);
        return Result.get().vod(list).page(page, page + 1, 30, page * 30 + 30).string(); // 假设每页30条
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String detailUrl = SITE_URL + "/detail/?" + vodId + ".html";
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeader()));

        Vod vod = new Vod();
        vod.setVodId(vodId);

        // 标题
        vod.setVodName(doc.selectFirst("h1, .title").text().trim());

        // 图片（若无，占位）
        String pic = doc.selectFirst("img").attr("src");
        if (pic.isEmpty() || pic.contains("placeholder")) pic = "https://via.placeholder.com/300x400?text=No+Image";
        vod.setVodPic(pic);

        // 其他信息（年份、地区、演员等）
        String infoText = doc.text();
        vod.setVodYear(extractInfo(infoText, "年份", "导演"));
        vod.setVodArea(extractInfo(infoText, "地区", "年份"));
        vod.setVodActor(extractInfo(infoText, "主演：", "导演"));
        vod.setVodDirector(extractInfo(infoText, "导演：", "主演"));
        vod.setVodContent(doc.selectFirst(".desc, .content").text().trim());

        // 播放源（通常只有一个“正片”）
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        List<Vod.VodPlayBuilder.PlayUrl> urls = new ArrayList<>();

        Elements eps = doc.select("a[href^=/video/?]");
        if (eps.isEmpty()) {
            //  fallback 单集
            Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
            pu.name = "正片";
            pu.url = vodId + "-0-0";
            urls.add(pu);
        } else {
            for (Element ep : eps) {
                Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
                pu.name = ep.text().trim();
                String playHref = ep.attr("href").replace("/video/?", "").replace(".html", "");
                pu.url = playHref;
                urls.add(pu);
            }
        }
        builder.append("正片", urls);
        Vod.VodPlayBuilder.BuildResult res = builder.build();
        vod.setVodPlayFrom(res.vodPlayFrom);
        vod.setVodPlayUrl(res.vodPlayUrl);

        return Result.string(Arrays.asList(vod));
    }

    private String extractInfo(String text, String start, String end) {
        int s = text.indexOf(start);
        if (s == -1) return "";
        s += start.length();
        int e = end != null ? text.indexOf(end, s) : text.length();
        if (e == -1) e = text.length();
        return text.substring(s, e).trim().replace("：", "");
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // play URL: /video/?{id}-0-0.html 或类似
        String playUrl = SITE_URL + "/video/?" + id + ".html";
        Document doc = Jsoup.parse(OkHttp.string(playUrl, getHeader()));

        // 尝试提取 player script 或 iframe
        String script = doc.select("script:contains(player)").html();
        if (!script.isEmpty()) {
            // 假设 player_aaaa 或类似
            try {
                String realUrl = script.split("\"url\":\"")[1].split("\"")[0].replace("\\/", "/");
                return Result.get().url(realUrl).parse(1).chrome().string();
            } catch (Exception ignored) {}
        }

        Element iframe = doc.selectFirst("iframe");
        if (iframe != null && !iframe.attr("src").isEmpty()) {
            return Result.get().url(iframe.attr("src")).parse(1).chrome().string();
        }

        return Result.get().url("").string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        // 搜索可能 /search/?wd={key} 但404，尝试其他常见
        String searchUrl = SITE_URL + "/search/" + key + "/";
        // 或直接首页搜索，但若无，暂返回空或用首页逻辑
        // 实际测试可能需调整
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeader()));
        List<Vod> list = new ArrayList<>();
        // 解析同home
        Elements items = doc.select("a[href^=/detail/?]");
        for (Element a : items) {
            // 同home解析
            String vodId = a.attr("href").replace("/detail/?", "").replace(".html", "");
            String vodName = a.text().trim();
            String remarks = a.parent().text().replace(vodName, "").trim();
            String vodPic = "https://via.placeholder.com/300x400?text=No+Image";
            list.add(new Vod(vodId, vodName, vodPic, remarks));
        }

        return Result.string(list);
    }
}
