package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuanJuTJ extends Spider {

    private static final String siteUrl = "https://www.duanjutj.com";
    private static final Pattern VIDEO_LINK = Pattern.compile("<a href=\"(/video/(\\d+)\\.html)\">([^<]+)</a>");

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");
        headers.put("Referer", siteUrl + "/");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<com.github.catvod.bean.Filter>> filters = new LinkedHashMap<>();

        Document doc = Jsoup.parse(OkHttp.string(siteUrl + "/", getHeaders()));

        // 提取分类（从 Markdown 风格的 ## [分类名](/type/tid.html)）
        Elements typeLinks = doc.select("a[href^=/type/]");
        for (Element link : typeLinks) {
            String href = link.attr("href"); // /type/duanju.html
            String tid = href.substring(6, href.length() - 5); // duanju 等
            String name = link.text().trim();
            if (!name.isEmpty() && !tid.isEmpty()) {
                classes.add(new Class(tid, name));
            }
        }

        // 首页推荐：解析所有视频链接（纯文本中隐藏的 <a> 标签）
        List<Vod> vods = parseVideoLinks(doc.html());

        return Result.string(classes, vods, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateUrl = siteUrl + "/type/" + tid + ".html";
        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeaders()));

        List<Vod> vods = parseVideoLinks(doc.html());

        // 该站无分页，固定一页
        return Result.get().vod(vods).page(1, 1, 24, vods.size()).string();
    }

    private List<Vod> parseVideoLinks(String html) {
        List<Vod> vods = new ArrayList<>();
        Matcher matcher = VIDEO_LINK.matcher(html);
        while (matcher.find()) {
            String fullHref = matcher.group(1); // /video/123.html
            String vodId = matcher.group(2);    // 123
            String title = matcher.group(3).trim();

            // 备注：从前面的文本提取 "全XX集" 或 "已完结"
            String remark = "全集"; // 默认
            int start = Math.max(0, matcher.start() - 50);
            String preText = html.substring(start, matcher.start());
            if (preText.contains("全")) {
                int idx = preText.lastIndexOf("全");
                if (idx > -1) {
                    remark = preText.substring(idx).split("\n")[0].trim();
                }
            } else if (preText.contains("更新至")) {
                remark = preText.split("更新至")[1].trim().split("\n")[0];
            }

            // 无图片，用默认占位图（框架有默认）
            vods.add(new Vod(vodId, title, "", remark));
        }
        return vods;
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String detailUrl = siteUrl + "/video/" + id + ".html";
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeaders()));

        String title = doc.selectFirst("h1") != null ? doc.selectFirst("h1").text() : "";
        String pic = ""; // 无图

        Vod vod = new Vod(id, title, pic);

        // 播放源：站点一般只有一个播放源
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
        pu.name = "播放";
        pu.url = detailUrl; // 直接传详情页，让 playerContent 处理
        builder.append("默认线路", java.util.Collections.singletonList(pu));

        Vod.VodPlayBuilder.BuildResult br = builder.build();
        vod.setVodPlayFrom(br.vodPlayFrom);
        vod.setVodPlayUrl(br.vodPlayUrl);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id 是 /video/xxx.html
        Document doc = Jsoup.parse(OkHttp.string(id, getHeaders()));

        // 提取播放 script 中的 url
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String content = script.html();
            if (content.contains("player_") || content.contains("url")) {
                try {
                    // 常见 player_aaa = {"url":"xxx","link":"..."}
                    int start = content.indexOf("{");
                    int end = content.lastIndexOf("}") + 1;
                    String jsonPart = content.substring(start, end);

                    // 简单提取 url
                    String[] parts = jsonPart.split("\"url\":\"");
                    if (parts.length > 1) {
                        String playUrl = parts[1].split("\"")[0].replace("\\/", "/");
                        if (playUrl.startsWith("http")) {
                            return Result.get().url(playUrl).m3u8().string();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 兜底解析
        return Result.get().parse(1).url(id).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String searchUrl = siteUrl + "/search/" + URLEncoder.encode(key, "UTF-8") + ".html";
        // 注意：实际搜索路径可能不同，若无结果可尝试其他路径或直接返回空
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeaders()));

        List<Vod> vods = parseVideoLinks(doc.html());
        return Result.string(vods);
    }
}
