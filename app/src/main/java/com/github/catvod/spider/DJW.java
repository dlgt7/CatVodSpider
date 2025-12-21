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

public class DJW extends Spider {

    private static final String siteUrl = "https://www.djw123.com";
    private static final Pattern VOD_PATTERN = Pattern.compile("<a[^>]+href=\"(/vod/(\\d+)\\.html)\"[^>]*>([^<]+)</a>");

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
            String href = matcher.group(1); // /vod/xxx.html
            String vodId = matcher.group(2); // ID
            String title = matcher.group(3).trim().replace("**", "");

            // 备注：从前文提取 更新至全集 / 全XX集 / 已完结 等
            String remark = "全集";
            int start = Math.max(0, matcher.start() - 100);
            String pre = html.substring(start, matcher.start());
            if (pre.contains("更新至")) {
                remark = pre.split("更新至")[1].trim();
            } else if (pre.contains("全集")) {
                remark = "全集";
            } else if (pre.contains("已完结")) {
                remark = "已完结";
            } else if (pre.contains("全") && pre.matches(".*全\\d+集.*")) {
                try {
                    String num = pre.replaceAll(".*全(\\d+)集.*", "$1");
                    remark = "全" + num + "集";
                } catch (Exception ignored) {}
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

        // 分类：提取 /show/ 或 /type/ 链接（站点有 /show/26---类别--------.html）
        Elements cateLinks = doc.select("a[href*=/show/]");
        for (Element link : cateLinks) {
            String href = link.attr("href");
            if (!href.contains("---")) continue;
            String name = link.text().trim();
            if (name.isEmpty() || name.contains("更多")) continue;
            // tid 使用 href 去掉 siteUrl 和前缀
            String tid = href.replace("/show/", "");
            classes.add(new Class(tid, name));
        }

        // 如果没提取到，硬编码常见分类
        if (classes.isEmpty()) {
            classes.add(new Class("duanju-----------", "短剧"));
            // 可添加更多
        }

        List<Vod> vods = parseVods(doc.html());

        return Result.string(classes, vods, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 分类页如 /show/duanju-----------.html 或带分页
        String cateUrl = siteUrl + "/show/" + tid;
        if (!pg.equals("1")) {
            cateUrl = cateUrl.replace("--------", pg); // 假设分页格式，实际可能不同
        }
        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeaders()));

        List<Vod> vods = parseVods(doc.html());

        return Result.get().vod(vods).page(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 30, vods.size() + 30).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String detailUrl = siteUrl + "/vod/" + id + ".html";
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeaders()));

        String title = doc.selectFirst("h1") != null ? doc.selectFirst("h1").text() : "";
        Vod vod = new Vod(id, title, "");

        // 播放源
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();

        // 线路和剧集：查找播放列表 <a href="/play/...">
        Elements playLinks = doc.select("a[href^=/play/]");
        if (!playLinks.isEmpty()) {
            // 假设只有一个线路
            String playFrom = "默认线路";
            List<Vod.VodPlayBuilder.PlayUrl> pus = new ArrayList<>();
            for (Element a : playLinks) {
                String epName = a.text().trim();
                if (epName.isEmpty() || epName.contains("线路")) continue;
                String epHref = a.attr("href");

                Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
                pu.name = epName;
                pu.url = siteUrl + epHref;
                pus.add(pu);
            }
            builder.append(playFrom, pus);
        } else {
            // 无剧集，直接播放详情页
            Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
            pu.name = "全集";
            pu.url = detailUrl;
            builder.append("默认", java.util.Collections.singletonList(pu));
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

        // 提取 player_ script
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String content = script.html();
            if (content.contains("player_") && content.contains("url")) {
                try {
                    int start = content.indexOf("{");
                    int end = content.lastIndexOf("}") + 1;
                    String json = content.substring(start, end);
                    String urlPart = json.split("\"url\":\"")[1];
                    String playUrlFinal = urlPart.split("\"")[0].replace("\\/", "/");
                    if (playUrlFinal.startsWith("http")) {
                        return Result.get().url(playUrlFinal).m3u8().string();
                    }
                } catch (Exception ignored) {}
            }
        }

        // 兜底解析
        return Result.get().parse(1).url(playUrl).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        // 站点搜索可能不支持，或路径未知，返回空或尝试
        return Result.string(new ArrayList<>());
    }
}
