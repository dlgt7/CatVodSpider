package com.github.catvod.spider;

import android.content.Context;
import android.util.Base64;

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
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Dm84 extends Spider {

    private String siteUrl = "https://dm84.tv/";

    // 备用域名（站点常备）
    private final String[] backupUrls = {"https://dm84.net/", "https://dm84.pro/"};

    private HashMap<String, String> getHeader() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("Accept-Encoding", "gzip, deflate");
        headers.put("Connection", "keep-alive");
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

    private String fetchWithBackup(String url) {
        try {
            return OkHttp.string(url, getHeader());
        } catch (Exception e) {
            for (String backup : backupUrls) {
                try {
                    String backupUrl = url.replaceAll("https?://[^/]+", backup.replace("/", ""));
                    return OkHttp.string(backupUrl, getHeader());
                } catch (Exception ignored) {}
            }
            throw e;
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
            String content = fetchWithBackup(siteUrl);
            Document doc = Jsoup.parse(content);
            Elements items = doc.select(".v_list li, .hl-vod-list li, .module-item");
            for (Element item : items) {
                Element a = item.selectFirst("a");
                if (a == null) continue;
                String href = a.attr("href");
                String title = a.attr("title").replaceAll("在线观看|观看", "").trim();
                if (title.isEmpty()) title = a.text().trim();
                String pic = item.selectFirst(".lazy, img").attr("data-bg");
                if (pic.isEmpty()) pic = item.selectFirst("img").attr("data-original");
                if (pic.isEmpty()) pic = item.selectFirst("img").attr("src");
                String remark = item.selectFirst(".desc, .pic-text, .note, .hl-item-note").text();

                list.add(new Vod(fixUrl(href), title, fixUrl(pic), remark));
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
            String content = fetchWithBackup(url);
            Document doc = Jsoup.parse(content);
            Elements items = doc.select(".v_list li, .hl-vod-list li, .module-item");
            for (Element item : items) {
                Element a = item.selectFirst("a");
                if (a == null) continue;
                String href = a.attr("href");
                String title = a.attr("title").replaceAll("在线观看|观看", "").trim();
                if (title.isEmpty()) title = a.text().trim();
                String pic = item.selectFirst(".lazy, img").attr("data-bg");
                if (pic.isEmpty()) pic = item.selectFirst("img").attr("data-original");
                if (pic.isEmpty()) pic = item.selectFirst("img").attr("src");
                String remark = item.selectFirst(".desc, .pic-text, .note, .hl-item-note").text();

                list.add(new Vod(fixUrl(href), title, fixUrl(pic), remark));
            }

            boolean hasMore = list.size() >= 20;
            int limit = 24;
            int total = hasMore ? Integer.MAX_VALUE : (page - 1) * limit + list.size();
            int pageCount = hasMore ? Integer.MAX_VALUE : page;

            return Result.get().vod(list).page(page, pageCount, limit, total).string();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.get().vod(list).page(page, page, 24, 0).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String detailUrl = siteUrl + ids.get(0);
            String content = fetchWithBackup(detailUrl);
            Document doc = Jsoup.parse(content);

            String title = doc.selectFirst("h1").text();
            String pic = fixUrl(doc.selectFirst(".poster img").attr("src"));
            String contentText = doc.selectFirst(".intro").text();

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
                    playUrl.append(a.text().trim()).append("$").append(a.attr("href"));
                    if (j < as.size() - 1) playUrl.append("#");
                }
                playUrl.append("$$$");
            }

            Vod vod = new Vod();
            vod.setVodId(ids.get(0));
            vod.setVodName(title);
            vod.setVodPic(pic);
            vod.setVodContent(contentText);
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
        String url = siteUrl + "s-" + java.net.URLEncoder.encode(key) + "---------" + page + ".html";

        try {
            String content = fetchWithBackup(url);
            Document doc = Jsoup.parse(content);
            Elements items = doc.select(".v_list li, .hl-vod-list li, .module-item");
            for (Element item : items) {
                Element a = item.selectFirst("a");
                if (a == null) continue;
                String href = a.attr("href");
                String title = a.attr("title").replaceAll("在线观看|观看", "").trim();
                if (title.isEmpty()) title = a.text().trim();
                String pic = item.selectFirst(".lazy, img").attr("data-bg");
                if (pic.isEmpty()) pic = item.selectFirst("img").attr("data-original");
                if (pic.isEmpty()) pic = item.selectFirst("img").attr("src");
                String remark = item.selectFirst(".desc, .pic-text, .note").text();

                list.add(new Vod(fixUrl(href), title, fixUrl(pic), remark));
            }

            boolean hasMore = list.size() >= 20;
            int limit = 24;
            int total = hasMore ? Integer.MAX_VALUE : (page - 1) * limit + list.size();
            int pageCount = hasMore ? Integer.MAX_VALUE : page;

            return Result.get().vod(list).page(page, pageCount, limit, total).string();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.get().vod(list).page(page, page, 24, 0).string();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String playUrl = siteUrl + id;
            String content = fetchWithBackup(playUrl);

            Pattern p = Pattern.compile("player_aaaa=(\\{.*?\\})");
            Matcher m = p.matcher(content);
            if (m.find()) {
                String jsonStr = m.group(1);
                Pattern urlP = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
                Matcher urlM = urlP.matcher(jsonStr);
                if (urlM.find()) {
                    String url = urlM.group(1);
                    String encrypt = getJsonValue(jsonStr, "encrypt");

                    if ("1".equals(encrypt)) {
                        url = unescape(url);
                    } else if ("2".equals(encrypt)) {
                        url = new String(Base64.decode(url, Base64.DEFAULT));
                    }

                    if (url.startsWith("//")) url = "https:" + url;

                    return Result.get().url(url).header(getHeader()).string();
                }
            }

            Element iframe = Jsoup.parse(content).selectFirst("iframe[src*=/play/]");
            if (iframe != null) {
                String src = iframe.attr("src");
                return Result.get().url(fixUrl(src)).parse(1).string();
            }

            return Result.get().url(playUrl).parse(1).string();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.get().url(siteUrl + id).parse(1).string();
        }
    }

    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return siteUrl + url.substring(1);
        return siteUrl + url;
    }

    private String unescape(String str) {
        return str.replace("%u", "\\u")
                .replace("%20", " ").replace("%21", "!").replace("%24", "$")
                .replace("%25", "%").replace("%28", "(").replace("%29", ")")
                .replace("%2A", "*").replace("%2B", "+").replace("%2C", ",")
                .replace("%2F", "/").replace("%3A", ":").replace("%3B", ";")
                .replace("%3D", "=").replace("%3F", "?").replace("%40", "@")
                .replace("%23", "#").replace("%26", "&");
    }

    private String getJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }
}
