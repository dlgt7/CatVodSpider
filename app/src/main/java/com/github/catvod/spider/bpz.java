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

/**
 * 白嫖者联盟影视爬虫 - 最终适配版 (2025年12月20日)
 * 网站: https://www.qyzf88.com/
 * 当前为纯文本列表结构，无缩略图，标准多线路播放
 */
public class bpz extends Spider {

    private static final String siteUrl = "https://www.qyzf88.com";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
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
        List<Vod> list = new ArrayList<>();

        String html = OkHttp.string(siteUrl + "/", getHeaders());
        Document doc = Jsoup.parse(html);

        // 固定分类（基于当前网站导航）
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));

        // 首页推荐（多个模块合并，纯文本列表）
        Elements links = doc.select("a[href*=/qyvoddetail/]");
        for (Element a : links) {
            String href = a.attr("href");
            String title = a.text().trim();
            if (title.isEmpty() || !href.startsWith("/qyvoddetail/")) continue;

            // 备注：尝试从父元素或前后文本获取更新状态/评分
            String remark = "";
            Element parent = a.parent();
            if (parent != null) {
                Elements siblings = parent.children();
                for (Element sib : siblings) {
                    if (sib != a) {
                        String txt = sib.text().trim();
                        if (txt.contains("集") || txt.contains("分") || txt.matches(".*\\d{4}.*")) {
                            remark = txt;
                            break;
                        }
                    }
                }
            }

            String vodId = href.replace("/qyvoddetail/", "").replace(".html", "");
            list.add(new Vod(vodId, title, "", remark)); // 无图
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();

        // 当前分类URL模式：/qyvodshow/{tid}-----------{pg}.html (11个- + 分页)
        String url = siteUrl + "/qyvodshow/" + tid + "-----------" + pg + ".html";
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements links = doc.select("a[href*=/qyvoddetail/]");
        for (Element a : links) {
            String href = a.attr("href");
            String title = a.text().trim();
            if (title.isEmpty() || !href.startsWith("/qyvoddetail/")) continue;

            String remark = "";
            Element parent = a.parent();
            if (parent != null) {
                String parentText = parent.ownText().trim();
                if (parentText.contains("集") || parentText.contains("分")) remark = parentText;
            }

            String vodId = href.replace("/qyvoddetail/", "").replace(".html", "");
            list.add(new Vod(vodId, title, "", remark));
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = siteUrl + "/qyvoddetail/" + id + ".html";

        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(id);

        // 标题
        Element titleEl = doc.selectFirst("h1, .title, strong");
        vod.setVodName(titleEl != null ? titleEl.text().trim() : "");

        // 无图
        vod.setVodPic("");

        // 基本信息（演员、地区、年份、简介）
        String fullText = doc.text();
        vod.setVodActor(extractInfo(fullText, "演员："));
        vod.setVodArea(extractInfo(fullText, "地区："));
        vod.setVodYear(extractInfo(fullText, "年份：") + extractInfo(fullText, "上映："));
        vod.setVodContent(doc.selectFirst("div.content, p") != null ? doc.selectFirst("div.content, p").text().trim() : "");

        // 播放列表（多线路）
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();

        // 线路tab（如果有显式tab，否则默认分组）
        Elements tabItems = doc.select("div.tab-item, .play-source");
        Elements playLists = doc.select("div.play-list, .episode-list");

        if (tabItems.isEmpty() || playLists.isEmpty()) {
            // 兜底：所有集数链接分组为单一线路
            List<Vod.VodPlayBuilder.PlayUrl> urls = new ArrayList<>();
            Elements eps = doc.select("a[href*=/qyvodplay/]");
            for (Element ep : eps) {
                String epName = ep.text().trim();
                String epHref = ep.attr("href");
                if (!epHref.startsWith("http")) epHref = siteUrl + epHref;

                Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
                pu.name = epName.isEmpty() ? "播放" : epName;
                pu.url = epHref;
                urls.add(pu);
            }
            if (!urls.isEmpty()) {
                builder.append("默认线路", urls);
            }
        } else {
            for (int i = 0; i < tabItems.size() && i < playLists.size(); i++) {
                String fromName = tabItems.get(i).text().trim();
                if (fromName.isEmpty()) fromName = "线路" + (i + 1);

                List<Vod.VodPlayBuilder.PlayUrl> urls = new ArrayList<>();
                Elements eps = playLists.get(i).select("a");
                for (Element ep : eps) {
                    String epName = ep.text().trim();
                    String epHref = ep.attr("href");
                    if (!epHref.startsWith("http")) epHref = siteUrl + epHref;

                    Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
                    pu.name = epName;
                    pu.url = epHref;
                    urls.add(pu);
                }
                if (!urls.isEmpty()) {
                    builder.append(fromName, urls);
                }
            }
        }

        Vod.VodPlayBuilder.BuildResult result = builder.build();
        vod.setVodPlayFrom(result.vodPlayFrom);
        vod.setVodPlayUrl(result.vodPlayUrl);

        return Result.string(vod);
    }

    private String extractInfo(String text, String prefix) {
        int index = text.indexOf(prefix);
        if (index != -1) {
            String sub = text.substring(index + prefix.length());
            int end = sub.indexOf(" ");
            if (end == -1) end = sub.length();
            return sub.substring(0, end).replace("：", "").trim();
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();

        // 当前搜索URL模式类似分类
        String url = siteUrl + "/qyvodsearch/" + URLEncoder.encode(key, "UTF-8") + "-----------" + pg + ".html";
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements links = doc.select("a[href*=/qyvoddetail/]");
        for (Element a : links) {
            String href = a.attr("href");
            String title = a.text().trim();
            if (title.isEmpty() || !href.startsWith("/qyvoddetail/")) continue;

            String remark = a.parent() != null ? a.parent().ownText().trim() : "";
            String vodId = href.replace("/qyvoddetail/", "").replace(".html", "");
            list.add(new Vod(vodId, title, "", remark));
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = siteUrl + id;

        // 无独立player脚本，直接交给APP内置解析器
        return Result.get().url(playUrl).header(getHeaders()).parse(1).string();
    }
}
