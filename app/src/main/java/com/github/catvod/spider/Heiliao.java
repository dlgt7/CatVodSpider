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
 * 黑料网 Spider - 最终修复版 (适配现有 OkHttp.java)
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
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
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
                
                // 如果是占位图或空图，通过 proxy 代理并进行 AES 解密
                if (vodPic.contains("base64") || vodPic.isEmpty() || vodPic.contains("placeholder")) {
                    // 对 vodId 进行 Base64 编码，作为参数传递给 proxy
                    String idEncoded = Base64.encodeToString(vodId.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                    vodPic = Proxy.getUrl() + "?do=img&id=" + idEncoded;
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
    public String detailContent(List<String> ids) throws Exception {
        try {
            String id = ids.get(0);
            Document doc = Jsoup.parse(OkHttp.string(siteUrl + id, getHeaders()));
            Vod vod = new Vod();
            vod.setVodId(id);
            Element titleEl = doc.selectFirst("h1.title, .entry-title");
            vod.setVodName(titleEl != null ? titleEl.text().trim() : "未知标题");
            vod.setVodPic(doc.select("meta[property=og:image]").attr("content"));
            
            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();
            Elements videos = doc.select("video, iframe");
            int idx = 1;
            for (Element v : videos) {
                String src = v.attr("src");
                if (src.isEmpty()) {
                    Element source = v.selectFirst("source");
                    src = source != null ? source.attr("src") : "";
                }
                if (src.isEmpty()) continue;

                playFrom.add("播放源 " + idx);
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
                // 1. 解码传入的 vodId
                byte[] decodedBytes = Base64.decode(id, Base64.DEFAULT);
                String vodId = new String(decodedBytes, StandardCharsets.UTF_8);
                
                // 2. AES 配置 (需匹配网站 JS)
                String keyStr = "xIGg8kTtzg0rKz8z";
                String ivStr = "0000000000000000";
                
                // 3. 调用 Crypto.java 中的 CBC 解密得到真实图片 URL
                // Crypto.CBC 内部会自动处理 URL 中的反斜杠和 Base64 解码
                String picUrl = Crypto.CBC(vodId, keyStr, ivStr); 
                if (picUrl.isEmpty()) picUrl = vodId;

                // 4. 调用 OkHttp.java 中已有的公共方法 newCall
                // 注意：这里直接获取字节流返回给播放器/壳子
                try (Response response = OkHttp.newCall(picUrl, getHeaders())) {
                    if (response.isSuccessful() && response.body() != null) {
                        return new Object[]{200, "image/jpeg", response.body().bytes()};
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
        }
        return null;
    }
}
