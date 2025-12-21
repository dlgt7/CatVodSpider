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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DJW extends Spider {

    private static final String siteUrl = "https://www.djw123.com";
    private static final Pattern VOD_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\((/vod/(\\d+)\\.html)\\)");
    private static final Pattern REMARK_PATTERN = Pattern.compile("(更新至全集|全集完结|全\\d+集|已完结|全集|\\d+集)");

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

    private List<Vod> parseVods(String html) {
        List<Vod> vods = new ArrayList<>();
        Matcher matcher = VOD_PATTERN.matcher(html);
        while (matcher.find()) {
            String title = matcher.group(1).trim();
            String vodId = matcher.group(3);
            String remark = "全集";

            // 从 matcher.end() 后提取备注
            int end = matcher.end();
            String postText = html.substring(end, Math.min(end + 100, html.length()));
            Matcher rMatcher = REMARK_PATTERN.matcher(postText);
            if (rMatcher.find()) {
                remark = rMatcher.group(0).trim();
            }

            vods.add(new Vod(vodId, title, "", remark)); // 无图片
        }
        return vods;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<com.github.catvod.bean.Filter>> filters = new LinkedHashMap<>();

        Document doc = Jsoup.parse(OkHttp.string(siteUrl + "/", getHeaders()));

        // 分类（从导航或硬编码，主要一个短剧库）
        classes.add(new Class("duanju", "短剧库"));

        List<Vod> vods = parseVods(doc.html());

        return Result.string(classes, vods, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateUrl = siteUrl + "/type/" + tid + ".html";
        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeaders()));

        List<Vod> vods = parseVods(doc.html());

        int page = Integer.parseInt(pg);
        int pageCount = page + 1; // 假设有下一页，无明确分页

        return Result.get().vod(vods).page(page, pageCount, 50, vods.size() + 50).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String detailUrl = siteUrl + "/vod/" + id + ".html";
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeaders()));

        Element h1 = doc.selectFirst("h1");
        String title = h1 != null ? h1.text().trim() : "";

        Vod vod = new Vod(id, title, "");

        // 剧集列表（从 .jisu 或类似）
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        Elements eps = doc.select(".jisu a");
        if (eps.isEmpty()) {
            // 兜底单个全集
            Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
            pu.name = "全集";
            pu.url = detailUrl;
            builder.append("默认线路", java.util.Collections.singletonList(pu));
        } else {
            List<Vod.VodPlayBuilder.PlayUrl> pus = new ArrayList<>();
            for (Element ep : eps) {
                String epName = ep.text().trim();
                String epHref = ep.attr("href");
                if (epHref.startsWith("/play/")) {
                    Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
                    pu.name = epName;
                    pu.url = siteUrl + epHref;
                    pus.add(pu);
                }
            }
            builder.append("迅雷", pus); // 默认线路名
        }

        // 额外线路
        Elements xianlu = doc.select(".xianlu a");
        for (int i = 1; i < xianlu.size(); i++) { // 跳过第一个 active
            Element line = xianlu.get(i);
            String from = line.text().trim();
            String href = line.attr("href");
            if (href.startsWith("/play/")) {
                Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
                pu.name = "全集";
                pu.url = siteUrl + href;
                builder.append(from, java.util.Collections.singletonList(pu));
            }
        }

        Vod.VodPlayBuilder.BuildResult br = builder.build();
        vod.setVodPlayFrom(br.vodPlayFrom);
        vod.setVodPlayUrl(br.vodPlayUrl);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = id.startsWith("http") ? id : siteUrl + id;
        Document doc = Jsoup.parse(OkHttp.string(playUrl, getHeaders()));

        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String content = script.html();
            if (content.contains("player_") && content.contains("\"url\"")) {
                try {
                    int start = content.indexOf("{");
                    int end = content.lastIndexOf("}") + 1;
                    String json = content.substring(start, end);
                    String urlPart = json.split("\"url\":\"")[1];
                    String realUrl = urlPart.split("\"")[0].replace("\\/", "/");
                    if (realUrl.startsWith("http")) {
                        return Result.get().url(realUrl).header(getHeaders()).string(); // 移除 .m3u8() 以防 malformed
                    }
                } catch (Exception ignored) {}
            }
        }

        // 兜底解析
        return Result.get().parse(1).url(playUrl).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return Result.string(new ArrayList<>()); // 站点无搜索
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick);
    }
}
