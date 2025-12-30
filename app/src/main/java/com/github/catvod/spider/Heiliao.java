package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
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

/**
 * 黑料网 Spider - 2025年12月30日最新适配
 * <p>
 * 站点域名极不稳定：
 * - 海外永久域：https://heiliao.com (常作为发布页)
 * - 当前最新主入口：https://heiliao43.com (多个来源确认，2025年底活跃)
 * - 其他备用：heiliao44.com / heiliao45.com 等可能轮换
 * <p>
 * 强烈建议在使用规则时通过 "ext" 参数传入当前可用域名，例如 "https://heiliao43.com"
 * <p>
 * 站点结构：文字爆料为主，少量直链视频或iframe外站播放，无加密图片。
 */
public class Heiliao extends com.github.catvod.crawler.Spider {

    private String siteUrl = "https://heiliao43.com";  // 默认使用当前最新主域
    private HashMap<String, String> headers;

    private HashMap<String, String> getHeaders() {
        if (headers == null) {
            headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
            headers.put("Referer", siteUrl + "/");
        }
        return headers;
    }

    @Override
    public void init(Context context, String extend) {
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
        // 2025年底实测常见固定分类（即使域名变，路径基本一致）
        String[][] nav = {
                {"", "首页/最新黑料"},
                {"hlcg", "最新黑料"},
                {"jrrs", "今日热瓜"},
                {"rmhl", "热门黑料"},
                {"jdh", "经典黑料"},
                {"day", "日榜"},
                {"week", "周榜"},
                {"month", "月榜"},
                {"xycg", "校园黑料"},
                {"whhl", "网红黑料"},
                {"mxl", "明星丑闻"},
                {"qyhl", "反差专区"}
                // 可根据需要继续补充
        };
        for (String[] item : nav) {
            String tid = item[0].isEmpty() ? "/" : "/" + item[0] + "/";
            classes.add(new Class(tid, item[1]));
        }

        LinkedHashMap<String, List<com.github.catvod.bean.Filter>> filters = new LinkedHashMap<>();
        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        if (pg == null || pg.isEmpty()) pg = "1";
        String url = siteUrl + tid;
        if (!tid.endsWith("/")) url += "/";
        if (!pg.equals("1")) url += "page/" + pg + "/";  // 常见分页格式 page/2/

        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
            List<Vod> list = new ArrayList<>();

            // 通配多种可能结构（article, .post, .archive-item 等）
            Elements items = doc.select("article, .post-item, .archive-item, .list-item, a[href*=/archives/]");
            for (Element item : items) {
                Element link = item.tagName().equals("a") ? item : item.selectFirst("a[href*=/archives/]");
                if (link == null) continue;

                String vodId = link.attr("href");
                if (!vodId.contains("/archives/")) continue;

                String titleEl = link.selectFirst("h2, h3, .title, img[alt]");
                String vodName = titleEl != null ? titleEl.attr("alt").trim() : link.text().trim();
                if (vodName.isEmpty()) continue;

                String pic = "";
                Element img = link.selectFirst("img");
                if (img != null) pic = img.absUrl("src");

                String remark = item.selectFirst(".date, .time, .meta") != null ? item.selectFirst(".date, .time, .meta").text().trim() : "";

                list.add(new Vod(vodId, vodName, pic.isEmpty() ? "https://via.placeholder.com/300x400?text=HL" : pic, remark));
            }

            return Result.get().vod(list).page(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 30, list.size() + 500).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        String id = ids.get(0);
        String url = siteUrl + id;

        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

            Vod vod = new Vod();
            vod.setVodId(id);

            Element titleEl = doc.selectFirst("h1.title, h1.entry-title, h1");
            vod.setVodName(titleEl != null ? titleEl.text().trim() : "未知标题");

            String pic = doc.selectFirst("meta[property=og:image]") != null ? doc.selectFirst("meta[property=og:image]").attr("content") : "";
            vod.setVodPic(pic.isEmpty() ? "https://via.placeholder.com/300x400?text=HL" : pic);

            String content = doc.selectFirst(".content, .entry-content, .post-content") != null ?
                    doc.selectFirst(".content, .entry-content, .post-content").text() : "";
            vod.setVodContent(content.length() > 300 ? content.substring(0, 300) + "..." : content);

            // 播放源提取
            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();

            Elements videos = doc.select("video source, video");
            int idx = 1;
            for (Element v : videos) {
                String src = v.attr("src");
                if (!src.isEmpty() && src.startsWith("http")) {
                    playFrom.add("直链" + idx);
                    playUrl.add("第" + idx + "段$" + src);
                    idx++;
                }
            }

            Elements iframes = doc.select("iframe[src^=http]");
            for (Element iframe : iframes) {
                String src = iframe.attr("src");
                playFrom.add("外站" + idx);
                playUrl.add("第" + idx + "段$" + src);
                idx++;
            }

            if (!playFrom.isEmpty()) {
                vod.setVodPlayFrom(String.join("$$$", playFrom));
                vod.setVodPlayUrl(String.join("$$$", playUrl));
            }

            List<Vod> resList = new ArrayList<>();
            resList.add(vod);
            return Result.string(resList);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        String url = siteUrl + "/?s=" + URLEncoder.encode(key);
        return categoryContent("/?s=" + URLEncoder.encode(key), "1", false, new HashMap<>());
    }
}
