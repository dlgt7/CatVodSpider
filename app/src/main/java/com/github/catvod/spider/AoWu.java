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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 嗷呜动漫爬虫（2025年修复版）
 * 网站已大幅转向APP推广（Moefun），网页端结构大变，原选择器失效。
 * 当前网页仍有部分静态内容可抓，但列表有限，可能不完整。
 * 已根据最新结构调整选择器，优先抓取新番等列表。
 */
public class AoWu extends Spider {

    private static String siteUrl = "https://www.aowu.tv";

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        header.put("Accept-Language", "zh-CN,zh;q=0.9");
        header.put("Referer", siteUrl);
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
        classes.add(new Class("YAAAAK-", "当季新番"));  // 调整为当前导航文字
        classes.add(new Class("kAAAAK-", "番剧"));
        classes.add(new Class("1AAAAK-", "剧场"));

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
        List<Vod> list = new ArrayList<>();

        // 当前首页新番列表通常在 <a href="/bangumi/xxx.html"> 或直接标题链接
        Elements items = doc.select("a[href^=/bangumi/]");
        for (Element item : items) {
            String vid = item.attr("href");
            if (!vid.startsWith("http")) vid = siteUrl + vid;
            String name = item.text().trim();
            if (name.isEmpty()) continue;

            // 图片可能在父元素或兄弟元素
            String pic = item.selectFirst("img") != null ? item.selectFirst("img").attr("src") : "";
            if (pic.isEmpty()) pic = item.parent().selectFirst("img") != null ? item.parent().selectFirst("img").attr("src") : "";
            if (!pic.isEmpty() && !pic.startsWith("http")) pic = siteUrl + pic;

            // 更新备注（如“更新至xx话”）可能在附近span
            String remark = "";
            Element parent = item.parent();
            if (parent != null) {
                Elements notes = parent.select("span, div, small");
                for (Element note : notes) {
                    String text = note.text();
                    if (text.contains("更新") || text.contains("话") || text.contains("周")) {
                        remark = text;
                        break;
                    }
                }
            }

            if (!name.isEmpty()) {
                list.add(new Vod(vid.replace(siteUrl, ""), name, pic, remark));
            }
        }

        // 如果首页抓不到，尝试从分类页补充（可选）
        if (list.isEmpty()) {
            // 可添加备用逻辑
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateUrl = siteUrl + "/show/" + tid;
        if (!"1".equals(pg)) {
            cateUrl += "/page/" + pg + ".html";
        } else {
            cateUrl += ".html";
        }

        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeader()));
        List<Vod> list = new ArrayList<>();

        Elements items = doc.select("a[href^=/bangumi/]");
        for (Element item : items) {
            String vid = item.attr("href");
            if (!vid.startsWith("http")) vid = siteUrl + vid;

            String name = item.attr("title");
            if (name.isEmpty()) name = item.text().trim();

            String pic = item.selectFirst("img") != null ? item.selectFirst("img").attr("src") : "";
            if (pic.isEmpty() && item.parent() != null) {
                pic = item.parent().selectFirst("img") != null ? item.parent().selectFirst("img").attr("src") : "";
            }
            if (!pic.isEmpty() && !pic.startsWith("http")) pic = siteUrl + pic;

            String remark = "";
            if (item.parent() != null) {
                remark = item.parent().text();
                remark = remark.replace(name, "").trim();
            }

            if (!name.isEmpty()) {
                list.add(new Vod(vid.replace(siteUrl, ""), name, pic, remark));
            }
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String detailUrl = siteUrl + ids.get(0);
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeader()));

        String name = doc.selectFirst("h1, h2, h3, .title") != null ? doc.selectFirst("h1, h2, h3, .title").text() : "";
        String pic = doc.selectFirst("img.cover, img[src*='cover'], .detail-pic img") != null ? doc.selectFirst("img.cover, img[src*='cover'], .detail-pic img").attr("src") : "";
        if (!pic.startsWith("http")) pic = siteUrl + pic;

        String brief = doc.selectFirst(".intro, .summary, .desc, .check") != null ? doc.selectFirst(".intro, .summary, .desc, .check").text() : "";

        // 播放源和列表
        StringBuilder vod_play_from = new StringBuilder();
        StringBuilder vod_play_url = new StringBuilder();

        Elements sources = doc.select(".play-source a, .anthology-tab a, a[data-source]");
        Elements playLists = doc.select(".play-list, .anthology-list-play, ul.episodes");

        for (int i = 0; i < sources.size() && i < playLists.size(); i++) {
            String sourceName = sources.get(i).text().trim();
            if (sourceName.isEmpty()) sourceName = "线路" + (i + 1);
            vod_play_from.append(sourceName).append("$$$");

            Elements eps = playLists.get(i).select("a, li");
            for (int j = 0; j < eps.size(); j++) {
                Element ep = eps.get(j);
                String epName = ep.text().trim();
                String epUrl = ep.attr("href");
                if (!epUrl.startsWith("http")) epUrl = siteUrl + epUrl;
                vod_play_url.append(epName).append("$").append(epUrl.replace(siteUrl, ""));
                vod_play_url.append(j < eps.size() - 1 ? "#" : "$$$");
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
        Elements items = doc.select("a[href^=/bangumi/]");
        for (Element item : items) {
            String vid = item.attr("href");
            if (!vid.startsWith("http")) vid = siteUrl + vid;

            String name = item.attr("title");
            if (name.isEmpty()) name = item.text();

            String pic = item.selectFirst("img") != null ? item.selectFirst("img").attr("src") : "";
            if (!pic.startsWith("http")) pic = siteUrl + pic;

            String remark = item.nextSibling() != null ? item.nextSibling().toString() : "";

            list.add(new Vod(vid.replace(siteUrl, ""), name, pic, remark));
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = siteUrl + id;
        Document doc = Jsoup.parse(OkHttp.string(playUrl, getHeader()));

        String videoUrl = "";
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String content = script.html();
            if (content.contains("player_") || content.contains("url") || content.contains("video")) {
                Pattern p = Pattern.compile("(?:\"|'|url[:=]\\s*)[\"']?(https?://[^\"']+\\.m3u8|[^\"']+\\.mp4)[\"']?");
                Matcher m = p.matcher(content);
                if (m.find()) {
                    videoUrl = m.group(1).replace("\\", "");
                    break;
                }
            }
        }

        return videoUrl.isEmpty() ? Result.get().url(playUrl).string() : Result.get().url(videoUrl).header(getHeader()).string();
    }
}
