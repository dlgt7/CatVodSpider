package com.github.catvod.spider;

import android.content.Context;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Response;

/**
 * 黑料网 Spider - 2025最终修复版
 * 适配 OkHttp (private client), Crypto.CBC
 */
public class Heiliao extends com.github.catvod.crawler.Spider {

    private String siteUrl = "https://heiliao43.com";
    private HashMap<String, String> headers;

    private HashMap<String, String> getHeaders() {
        if (headers == null) {
            headers = new HashMap<>();
            headers.put("User-Agent", Util.CHROME);
            headers.put("Referer", siteUrl + "/");
        }
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (extend != null && !extend.isEmpty()) {
            siteUrl = extend.trim();
            if (!siteUrl.startsWith("http")) siteUrl = "https://" + siteUrl;
            if (siteUrl.endsWith("/")) siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        // 对应 JS 里的分类逻辑
        String[][] nav = {
                {"/", "最新黑料"}, {"/category/hlcg/", "吃瓜视频"}, {"/category/whhl/", "网红事件"}, 
                {"/category/rmhl/", "热门黑料"}, {"/category/jdh/", "经典黑料"}
        };
        for (String[] item : nav) {
            classes.add(new Class(item[0], item[1]));
        }
        return Result.string(classes, new ArrayList<>(), new LinkedHashMap<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (pg == null || pg.isEmpty()) pg = "1";
        String url = siteUrl + tid;
        if (!tid.endsWith("/")) url += "/";
        if (!pg.equals("1")) url += "page/" + pg + "/";

        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
            List<Vod> list = new ArrayList<>();
            // 匹配 JS 里的 a.slider-item
            Elements items = doc.select("div.archive-item, article, a.slider-item");

            for (Element item : items) {
                String vodId = item.attr("href");
                if (vodId.isEmpty()) vodId = item.select("a").attr("href");
                if (vodId.isEmpty()) continue;

                String vodName = item.select("h2, .title, span").first().text().trim();
                
                Element img = item.selectFirst("img");
                String vodPic = img != null ? img.absUrl("src") : "";
                
                // 重点：处理加密占位图
                if (vodPic.contains("base64") || vodPic.isEmpty() || vodPic.contains("placeholder")) {
                    // 将加密串(通常是 vodId 相关的标识)传给代理
                    String idEncoded = Base64.encodeToString(vodId.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                    vodPic = Proxy.getUrl() + "?do=img&id=" + idEncoded;
                }

                String vodRemarks = item.select(".date, .time").text().trim();
                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }
            return Result.get().vod(list).page(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 20, 100).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String id = ids.get(0);
            String url = id.startsWith("http") ? id : siteUrl + id;
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
            
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(doc.select("h1.title, .entry-title").text().trim());
            vod.setVodPic(doc.select("meta[property=og:image]").attr("content"));
            
            Elements videos = doc.select("video source, video, iframe");
            List<String> urls = new ArrayList<>();
            for (Element v : videos) {
                String src = v.attr("src");
                if (!src.isEmpty()) urls.add("播放$" + src);
            }

            vod.setVodPlayFrom("黑料直连");
            vod.setVodPlayUrl(String.join("#", urls));
            return Result.string(vod);
        } catch (Exception e) {
            return Result.error("详情解析失败");
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return Result.get().url(id).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = "/index/search?keyword=" + URLEncoder.encode(key, "UTF-8");
        return categoryContent(url, "1", false, new HashMap<>());
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        if ("img".equals(params.get("do"))) {
            String id = params.get("id");
            if (id != null) {
                // 1. 获取原始加密串
                byte[] decodedBytes = Base64.decode(id, Base64.DEFAULT);
                String encryptedStr = new String(decodedBytes, StandardCharsets.UTF_8);
                
                // 2. JS 里的 key 和 iv
                String keyStr = "xIGg8kTtzg0rKz8z";
                String ivStr = "0000000000000000";
                
                // 3. 调用 Crypto.CBC 进行 AES 解密
                String picUrl = Crypto.CBC(encryptedStr, keyStr, ivStr); 
                if (picUrl.isEmpty()) picUrl = encryptedStr;

                // 4. 使用公共的 OkHttp.newCall 获取图片流
                try (Response response = OkHttp.newCall(picUrl, getHeaders())) {
                    if (response.isSuccessful() && response.body() != null) {
                        return new Object[]{200, "image/jpeg", response.body().bytes()};
                    }
                }
            }
        }
        return null;
    }
}
