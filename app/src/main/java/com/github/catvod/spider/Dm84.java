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
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Dm84 extends Spider {

    private String siteUrl = "https://dm84.tv/";

    // 备用域名（站点首页提示）
    private final String[] backupUrls = {"https://dm84.net/", "https://dm84.pro/"};

    private HashMap<String, String> getHeader() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("Accept-Encoding", "gzip, deflate");
        headers.put("Connection", "keep-alive");
        headers.put("Upgrade-Insecure-Requests", "1");
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
                    String backupUrl = url.replace("https://dm84.tv/", backup);
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

            // 增强选择器
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

            return Result.get().vod(list)
                    .page(page, pageCount, limit, total)
                    .string();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.get().vod(list).page(page, page, 24, 0).string();
    }

    // detailContent、searchContent、playerContent 保持原样（已很完善）
    // ...（直接复制你原代码中的这三个方法即可）

    @Override
    public String detailContent(List<String> ids) {
        // （复制你原代码的 detailContent）
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        // （复制你原代码的 searchContent，支持翻页）
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        // （复制你原代码的 playerContent，已支持解密+iframe+嗅探）
    }

    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return siteUrl + url.substring(1);
        return siteUrl + url;
    }

    // unescape 和 getJsonValue 方法保持不变
}
