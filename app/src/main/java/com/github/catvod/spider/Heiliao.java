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

/**
 * 黑料网 Spider - 整合修复版
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
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        String[][] nav = {
                {"", "最新黑料"}, {"hlcg", "吃瓜视频"}, {"whhl", "网红事件"}, {"rmhl", "热门黑料"},
                {"jdh", "经典黑料"}, {"day", "日榜黑料"}, {"week", "周榜精选"}, {"month", "月榜热瓜"}
        };
        for (String[] item : nav) {
            String tid = item[0].isEmpty() ? "/" : "/" + item[0] + "/";
            classes.add(new Class(tid, item[1]));
        }
        return Result.string(classes, new ArrayList<>(), new LinkedHashMap<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        if (pg == null || pg.isEmpty()) pg = "1";
        String url = siteUrl + tid;
        if (!tid.endsWith("/")) url += "/";
        if (!pg.equals("1")) url += "page/" + pg + "/";

        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
            List<Vod> list = new ArrayList<>();
            Elements items = doc.select("div.archive-item, article, .post-item");

            for (Element item : items) {
                Element link = item.selectFirst("a");
                if (link == null) continue;

                String vodId = link.attr("href");
                String vodName = item.select("h2, .title").text().trim();
                if (vodName.isEmpty()) continue;

                Element img = item.selectFirst("img");
                String vodPic = img != null ? img.absUrl("src") : "";
                
                // 如果图片是加密占位图，走代理进行 AES 解密
                if (vodPic.contains("base64") || vodPic.isEmpty()) {
                    // 使用 android.util.Base64 进行编码
                    String idEncoded = Base64.encodeToString(vodId.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                    vodPic = Proxy.getUrl(false) + "?do=img&id=" + idEncoded;
                }

                String vodRemarks = item.select(".date, .time, span.date").text().trim();
                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }
            return Result.get().vod(list).page(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 20, 1000).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            Document doc = Jsoup.parse(OkHttp.string(siteUrl + id, getHeaders()));
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(doc.selectFirst("h1.title").text().trim());
            vod.setVodPic(doc.select("meta[property=og:image]").attr("content"));
            
            // 播放源解析
            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();
            Elements videos = doc.select("video, iframe");
            int idx = 1;
            for (Element v : videos) {
                String src = v.attr("src");
                if (src.isEmpty()) continue;
                playFrom.add(v.tagName().equals("video") ? "直链" + idx : "外站" + idx);
                playUrl.add("第" + idx + "段$" + src);
                idx++;
            }

            vod.setVodPlayFrom(String.join("$$$", playFrom));
            vod.setVodPlayUrl(String.join("$$$", playUrl));
            return Result.string(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("详情解析失败");
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return categoryContent("/index/search?keyword=" + URLEncoder.encode(key), "1", false, new HashMap<>());
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        if ("img".equals(params.get("do"))) {
            String id = params.get("id");
            if (id != null) {
                // 解码传入的 ID
                byte[] data = Base64.decode(id, Base64.DEFAULT);
                String vodId = new String(data, StandardCharsets.UTF_8);
                
                // 这里的 AES Key 和 IV 需与目标网站 JS 对应
                String keyStr = "xIGg8kTtzg0rKz8z";
                String ivStr = "0000000000000000";
                
                // 调用你提供的 Crypto.CBC 进行解密
                // 注意：CBC 方法内部做了 Base64 decode，如果 vodId 本身就是加密串则直接传入
                String picUrl = Crypto.CBC(vodId, keyStr, ivStr); 
                
                if (picUrl.isEmpty()) picUrl = vodId; // Fallback
                
                byte[] imageBytes = OkHttp.byteArray(picUrl, getHeaders());
                return new Object[]{200, "image/jpeg", imageBytes};
            }
        }
        return null;
    }
}
