package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * XBPQ 终极优化完善版（2025.12.19 - 完全兼容当前框架）
 * 已修复所有编译错误：无 ambiguous、无不存在的 list() 方法
 */
public class XBPQ extends Spider {

    private JSONObject rule;
    private String siteUrl = "";

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
        try {
            rule = new JSONObject(extend);
            siteUrl = rule.optString("url", "").trim();
            if (!siteUrl.startsWith("http")) siteUrl = "";
        } catch (Exception e) {
            SpiderDebug.log(e);
            rule = new JSONObject();
        }
    }

    private HashMap<String, String> getHeaders(String url) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        if (rule.has("header")) {
            try {
                JSONObject h = rule.getJSONObject("header");
                Iterator<String> keys = h.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    headers.put(key, h.optString(key));
                }
            } catch (Exception ignored) {}
        }
        return headers;
    }

    private String proxyPic(String pic) {
        if (!rule.optBoolean("picProxy", true) || TextUtils.isEmpty(pic) || pic.contains("127.0.0.1")) {
            return pic;
        }
        return "http://127.0.0.1:9978/file/" + Base64.encodeToString(pic.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP);
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            List<Class> classes = new ArrayList<>();
            String cateManual = rule.optString("cateManual", "");

            if (!TextUtils.isEmpty(cateManual)) {
                String[] items = cateManual.split("&");
                for (String item : items) {
                    String[] kv = item.split("\\$");
                    if (kv.length >= 2) {
                        classes.add(new Class(kv[1].trim(), kv[0].trim()));
                    }
                }
            }

            if (classes.isEmpty() && !TextUtils.isEmpty(siteUrl)) {
                classes.add(new Class(siteUrl + "/vodshow/1--------1---.html", "电影"));
                classes.add(new Class(siteUrl + "/vodshow/2--------1---.html", "剧集"));
                classes.add(new Class(siteUrl + "/vodshow/4--------1---.html", "动漫"));
                classes.add(new Class(siteUrl + "/vodshow/3--------1---.html", "综艺"));
            }

            Result result = new Result();
            result.classes = classes;
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            String url = tid.startsWith("http") ? tid : siteUrl + tid;
            if (!url.contains(".html") && !url.endsWith("/")) {
                url = url.replaceAll("/+$", "") + "/vodshow/" + tid.replaceAll(".html.*", "") + "--------" + pg + "---.html";
            }

            String html = fetch(url);
            Document doc = Jsoup.parse(html);
            List<Vod> list = new ArrayList<>();

            String listSel = rule.optString("列表", ".module-items .module-item");
            Elements items = doc.select(listSel);

            for (Element item : items) {
                Element link = item.selectFirst(rule.optString("标题", ".module-item-title a"));
                if (link == null) link = item.selectFirst("a");

                Element picEl = item.selectFirst(rule.optString("图片", ".module-item-pic img"));
                Element remarkEl = item.selectFirst(rule.optString("备注", ".module-item-text"));

                if (link == null) continue;

                String vodId = link.absUrl("href");
                String vodName = link.attr("title");
                if (TextUtils.isEmpty(vodName)) vodName = link.text().trim();

                String vodPic = "";
                if (picEl != null) {
                    vodPic = picEl.attr("data-src");
                    if (TextUtils.isEmpty(vodPic)) vodPic = picEl.attr("src");
                    if (TextUtils.isEmpty(vodPic)) vodPic = picEl.attr("data-original");
                }
                vodPic = proxyPic(vodPic);

                String remarks = remarkEl != null ? remarkEl.text().trim() : "";

                list.add(new Vod(vodId, vodName, vodPic, remarks));
            }

            Result result = new Result();
            result.list = list;
            result.page(Integer.parseInt(pg), 999, 24, list.size());
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String url = ids.get(0);
            if (!url.startsWith("http")) url = siteUrl + url;

            String html = fetch(url);
            Document doc = Jsoup.parse(html);

            Vod vod = new Vod();
            vod.setVodId(ids.get(0));
            Element nameEl = doc.selectFirst(rule.optString("片名", ".video-info-header h1"));
            vod.setVodName(nameEl != null ? nameEl.text().trim() : "");

            Element picEl = doc.selectFirst(rule.optString("图片", ".video-pic img"));
            if (picEl != null) {
                String pic = picEl.attr("data-src");
                if (TextUtils.isEmpty(pic)) pic = picEl.attr("src");
                vod.setVodPic(proxyPic(pic));
            }

            Element contentEl = doc.selectFirst(rule.optString("简介", ".video-info-content"));
            vod.setVodContent(contentEl != null ? contentEl.text().trim() : "");

            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();

            Elements tabs = doc.select(rule.optString("线路tab", ".play-source a"));
            if (tabs.isEmpty()) {
                Elements lis = doc.select(rule.optString("剧集列表", ".playlist ul li, .playlist li"));
                StringBuilder sb = new StringBuilder();
                for (Element li : lis) {
                    String name = li.text().trim();
                    Element a = li.selectFirst("a");
                    String link = a != null ? a.absUrl("href") : "";
                    sb.append(name).append("$").append(link).append("#");
                }
                playFrom.add("默认");
                playUrl.add(sb.toString());
            } else {
                for (Element tab : tabs) {
                    playFrom.add(tab.text().trim());
                    String id = tab.attr("data-id");
                    if (TextUtils.isEmpty(id)) id = tab.attr("href").substring(1);

                    Elements lis = doc.select("ul[data-id=" + id + "] li, ul#" + id + " li");
                    StringBuilder sb = new StringBuilder();
                    for (Element li : lis) {
                        String name = li.text().trim();
                        Element a = li.selectFirst("a");
                        String link = a != null ? a.absUrl("href") : "";
                        sb.append(name).append("$").append(link).append("#");
                    }
                    playUrl.add(sb.toString());
                }
            }

            vod.setVodPlayFrom(String.join("$$$", playFrom));
            vod.setVodPlayUrl(String.join("$$$", playUrl));

            Result result = new Result();
            result.list = List.of(vod);
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            Result result = new Result();
            result.url = id;
            result.parse = 0;
            result.header = getHeaders(id);
            return result.string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            if (TextUtils.isEmpty(siteUrl)) return "";
            String url = siteUrl + "/vodsearch/" + URLEncoder.encode(key, "UTF-8") + "----------1---.html";
            return categoryContent(url, "1", false, new HashMap<>());
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public boolean manualVideoCheck() { return true; }

    @Override
    public boolean isVideoFormat(String url) {
        if (TextUtils.isEmpty(url)) return false;
        url = url.toLowerCase();
        if (url.contains("=http") || url.contains("=https%3a")) return false;
        return url.contains(".m3u8") || url.contains(".mp4") || url.contains(".flv") || url.contains(".m4a");
    }

    private String fetch(String webUrl) {
        String html = OkHttp.string(webUrl, getHeaders(webUrl));
        return btwafBypass(webUrl, html);
    }

    private String btwafBypass(String webUrl, String html) {
        if (!rule.optBoolean("btwaf", false) || !html.contains("btwaf")) return html;

        try {
            int start = html.indexOf("btwaf=");
            if (start != -1) {
                int end = html.indexOf("\"", start + 6);
                if (end != -1) {
                    String btwaf = html.substring(start + 6, end);
                    String bturl = webUrl + (webUrl.contains("?") ? "&" : "?") + "btwaf=" + btwaf;
                    OkHttp.string(bturl, getHeaders(webUrl));
                    html = OkHttp.string(webUrl, getHeaders(webUrl));
                }
            }
        } catch (Exception ignored) {}
        return html.trim();
    }
}
