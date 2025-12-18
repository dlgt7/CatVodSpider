package com.github.catvod.spider;

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

public class FengChe extends Spider {

    private static final String siteUrl = "https://www.dmla.xyz";

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        String[] names = {"日本动漫", "国产动漫", "动漫电影", "欧美动漫"};
        String[] ids = {"ribendongman", "guochandongman", "dongmandianying", "omeidongman"};
        for (int i = 0; i < names.length; i++) {
            classes.add(new Class(ids[i], names[i]));
        }

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));
        List<Vod> vods = new ArrayList<>();

        Elements items = doc.select("a[href^=/video/]");
        for (Element a : items) {
            String vid = a.attr("href");
            String fullText = a.text();
            String name = fullText.replaceAll("更新至.*|第.*集", "").trim();
            String remark = fullText.replace(name, "").trim();
            String pic = "";
            Element parent = a.parent();
            if (parent != null) {
                Element img = parent.selectFirst("img");
                if (img != null) {
                    pic = img.attr("src");
                    if (!pic.startsWith("http")) pic = siteUrl + pic;
                }
            }
            if (!name.isEmpty()) {
                vods.add(new Vod(vid, name, pic, remark));
            }
        }

        return Result.string(classes, vods);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = siteUrl + "/show/" + tid + "-----------.html";
        if (!"1".equals(pg)) {
            url = url.replace(".html", "--" + pg + ".html");
        }

        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
        List<Vod> vods = new ArrayList<>();

        Elements items = doc.select("a[href^=/video/]");
        for (Element a : items) {
            String vid = a.attr("href");
            String fullText = a.text();
            String name = fullText.replaceAll("更新至.*|第.*集", "").trim();
            String remark = fullText.replace(name, "").trim();
            String pic = "";
            Element parent = a.parent();
            if (parent != null) {
                Element img = parent.selectFirst("img");
                if (img != null) {
                    pic = img.attr("src");
                    if (!pic.startsWith("http")) pic = siteUrl + pic;
                }
            }
            if (!name.isEmpty()) {
                vods.add(new Vod(vid, name, pic, remark));
            }
        }

        return Result.string(vods);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = siteUrl + ids.get(0);
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        String name = doc.selectFirst("h1, .title") != null ? doc.selectFirst("h1, .title").text() : "";
        String pic = "";
        Element img = doc.selectFirst("img[src^=/uploads]");
        if (img != null) {
            pic = img.attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
        }

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(name);
        vod.setVodPic(pic);

        StringBuilder from = new StringBuilder("风车$$$");
        StringBuilder playUrl = new StringBuilder();
        Elements as = doc.select(".playlist a, .play a, a[href^=/play/]");
        for (int i = 0; i < as.size(); i++) {
            Element a = as.get(i);
            playUrl.append(a.text()).append("$").append(a.attr("href"));
            playUrl.append(i == as.size() - 1 ? "$$$" : "#");
        }
        vod.setVodPlayFrom(from.toString());
        vod.setVodPlayUrl(playUrl.toString());

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = siteUrl + "/search/" + key;
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
        List<Vod> vods = new ArrayList<>();

        Elements items = doc.select("a[href^=/video/]");
        for (Element a : items) {
            String vid = a.attr("href");
            String fullText = a.text();
            String name = fullText.replaceAll("更新至.*|第.*集", "").trim();
            String remark = fullText.replace(name, "").trim();
            if (!name.isEmpty()) {
                vods.add(new Vod(vid, name, "", remark));
            }
        }

        return Result.string(vods);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return Result.get().url(siteUrl + id).header(getHeaders()).string(); // 依赖客户端嗅探
    }
}
