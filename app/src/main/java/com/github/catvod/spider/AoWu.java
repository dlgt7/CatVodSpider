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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 嗷呜动漫爬虫（修复版）
 * 原网站 https://www.aowu.tv 可能已转向重度推广 APP（moefun），网页端内容可能被反爬或动态加载导致列表为空。
 * 代码保留原逻辑，仅优化选择器鲁棒性，并添加部分容错。
 * 如果仍无数据，建议更换站点或等待网站恢复。
 */
public class AoWu extends Spider {

    private static String siteUrl = "https://www.aowu.tv";

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        header.put("Accept-Language", "zh-CN,zh;q=0.8");
        // 添加Referer，部分站点需要
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
        classes.add(new Class("YAAAAK-", "新番"));
        classes.add(new Class("kAAAAK-", "番剧"));
        classes.add(new Class("1AAAAK-", "剧场"));

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
        List<Vod> list = new ArrayList<>();

        // 原选择器可能失效，尝试更宽松的选择器或多个备选
        Elements itemElements = doc.select(".public-list-box, .bangumi-item, .list-item, .anime-item, .thumb-block, div[class*='list'][class*='box']");
        if (itemElements.isEmpty()) {
            // 如果仍为空，尝试从所有a标签中筛选包含番剧链接的
            itemElements = doc.select("a[href*='/bangumi/'], a[href*='/play/'], a[href*='.html']");
        }

        for (Element ele : itemElements) {
            try {
                // 更灵活地提取
                Element aTag = ele.selectFirst("a.public-list-exp, a.thumb, a[href], .title a");
                if (aTag == null) aTag = ele.selectFirst("a");

                if (aTag != null) {
                    String vid = aTag.attr("href");
                    if (!vid.startsWith("http")) vid = siteUrl + vid.replace(siteUrl, ""); // 防重复

                    String name = aTag.attr("title");
                    if (name.isEmpty()) name = aTag.text();

                    String pic = ele.selectFirst("img.lazy, img[data-src], img[src], img").attr("data-src");
                    if (pic.isEmpty()) pic = ele.selectFirst("img").attr("src");

                    String remark = ele.selectFirst(".public-list-prb, .update-info, .remark, .note").text();
                    if (remark.isEmpty()) remark = ele.selectFirst("span[class*='update'], span[class*='prb']").text();

                    if (!pic.startsWith("http")) {
                        pic = siteUrl + pic;
                    }

                    if (!name.isEmpty()) {
                        list.add(new Vod(vid, name, pic, remark));
                    }
                }
            } catch (Exception ignored) {}
        }

        return Result.string(classes, list);
    }

    // categoryContent、searchContent 类似优化选择器
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateUrl = siteUrl + String.format("/show/%s.html", tid);
        if (!"1".equals(pg)) {
            cateUrl = siteUrl + String.format("/show/%s/page/%s.html", tid, pg);
        }

        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeader()));
        List<Vod> list = new ArrayList<>();

        Elements elements = doc.select(".public-list-box, .bangumi-item, .list-item, .anime-item");
        for (Element ele : elements) {
            try {
                Element aTag = ele.selectFirst("a.public-list-exp, a.thumb, a[href]");
                if (aTag == null) continue;

                String vid = aTag.attr("href");
                String name = aTag.attr("title");
                if (name.isEmpty()) name = aTag.text();

                String pic = ele.selectFirst("img.lazy, img").attr("data-src");
                if (pic.isEmpty()) pic = ele.selectFirst("img").attr("src");

                String remark = ele.selectFirst(".public-list-prb, .update-info").text();

                if (!pic.startsWith("http")) pic = siteUrl + pic;

                list.add(new Vod(vid, name, pic, remark));
            } catch (Exception ignored) {}
        }

        return Result.string(list);
    }

    // detailContent 和 playerContent 保持原样，如果播放页也变化可能需进一步调整
    @Override
    public String detailContent(List<String> ids) throws Exception {
        // 原逻辑保留，必要时可类似优化
        String detailUrl = siteUrl + ids.get(0);
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeader()));

        String name = doc.selectFirst("h1, h2, h3").text();
        String pic = doc.selectFirst(".detail-pic img, .cover img, img[src*='cover']").attr("src");
        if (!pic.startsWith("http")) pic = siteUrl + pic;

        String brief = doc.selectFirst(".detail-info .check, .intro, .summary").text();

        // 播放源部分保持原样
        Elements sources = doc.select(".anthology-tab a, .play-source a");
        Elements playLists = doc.select(".anthology-list-play, .play-list");

        StringBuilder vod_play_from = new StringBuilder();
        StringBuilder vod_play_url = new StringBuilder();

        for (int i = 0; i < sources.size() && i < playLists.size(); i++) {
            String sourceName = sources.get(i).text();
            vod_play_from.append(sourceName).append("$$$");

            Elements aElements = playLists.get(i).select("a");
            for (int j = 0; j < aElements.size(); j++) {
                Element a = aElements.get(j);
                String href = a.attr("href");
                String text = a.text();
                vod_play_url.append(text).append("$").append(href);
                if (j < aElements.size() - 1) vod_play_url.append("#");
                else vod_play_url.append("$$$");
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
        // 同 category 优化
        String searchUrl = siteUrl + "/search/-------------.html?wd=" + URLEncoder.encode(key, "UTF-8");
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeader()));

        List<Vod> list = new ArrayList<>();
        Elements elements = doc.select(".public-list-box, .search-item, .list-item");
        for (Element ele : elements) {
            try {
                Element aTag = ele.selectFirst("a");
                if (aTag == null) continue;

                String vid = aTag.attr("href");
                String name = aTag.attr("title");
                if (name.isEmpty()) name = aTag.text();

                String pic = ele.selectFirst("img").attr("data-src");
                if (pic.isEmpty()) pic = ele.selectFirst("img").attr("src");

                String remark = ele.selectFirst(".remark, .note").text();

                if (!pic.startsWith("http")) pic = siteUrl + pic;

                list.add(new Vod(vid, name, pic, remark));
            } catch (Exception ignored) {}
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 原逻辑保留，如果播放解析变化需进一步调试
        String playUrl = siteUrl + id;
        Document doc = Jsoup.parse(OkHttp.string(playUrl, getHeader()));

        String videoUrl = "";
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String scriptContent = script.html();
            if (scriptContent.contains("player_aaaa") || scriptContent.contains("url")) {
                Pattern pattern = Pattern.compile("\"url\":\"(.*?)\"|url[:=]\\s*[\"'](.*?)[\"']");
                Matcher matcher = pattern.matcher(scriptContent);
                if (matcher.find()) {
                    videoUrl = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                    videoUrl = videoUrl.replace("\\", "");
                    break;
                }
            }
        }

        return videoUrl.isEmpty() ? Result.get().url(playUrl).string() : Result.get().url(videoUrl).string();
    }
}
