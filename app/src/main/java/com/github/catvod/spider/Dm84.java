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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.URLEncoder;  // ← 这一行是必须的！

public class Dm84 extends Spider {

    private String siteUrl = "https://dm84.tv";

    private HashMap<String, String> getHeader() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl);
        return headers;
    }

    @Override
    public void init(Context context, String extend) {
        try {
            super.init(context, extend);
            if (extend != null && !extend.isEmpty()) {
                siteUrl = extend.trim();
                if (!siteUrl.endsWith("/")) siteUrl += "/";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        String[] typeIds = {"1", "2", "3", "4"};
        String[] typeNames = {"国产动漫", "日本动漫", "欧美动漫", "动漫电影"};
        for (int i = 0; i < typeIds.length; i++) {
            classes.add(new Class(typeIds[i], typeNames[i]));
        }

        List<Vod> list = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
            Elements items = doc.select(".v_list li");
            for (Element item : items) {
                Element a = item.selectFirst("a");
                if (a == null) continue;
                String href = a.attr("href");
                String title = a.attr("title").replaceAll("在线观看", "").trim();
                String pic = item.selectFirst(".lazy").attr("data-bg");
                String remark = item.selectFirst(".desc").text();

                pic = fixUrl(pic);

                list.add(new Vod(href, title, pic, remark));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();
        int page = Integer.parseInt(pg);
        String url = siteUrl + "list-" + tid + "-" + page + ".html";

        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeader()));
            Elements items = doc.select(".v_list li");
            for (Element item : items) {
                Element a = item.selectFirst("a");
                if (a == null) continue;
                String href = a.attr("href");
                String title = a.attr("title").replaceAll("在线观看", "").trim();
                String pic = item.selectFirst(".lazy").attr("data-bg");
                String remark = item.selectFirst(".desc").text();

                pic = fixUrl(pic);

                list.add(new Vod(href, title, pic, remark));
            }

            // 简单判断是否有下一页（返回不满一页认为无）
            boolean hasMore = list.size() >= 20; // 通常每页20-24条
            int limit = 24;
            int total = hasMore ? Integer.MAX_VALUE : (page - 1) * limit + list.size();
            int pageCount = hasMore ? Integer.MAX_VALUE : page;

            return Result.get().vod(list)
                    .page(page, pageCount, limit, total)
                    .string();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.get().vod(list).page(page, page, 24, 0).string();
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String detailUrl = siteUrl + ids.get(0);
            Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeader()));

            String title = doc.selectFirst("h1").text();
            String pic = fixUrl(doc.selectFirst(".poster img").attr("src"));
            String content = doc.selectFirst(".intro").text();

            Elements tabs = doc.select(".play_from li");
            Elements lists = doc.select(".tab_content .play_list");

            StringBuilder playFrom = new StringBuilder();
            StringBuilder playUrl = new StringBuilder();

            for (int i = 0; i < tabs.size(); i++) {
                String from = tabs.get(i).text().trim();
                playFrom.append(from).append("$$$");

                Elements as = lists.get(i).select("a");
                for (int j = 0; j < as.size(); j++) {
                    Element a = as.get(j);
                    playUrl.append(a.text().trim())
                            .append("$")
                            .append(a.attr("href"));
                    if (j < as.size() - 1) playUrl.append("#");
                }
                playUrl.append("$$$");
            }

            Vod vod = new Vod();
            vod.setVodId(ids.get(0));
            vod.setVodName(title);
            vod.setVodPic(pic);
            vod.setVodContent(content);
            vod.setVodPlayFrom(playFrom.toString());
            vod.setVodPlayUrl(playUrl.toString());

            return Result.get().vod(vod).string();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("详情加载失败");
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        List<Vod> list = new ArrayList<>();
        int page = Integer.parseInt(pg);
        String url = siteUrl + "s-" + URLEncoder.encode(key) + "---------" + page + ".html";

        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeader()));
            Elements items = doc.select(".v_list li");
            for (Element item : items) {
                Element a = item.selectFirst("a");
                if (a == null) continue;
                String href = a.attr("href");
                String title = a.attr("title").replaceAll("在线观看", "").trim();
                String pic = fixUrl(item.selectFirst(".lazy").attr("data-bg"));
                String remark = item.selectFirst(".desc").text();

                list.add(new Vod(href, title, pic, remark));
            }

            boolean hasMore = list.size() >= 20;
            int limit = 24;
            int total = hasMore ? Integer.MAX_VALUE : (page - 1) * limit + list.size();
            int pageCount = hasMore ? Integer.MAX_VALUE : page;

            return Result.get().vod(list)
                    .page(page, pageCount, limit, total)
                    .string();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.get().vod(list).page(page, page, 24, 0).string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String playUrl = siteUrl + id;
            String content = OkHttp.string(playUrl, getHeader());
            Document doc = Jsoup.parse(content);

            // 尝试提取 player_aaaa 对象
            Pattern p = Pattern.compile("player_aaaa=(\\{.*?\\})");
            Matcher m = p.matcher(content);
            if (m.find()) {
                String jsonStr = m.group(1);
                // 简单提取 url
                Pattern urlP = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
                Matcher urlM = urlP.matcher(jsonStr);
                if (urlM.find()) {
                    String url = urlM.group(1);
                    String encrypt = getJsonValue(jsonStr, "encrypt");

                    if ("1".equals(encrypt)) {
                        url = unescape(url);
                    } else if ("2".equals(encrypt)) {
                        url = new String(android.util.Base64.decode(url, android.util.Base64.DEFAULT));
                    }

                    if (url.startsWith("//")) url = "https:" + url;

                    return Result.get().url(url).header(getHeader()).string();
                }
            }

            // 备选：iframe 嵌套
            Element iframe = doc.selectFirst("iframe[src*=/play/]");
            if (iframe != null) {
                String src = iframe.attr("src");
                return Result.get().url(siteUrl + src).parse().string(); // 让APP解析
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.get().url(siteUrl + id).parse().string(); // 最后兜底让APP嗅探
    }

    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return siteUrl + url.substring(1);
        return siteUrl + url;
    }

    private String unescape(String str) {
        return str.replace("%20", " ").replace("%21", "!").replace("%24", "$")
                .replace("%25", "%").replace("%28", "(").replace("%29", ")")
                .replace("%2A", "*").replace("%2B", "+").replace("%2C", ",")
                .replace("%2F", "/").replace("%3A", ":").replace("%3B", ";")
                .replace("%3D", "=").replace("%3F", "?").replace("%40", "@")
                .replace("%23", "#").replace("%26", "&").replace("%u", "\\u");
    }

    private String getJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }
}
