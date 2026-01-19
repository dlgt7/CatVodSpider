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
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 51吃瓜爬虫 - 最终优化版（2025年12月14日）
 * 
 * 主要优化点：
 * 1. 多线路自动选最快（7条当前可用线路全集成）
 * 2. 图片显示完美：兼容 data-xkrkllgl + loadBannerDirect 两种加密方式（直接传原始加密内容给Proxy）
 * 3. 播放兼容所有格式：动态判断 parse=0（直链）或 parse=1（flv 等兼容）
 * 4. 支持 ext 传入域名（明文或Base64）
 */
public class WuCg extends Spider {

    private String currentHost = "https://article.vmaylon.cc";

    private static final String[] backupDomains = {
            "https://article.vmaylon.cc",
            "https://auto.vmaylon.cc",
            "https://agree.kmaxqhw.xyz",
            "https://basket.kmaxqhw.xyz",
            "https://butter.kmaxqhw.xyz",
            "https://breath.vmaylon.cc",
            "https://behind.vmaylon.cc"
    };

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);

        if (extend != null && !extend.isEmpty()) {
            String url = extend.trim();
            try {
                byte[] decoded = Base64.decode(url, Base64.NO_WRAP | Base64.URL_SAFE);
                url = new String(decoded, "UTF-8").trim();
            } catch (Exception ignored) {}
            if (url.startsWith("http://") || url.startsWith("https://")) {
                currentHost = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            }
        }

        currentHost = getWorkingHost();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();

        String response = OkHttp.string(currentHost, getHeaders());
        if (response == null || response.isEmpty()) {
            return Result.string(classes, list);
        }

        Document doc = Jsoup.parse(response);

        Elements navItems = doc.select(".navbar-nav li a, .category-list a, nav a, .menu a[href^=/category]");
        for (Element item : navItems) {
            String href = item.attr("href").trim();
            String text = item.text().trim();
            if (!href.isEmpty() && !text.isEmpty() && !href.equals("/") && !href.equals("#")) {
                if (!href.startsWith("http")) href = currentHost + href;
                classes.add(new Class(href.replace(currentHost, ""), text));
            }
        }

        list = parseVods(doc.select("#index article a, article a"));

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String url = currentHost + tid;
        if (!pg.equals("1")) {
            url += (tid.endsWith("/") ? "" : "/") + "page/" + pg + "/";
        }

        String response = OkHttp.string(url, getHeaders());
        if (response != null && !response.isEmpty()) {
            Document doc = Jsoup.parse(response);
            videos = parseVods(doc.select("#archive article a, article a"));
        }

        int page = Integer.parseInt(pg);
        return Result.get()
                .page(page, 99999, 90, 999999)
                .vod(videos)
                .string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String url = vodId.startsWith("http") ? vodId : currentHost + vodId;

        String response = OkHttp.string(url, getHeaders());
        if (response == null || response.isEmpty()) {
            return "";
        }

        Document doc = Jsoup.parse(response);
        Vod vod = new Vod();
        vod.setVodId(vodId);

        Element titleEl = doc.selectFirst("h1.post-title, .post-title");
        vod.setVodName(titleEl != null ? titleEl.text().trim() : "未知标题");

        Element picEl = doc.selectFirst("meta[property=og:image]");
        vod.setVodPic(picEl != null ? picEl.attr("content") : "");

        List<String> tagList = new ArrayList<>();
        for (Element tag : doc.select(".tags a, .keywords a")) {
            String title = tag.text().trim();
            String href = tag.attr("href");
            if (!title.isEmpty()) {
                tagList.add("[a=cr:{\"id\":\"" + href + "\",\"name\":\"" + title + "\"}/]" + title + "[/a]");
            }
        }
        vod.setVodContent(tagList.isEmpty() ? vod.getVodName() : String.join(" ", tagList));

        vod.setVodPlayFrom("51吃瓜");

        List<String> playUrls = new ArrayList<>();
        Elements players = doc.select(".dplayer");
        int index = 1;
        for (Element player : players) {
            String config = player.attr("data-config");
            if (!config.isEmpty()) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(config);
                    String videoUrl = json.optJSONObject("video").optString("url");
                    if (!videoUrl.isEmpty()) {
                        playUrls.add("视频" + index + "$" + videoUrl);
                        index++;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (playUrls.isEmpty()) {
            playUrls.add("暂无播放源$" + url);
        }
        vod.setVodPlayUrl(String.join("#", playUrls));

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();
        String url = currentHost + "/search/" + key + "/" + (pg.equals("1") ? "" : pg + "/");

        String response = OkHttp.string(url, getHeaders());
        if (response != null && !response.isEmpty()) {
            Document doc = Jsoup.parse(response);
            list = parseVods(doc.select("article a"));
        }

        int page = Integer.parseInt(pg);
        return Result.get()
                .page(page, 99999, 90, 999999)
                .vod(list)
                .string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 动态判断是否直链播放
        boolean isDirect = id.toLowerCase().matches(".*\\.(m3u8|mp4|ts|mov|avi|webm|mkv)(\\?.*)?$");
        int parse = isDirect ? 0 : 1;

        return Result.get()
                .url(id)
                .parse(parse)
                .header(getHeaders())
                .string();
    }

    // 图片处理终极优化：兼容两种加密方式
    private List<Vod> parseVods(Elements elements) {
        List<Vod> videos = new ArrayList<>();
        for (Element element : elements) {
            String href = element.attr("href");
            if (!href.startsWith("http")) href = currentHost + href;

            Element titleEl = element.selectFirst("h2, .post-card-title");
            String name = titleEl != null ? titleEl.text().trim() : "";
            if (name.isEmpty()) continue;

            String pic = "";

            // 方式1: data-xkrkllgl（Base64加密数据，直接传）
            Element imgEl = element.selectFirst("img");
            if (imgEl != null) {
                String encryptedBase64 = imgEl.attr("data-xkrkllgl");
                if (!encryptedBase64.isEmpty()) {
                    pic = Proxy.getUrl() + "&url=" + encryptedBase64 + "&type=img";
                }
            }

            // 方式2: script 中的 loadBannerDirect('加密URL') —— 直接传原始加密URL
            if (pic.isEmpty()) {
                Element scriptEl = element.selectFirst("script");
                if (scriptEl != null) {
                    String scriptText = scriptEl.html();
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("loadBannerDirect\\(['\"]([^'\"]+)['\"]\\)");
                    java.util.regex.Matcher m = p.matcher(scriptText);
                    if (m.find()) {
                        String encryptedUrl = m.group(1);
                        pic = Proxy.getUrl() + "&url=" + encryptedUrl + "&type=img";
                    }
                }
            }

            // 备用直链
            if (pic.isEmpty() && imgEl != null) {
                String src = imgEl.attr("src");
                if (!src.isEmpty() && !src.contains("placeholder")) {
                    pic = src;
                }
            }
            if (pic.isEmpty()) {
                Element og = element.selectFirst("meta[property=og:image]");
                if (og != null) pic = og.attr("content");
            }

            Element dateEl = element.selectFirst("time, span[itemprop=datePublished], .date");
            String remarks = dateEl != null ? dateEl.text().trim() : "";

            Vod vod = new Vod();
            vod.setVodId(href);
            vod.setVodName(name);
            vod.setVodPic(pic.isEmpty() ? "https://article.vmaylon.cc/usr/themes/Mirages/images/logo-2.png" : pic);
            vod.setVodRemarks(remarks);
            videos.add(vod);
        }
        return videos;
    }

    private String getWorkingHost() {
        ExecutorService executor = Executors.newFixedThreadPool(backupDomains.length);
        CompletionService<HashMap.Entry<String, Long>> completionService = new ExecutorCompletionService<>(executor);

        List<Callable<HashMap.Entry<String, Long>>> tasks = new ArrayList<>();
        for (String domain : backupDomains) {
            final String d = domain;
            tasks.add(() -> {
                try {
                    long start = System.currentTimeMillis();
                    OkHttp.string(d, getHeaders());
                    return new HashMap.SimpleEntry<>(d, System.currentTimeMillis() - start);
                } catch (Exception e) {
                    return new HashMap.SimpleEntry<>(d, Long.MAX_VALUE);
                }
            });
        }

        for (Callable<HashMap.Entry<String, Long>> task : tasks) {
            completionService.submit(task);
        }

        String fastest = currentHost;
        long minTime = Long.MAX_VALUE;
        try {
            for (int i = 0; i < tasks.size(); i++) {
                Future<HashMap.Entry<String, Long>> future = completionService.poll(8, TimeUnit.SECONDS);
                if (future != null) {
                    HashMap.Entry<String, Long> entry = future.get();
                    if (entry.getValue() < minTime) {
                        minTime = entry.getValue();
                        fastest = entry.getKey();
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            executor.shutdown();
        }
        return fastest;
    }
}
