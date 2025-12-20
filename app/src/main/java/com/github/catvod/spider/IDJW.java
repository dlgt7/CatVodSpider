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
 * 短剧窝(IDJW)爬虫 - 2025年12月20日 纯文本版适配
 */
public class IDJW extends Spider {

    private static final String siteUrl = "https://www.idjw.cc";

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
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();

        String html = OkHttp.string(siteUrl + "/", getHeaders());
        Document doc = Jsoup.parse(html);

        // 当前网站无明确导航分类，手动添加常见分类（基于观察）
        classes.add(new Class("1", "短剧"));
        classes.add(new Class("2", "电影"));
        classes.add(new Class("3", "电视剧"));
        classes.add(new Class("4", "综艺"));
        // 如有更多可继续添加

        // 首页列表（纯文本行）
        String[] lines = doc.text().split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 典型格式: [全集]标题 (评分) 年份
            if (line.contains("/vod/")) {
                String vodId = "";
                String title = line;
                String remark = "";

                // 提取 vodId
                int start = line.indexOf("/vod/");
                if (start != -1) {
                    int end = line.indexOf("/", start + 5);
                    if (end == -1) end = line.indexOf(")", start);
                    vodId = line.substring(start + 5, end != -1 ? end : line.length()).trim();
                }

                // 提取评分作为备注
                if (line.contains(".")) {
                    int scoreStart = line.lastIndexOf(" ", line.indexOf(".")) + 1;
                    if (scoreStart > 0) remark = line.substring(scoreStart).trim();
                }

                if (!vodId.isEmpty()) {
                    list.add(new Vod(vodId, title, "", remark)); // 无图，用空字符串
                }
            }
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "/type/" + tid + (pg.equals("1") ? ".html" : "-" + pg + ".html");
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        String[] lines = doc.text().split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains("/vod/")) {
                String vodId = "";
                String title = line;
                String remark = "";

                int start = line.indexOf("/vod/");
                if (start != -1) {
                    int end = line.indexOf("/", start + 5);
                    if (end == -1) end = line.length();
                    vodId = line.substring(start + 5, end).trim();
                }

                if (line.contains(".")) {
                    int scoreStart = line.lastIndexOf(" ", line.indexOf(".")) + 1;
                    if (scoreStart > 0) remark = line.substring(scoreStart).trim();
                }

                if (!vodId.isEmpty()) {
                    list.add(new Vod(vodId, title, "", remark));
                }
            }
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = siteUrl + "/vod/" + id + ".html";

        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        String vodName = doc.selectFirst("h1") != null ? doc.selectFirst("h1").text() : "";
        String content = doc.text(); // 简介通常在文本中

        // 只有一个播放源
        Vod vod = new Vod(id, vodName, "");
        vod.setVodContent(content);
        vod.setVodPlayFrom("云播");
        vod.setVodPlayUrl("立即播放$/play/" + id + "-1-1/");

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "/search/" + URLEncoder.encode(key, "UTF-8") + "----------" + pg + "---.html";
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        String[] lines = doc.text().split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains("/vod/")) {
                String vodId = "";
                String title = line;
                String remark = "";

                int start = line.indexOf("/vod/");
                if (start != -1) {
                    int end = line.indexOf("/", start + 5);
                    if (end == -1) end = line.length();
                    vodId = line.substring(start + 5, end).trim();
                }

                if (!vodId.isEmpty()) {
                    list.add(new Vod(vodId, title, "", remark));
                }
            }
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = siteUrl + id;

        // 当前播放页可能直接是视频或需进一步解析，直接让APP解析
        return Result.get().url(playUrl).header(getHeaders()).parse(1).string();
    }
}
