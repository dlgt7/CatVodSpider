package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 秀儿影视 Java 爬虫
 * 参考 xiuer.js 和 xiuer.py 实现
 */
public class Xiuer extends Spider {

    private final String siteUrl = "https://www.xiuer.pro";

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        header.put("Referer", siteUrl + "/");
        return header;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        String[] names = {"电影", "电视剧", "综艺", "动漫", "短剧", "纪录片"};
        String[] ids = {"dianying", "dianshiju", "zongyi", "dongman", "duanju", "jilupian"};
        for (int i = 0; i < names.length; i++) {
            classes.add(new Class(ids[i], names[i]));
        }
        return Result.string(classes, parseList(OkHttp.string(siteUrl, getHeader()), false));
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = OkHttp.string(siteUrl, getHeader());
        return Result.string(parseList(html, false));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = siteUrl + "/show/" + tid + "/page/" + pg + ".html";
        String html = OkHttp.string(url, getHeader());
        return Result.string(parseList(html, false));
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = siteUrl + ids.get(0);
        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(doc.selectFirst("h1").text());

        // 图片提取
        Element picTag = doc.selectFirst(".video-cover img, .module-item-pic img");
        if (picTag != null) {
            String pic = picTag.attr("data-src");
            if (pic.isEmpty()) pic = picTag.attr("src");
            vod.setVodPic(pic.startsWith("http") ? pic : siteUrl + pic);
        }

        // 详情信息
        Element aux = doc.selectFirst(".video-info-aux");
        if (aux != null) vod.setVodRemarks(aux.text());
        
        Element content = doc.selectFirst(".video-info-content");
        if (content != null) vod.setVodContent(content.text());

        // 对应 JS 索引：主演(0), 导演(1), 年份(3)
        Elements infoItems = doc.select(".video-info-items");
        vod.setVodActor(getInfoByIndex(infoItems, 0));
        vod.setVodDirector(getInfoByIndex(infoItems, 1));
        vod.setVodYear(getInfoByIndex(infoItems, 3));

        // 播放列表
        Elements tabs = doc.select(".module-tab-item");
        Elements lists = doc.select(".module-player-list");
        
        // 使用 Vod.java 中的 Builder 模式(如果可用)或手动拼接
        List<String> froms = new ArrayList<>();
        List<String> urls = new ArrayList<>();

        for (int i = 0; i < tabs.size(); i++) {
            String tabName = tabs.get(i).text();
            if (tabName.contains("排序")) continue;

            Elements links = lists.get(i).select("a[href*=/play/]");
            List<String> vodItems = new ArrayList<>();
            for (Element link : links) {
                vodItems.add(link.text() + "$" + link.attr("href"));
            }
            if (!vodItems.isEmpty()) {
                froms.add(tabName);
                urls.add(join("#", vodItems));
            }
        }
        vod.setVodPlayFrom(join("$$$", froms));
        vod.setVodPlayUrl(join("$$$", urls));

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = siteUrl + "/vod/search/wd/" + key + ".html";
        String html = OkHttp.string(url, getHeader());
        return Result.string(parseList(html, true));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 参考 JS 的 play_parse: true，这里返回解析模式
        String videoUrl = id.startsWith("http") ? id : siteUrl + id;
        return Result.get().url(videoUrl).parse().header(getHeader()).string();
    }

    // --- 内部解析工具 ---

    private List<Vod> parseList(String html, boolean isSearch) {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        String selector = isSearch ? ".module-search-item" : ".module-item";
        Elements items = doc.select(selector);

        for (Element item : items) {
            Element a = item.selectFirst("a[href*=/detail/]");
            if (a == null) continue;

            Vod vod = new Vod();
            vod.setVodId(a.attr("href"));
            vod.setVodName(isSearch ? item.selectFirst("h3").text() : a.attr("title"));

            Element img = item.selectFirst("img");
            if (img != null) {
                String pic = img.attr("data-src");
                if (pic.isEmpty()) pic = img.attr("src");
                vod.setVodPic(pic.startsWith("http") ? pic : siteUrl + pic);
            }

            Element remark = item.selectFirst(".video-serial, .module-item-text, .module-item-note");
            if (remark != null) vod.setVodRemarks(remark.text());

            list.add(vod);
        }
        return list;
    }

    private String getInfoByIndex(Elements items, int index) {
        if (items.size() > index) {
            String text = items.get(index).text();
            return text.contains("：") ? text.split("：")[1].trim() : text;
        }
        return "";
    }

    private String join(String separator, List<String> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) sb.append(separator);
        }
        return sb.toString();
    }
}
