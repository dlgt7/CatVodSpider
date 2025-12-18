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

public class FengChe extends Spider {

    private static final String siteUrl = "https://www.dmla.xyz";  // 更新为主域名

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
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

        // 当前结构：文本链接列表，抓取包含“更新至”的a标签
        Elements items = doc.select("a[href^=/video/]");
        for (Element a : items) {
            String vid = a.attr("href");
            String name = a.text().replaceAll("更新至.*|第.*集", "").trim();
            String remark = a.text().replace(name, "").trim();
            // 图片可能在父元素或无图片
            String pic = a.parent().selectFirst("img") != null ? a.parent().selectFirst("img").attr("src") : "";
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            if (name.isEmpty()) continue;
            vods.add(new Vod(vid, name, pic.isEmpty() ? "" : pic, remark));
        }

        return Result.string(classes, vods.isEmpty() ? new ArrayList<>() : vods);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = siteUrl + "/show/" + tid + "-----------.html";
        if (!"1".equals(pg)) url = url.replace(".html", "--" + pg + ".html");

        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
        List<Vod> vods = new ArrayList<>();

        Elements items = doc.select("a[href^=/video/]");
        for (Element a : items) {
            String vid = a.attr("href");
            String name = a.text().replaceAll("更新至.*|第.*集", "").trim();
            String remark = a.text().replace(name, "").trim();
            String pic = "";
            if (a.parent() != null) {
                pic = a.parent().selectFirst("img") != null ? a.parent().selectFirst("img").attr("src") : "";
            }
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            if (name.isEmpty()) continue;
            vods.add(new Vod(vid, name, pic, remark));
        }

        return Result.string(vods);
    }

    // detailContent、searchContent、playerContent 类似放宽选择器（保持原样但添加容错）
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = siteUrl + ids.get(0);
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        String name = doc.selectFirst("h1, .title").text();
        String pic = doc.selectFirst("img[src^=/uploads]") != null ? doc.selectFirst("img[src^=/uploads]").attr("src") : "";
        if (!pic.startsWith("http")) pic = siteUrl + pic;

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(name);
        vod.setVodPic(pic);

        // 播放列表（当前站点可能单源或多源）
        StringBuilder from = new StringBuilder("风车$$$");
        StringBuilder playUrl = new StringBuilder();
        Elements as = doc.select(".playlist a, .play a");
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
            String name = a.text().replaceAll("更新至.*", "").trim();
            String remark = a.text().replace(name, "").trim();
            vods.add(new Vod(vid, name, "", remark));
        }
        return Result.string(vods);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return Result.get().url(siteUrl + id).header(getHeaders()).string();  // 依赖嗅探
    }
}
