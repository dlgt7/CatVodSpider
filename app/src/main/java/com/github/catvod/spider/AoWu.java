package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 嗷呜动漫爬虫
 * 站点地址: https://www.aowu.tv
 */
public class AoWu extends Spider {

    private static String siteUrl = "https://www.aowu.tv";

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        header.put("Accept-Language", "zh-CN,zh;q=0.8");
        return header;
    }

    @Override
    public void init(Context context, String extend) {
        if (extend != null && !extend.isEmpty()) {
            siteUrl = extend;
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        // 添加分类
        classes.add(new Class("YAAAAK-", "新番"));
        classes.add(new Class("kAAAAK-", "番剧"));
        classes.add(new Class("1AAAAK-", "剧场"));

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
        List<Vod> list = new ArrayList<>();

        // 首页推荐内容 - 新番
        Elements newAnimeElements = doc.select(".new_bangumi .public-list-box");
        for (Element ele : newAnimeElements) {
            Element aTag = ele.select(".public-list-exp").first();
            if (aTag != null) {
                String vid = aTag.attr("href");
                String name = aTag.attr("title");
                String pic = ele.select(".lazy").attr("data-src");
                String remark = ele.select(".public-list-prb").text();

                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }

                list.add(new Vod(vid, name, pic, remark));
            }
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateUrl = siteUrl + String.format("/show/%s.html", tid);
        if (!"1".equals(pg)) {
            cateUrl = siteUrl + String.format("/show/%s/page/%s.html", tid, pg);
        }

        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeader()));
        List<Vod> list = new ArrayList<>();

        Elements elements = doc.select(".public-list-box");
        for (Element ele : elements) {
            Element aTag = ele.select(".public-list-exp").first();
            if (aTag != null) {
                String vid = aTag.attr("href");
                String name = aTag.attr("title");
                String pic = ele.select(".lazy").attr("data-src");
                String remark = ele.select(".public-list-prb").text();

                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }

                list.add(new Vod(vid, name, pic, remark));
            }
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String detailUrl = siteUrl + ids.get(0);
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeader()));

        // 提取基本信息
        String name = doc.select("h3").first().text();
        String pic = doc.select(".detail-pic img").attr("src");
        if (!pic.startsWith("http")) {
            pic = siteUrl + pic;
        }

        // 提取简介
        String brief = doc.select(".detail-info .check").text();

        // 提取播放源和播放列表
        Elements sources = doc.select(".anthology-tab a");
        Elements playLists = doc.select(".anthology-list-play");

        StringBuilder vod_play_url = new StringBuilder();
        StringBuilder vod_play_from = new StringBuilder();

        for (int i = 0; i < sources.size() && i < playLists.size(); i++) {
            String sourceName = sources.get(i).text();
            vod_play_from.append(sourceName).append("$$$");

            Elements aElementArray = playLists.get(i).select("a");
            for (int j = 0; j < aElementArray.size(); j++) {
                Element a = aElementArray.get(j);
                String href = a.attr("href");
                String text = a.text();
                vod_play_url.append(text).append("$").append(href);
                vod_play_url.append(j < aElementArray.size() - 1 ? "#" : "$$$");
            }
        }

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setVodContent(brief);
        vod.setVodPlayFrom(vod_play_from.toString());
        vod.setVodPlayUrl(vod_play_url.toString());

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String searchUrl = siteUrl + "/search/-------------.html?wd=" + URLEncoder.encode(key, "UTF-8");
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeader()));

        List<Vod> list = new ArrayList<>();
        Elements elements = doc.select(".public-list-box");
        for (Element ele : elements) {
            Element aTag = ele.select(".public-list-exp").first();
            if (aTag != null) {
                String vid = aTag.attr("href");
                String name = aTag.attr("title");
                String pic = ele.select(".lazy").attr("data-src");
                String remark = ele.select(".public-list-prb").text();

                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }

                list.add(new Vod(vid, name, pic, remark));
            }
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = siteUrl + id;
        Document doc = Jsoup.parse(OkHttp.string(playUrl, getHeader()));

        // 查找视频链接
        String videoUrl = "";
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String scriptContent = script.html();
            if (scriptContent.contains("player_aaaa")) {
                Pattern pattern = Pattern.compile("\"url\":\"(.*?)\"");
                Matcher matcher = pattern.matcher(scriptContent);
                if (matcher.find()) {
                    videoUrl = matcher.group(1);
                    break;
                }
            }
        }

        if (!videoUrl.isEmpty()) {
            return Result.get().url(videoUrl).string();
        }

        return Result.get().url(playUrl).string();
    }
}