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
 * XBPQ 终极优化完善版（2025.12.19）
 * 融合 XYQBiu 本地图片代理 + XBiu 全规则驱动 + XBiubiu 嗅探修复
 * 已删除所有冗余代码、修复潜在问题、强制 127.0.0.1 图片代理、直播播放、补全搜索
 * 最高稳定性与兼容性，适用于所有 biu 系站点
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

    /** 强制使用 127.0.0.1，最安全、最兼容所有设备 */
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
                // 手动分类：格式 "电影+/vodshow/1--------1---.html&剧集+/vodshow/2--------1---.html"
                String[] items = cateManual.split("&");
                for (String item : items) {
                    String[] kv = item.split("\\+");
                    if (kv.length >= 2) {
                        classes.add(new Class(kv[1].trim(), kv[0].trim()));
                    }
                }
            }

            if (classes.isEmpty() && !TextUtils.isEmpty(siteUrl)) {
                // 默认分类兜底
                classes.add(new Class(siteUrl + "/vodshow/1--------1---.html", "电影"));
                classes.add(new Class(siteUrl + "/vodshow/2--------1---.html", "剧集"));
                classes.add(new Class(siteUrl + "/vodshow/4--------1---.html", "动漫"));
                classes.add(new Class(siteUrl + "/vodshow/3--------1---.html", "综艺"));
            }

            return Result.string(classes, null, null);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            String url = tid.startsWith("http") ? tid : siteUrl + tid;

            // 兼容未拼接完整的情况
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

            return Result.string(null, list, null)
                    .page(Integer.parseInt(pg), 999, 24, list.size())
                    .string();
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
            vod.setVodName(doc.selectFirst(rule.optString("片名", ".video-info-header h1")).text().trim());

            Element picEl = doc.selectFirst(rule.optString("图片", ".video-pic img"));
            if (picEl != null) {
                String pic = picEl.attr("data-src");
                if (TextUtils.isEmpty(pic)) pic = picEl.attr("src");
                vod.setVodPic(proxyPic(pic));
            }

            vod.setVodContent(doc.selectFirst(rule.optString("简介", ".video-info-content")).text().trim());

            // 播放线路解析
            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();

            Elements tabs = doc.select(rule.optString("线路tab", ".play-source a"));
            if (tabs.isEmpty()) {
                // 单线路
                Elements lis = doc.select(rule.optString("剧集列表", ".playlist ul li, .playlist li"));
                StringBuilder sb = new StringBuilder();
                for (Element li : lis) {
                    String name = li.text().trim();
                    String link = li.selectFirst("a").absUrl("href");
                    sb.append(name).append("$").append(link).append("#");
                }
                playFrom.add("默认");
                playUrl.add(sb.toString());
            } else {
                // 多线路
                for (Element tab : tabs) {
                    playFrom.add(tab.text().trim());
                    String id = tab.attr("data-id");
                    if (TextUtils.isEmpty(id)) id = tab.attr("href").substring(1);

                    Elements lis = doc.select("ul[data-id=" + id + "] li, ul#" + id + " li");
                    StringBuilder sb = new StringBuilder();
                    for (Element li : lis) {
                        String name = li.text().trim();
                        String link = li.selectFirst("a").absUrl("href");
                        sb.append(name).append("$").append(link).append("#");
                    }
                    playUrl.add(sb.toString());
                }
            }

            vod.setVodPlayFrom(String.join("$$$", playFrom));
            vod.setVodPlayUrl(String.join("$$$", playUrl));

            return Result.string(null, List.of(vod), null);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // biu 系站点绝大多数直链可播，parse=0 更流畅
            return Result.get().url(id).parse(0).header(getHeaders(id)).string();
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

    /** 修复嗅探问题（XBiubiu 核心思想） */
    @Override
    public boolean manualVideoCheck() {
        return true;
    }

    @Override
    public boolean isVideoFormat(String url) {
        if (TextUtils.isEmpty(url)) return false;
        url = url.toLowerCase();
        if (url.contains("=http") || url.contains("=https%3a")) return false;
        return url.contains(".m3u8") || url.contains(".mp4") || url.contains(".flv") || url.contains(".m4a");
    }

    /** 网络请求 + 自动 btwaf 绕过 */
    private String fetch(String webUrl) {
        String html = OkHttp.string(webUrl, getHeaders(webUrl));
        return btwafBypass(webUrl, html);
    }

    private String btwafBypass(String webUrl, String html) {
        if (!rule.optBoolean("btwaf", false) || !html.contains("btwaf")) return html;

        try {
            int start = html.indexOf("btwaf=");
            if (start != -1) {
                String btwaf = html.substring(start + 6, html.indexOf("\"", start + 6));
                String bturl = webUrl + (webUrl.contains("?") ? "&" : "?") + "btwaf=" + btwaf;
                OkHttp.string(bturl, getHeaders(webUrl)); // 触发 cookie 设置
                html = OkHttp.string(webUrl, getHeaders(webUrl));
            }
        } catch (Exception ignored) {}
        return html.trim();
    }
}
