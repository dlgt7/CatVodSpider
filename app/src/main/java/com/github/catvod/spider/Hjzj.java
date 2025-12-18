package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Hjzj extends Spider {

    private static final String siteUrl = "https://hjzj2.com";

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        // 根据站点实际分类（从搜索结果推断：剧集=20, 其他类似）
        classes.add(new Class("20", "剧集"));
        classes.add(new Class("21", "电影"));  // 假设ID，实际可调整
        classes.add(new Class("22", "动漫"));
        classes.add(new Class("23", "综艺"));

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));
        List<Vod> vods = new ArrayList<>();

        // 首页推荐（常见 .hl-list-item 或 .module-items）
        Elements items = doc.select(".hl-item-thumb, .module-item, .hl-list-item");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String vid = a.attr("href");
            String name = a.attr("title");
            String pic = a.selectFirst("img").attr("data-src");
            if (pic.isEmpty()) pic = a.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = item.selectFirst(".hl-item-remark, .module-item-note").text();
            vods.add(new Vod(vid, name, pic, remark));
        }

        return Result.string(classes, vods);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateUrl = siteUrl + "/all/" + tid + "----------.html";
        if (!"1".equals(pg)) {
            cateUrl = siteUrl + "/all/" + tid + "---------" + pg + "-.html";  // 根据实际分页调整
        }

        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeaders()));
        List<Vod> vods = new ArrayList<>();

        Elements items = doc.select(".hl-item-thumb, .module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String vid = a.attr("href");
            String name = a.attr("title");
            String pic = a.selectFirst("img").attr("data-src");
            if (pic.isEmpty()) pic = a.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = item.selectFirst(".hl-item-remark, .module-item-note").text();
            vods.add(new Vod(vid, name, pic, remark));
        }

        return Result.string(vods);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String detailUrl = siteUrl + ids.get(0);
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeaders()));

        String name = doc.selectFirst("h1, .hl-detail-title").text();
        String pic = doc.selectFirst(".hl-lazy, .module-item-pic img").attr("data-src");
        if (pic.isEmpty()) pic = doc.selectFirst("img").attr("src");
        if (!pic.startsWith("http")) pic = siteUrl + pic;

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(name);
        vod.setVodPic(pic);

        // 播放源和集数
        StringBuilder from = new StringBuilder("默认$$$");
        StringBuilder playUrl = new StringBuilder();

        Elements as = doc.select(".hl-plays-list a, .module-play-list a");
        for (int i = 0; i < as.size(); i++) {
            Element a = as.get(i);
            playUrl.append(a.text()).append("$").append(a.attr("href"));
            playUrl.append(i < as.size() - 1 ? "#" : "$$$");
        }

        vod.setVodPlayFrom(from.toString());
        vod.setVodPlayUrl(playUrl.toString());

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String searchUrl = siteUrl + "/search.php?searchword=" + key;
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeaders()));
        List<Vod> vods = new ArrayList<>();

        Elements items = doc.select(".hl-item-thumb, .module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String vid = a.attr("href");
            String name = a.attr("title");
            String pic = a.selectFirst("img").attr("data-src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = item.selectFirst(".hl-item-remark").text();
            vods.add(new Vod(vid, name, pic, remark));
        }

        return Result.string(vods);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = siteUrl + id;
        return Result.get().url(playUrl).header(getHeaders()).string();  // 依赖嗅探
    }
}
