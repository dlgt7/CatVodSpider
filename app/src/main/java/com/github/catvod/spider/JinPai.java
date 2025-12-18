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

public class JinPai extends Spider {

    private static final String siteUrl = "https://www.cfkj86.com";  // 或 https://www.jiabaide.cn，两个结构类似

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        headers.put("Referer", siteUrl + "/");
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));
        List<Vod> vods = new ArrayList<>();

        // 首页热播/推荐列表（常见 .module-row 或 .hl-list-item）
        Elements items = doc.select(".module-item, .hl-item-thumb, .vodlist-item");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String vid = a.attr("href");
            String name = a.attr("title");
            if (name.isEmpty()) name = item.selectFirst(".module-item-title, .hl-item-title").text();
            String pic = item.selectFirst("img").attr("data-original");
            if (pic.isEmpty()) pic = item.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = item.selectFirst(".module-item-note, .hl-pic-text").text();
            vods.add(new Vod(vid, name, pic, remark));
        }

        return Result.string(classes, vods);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateUrl = siteUrl + "/vodshow/" + tid + "--------" + pg + "---.html";
        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeaders()));
        List<Vod> vods = new ArrayList<>();

        Elements items = doc.select(".module-item, .hl-item-thumb");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String vid = a.attr("href");
            String name = a.attr("title");
            if (name.isEmpty()) name = item.selectFirst(".module-item-title").text();
            String pic = item.selectFirst("img").attr("data-original");
            if (pic.isEmpty()) pic = item.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = item.selectFirst(".module-item-note").text();
            vods.add(new Vod(vid, name, pic, remark));
        }

        return Result.string(vods);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String detailUrl = siteUrl + ids.get(0);
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeaders()));

        String name = doc.selectFirst("h1, .module-item-title").text();
        String pic = doc.selectFirst(".module-item-pic img").attr("data-original");
        if (pic.isEmpty()) pic = doc.selectFirst(".module-item-pic img").attr("src");
        if (!pic.startsWith("http")) pic = siteUrl + pic;

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(name);
        vod.setVodPic(pic);

        // 多源播放列表
        StringBuilder from = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();

        Elements sources = doc.select(".module-tab-item, .play-source");
        Elements lists = doc.select(".module-play-list");

        if (sources.isEmpty()) {
            from.append("默认$$$");
            Elements as = doc.select(".module-play-list a");
            for (int i = 0; i < as.size(); i++) {
                Element a = as.get(i);
                playUrl.append(a.text()).append("$").append(a.attr("href"));
                playUrl.append(i < as.size() - 1 ? "#" : "$$$");
            }
        } else {
            for (int i = 0; i < sources.size() && i < lists.size(); i++) {
                from.append(sources.get(i).text()).append("$$$");
                Elements as = lists.get(i).select("a");
                for (int j = 0; j < as.size(); j++) {
                    Element a = as.get(j);
                    playUrl.append(a.text()).append("$").append(a.attr("href"));
                    playUrl.append(j < as.size() - 1 ? "#" : "$$$");
                }
            }
        }

        vod.setVodPlayFrom(from.toString());
        vod.setVodPlayUrl(playUrl.toString());

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String searchUrl = siteUrl + "/vodsearch/" + key + "----------" + "1" + "---.html";
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeaders()));
        List<Vod> vods = new ArrayList<>();

        Elements items = doc.select(".module-item, .hl-item-thumb");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String vid = a.attr("href");
            String name = a.attr("title");
            String pic = item.selectFirst("img").attr("data-original");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = item.selectFirst(".module-item-note").text();
            vods.add(new Vod(vid, name, pic, remark));
        }

        return Result.string(vods);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = siteUrl + id;
        return Result.get().url(playUrl).header(getHeaders()).string();  // 依赖客户端嗅探 m3u8/mp4
    }
}
