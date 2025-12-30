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
 * 黑料网 Spider - 2025年12月30日最终稳定版
 * <p>
 * 当前最新主入口（全网一致确认）：https://heiliao43.com
 * 海外发布页/镜像：https://heiliao.com
 * 强烈建议在规则JSON中使用 "ext": "https://heiliao43.com" 传入最新域名
 * <p>
 * 站点为成人敏感爆料类，请严格遵守当地法律法规。
 */
public class Heiliao extends com.github.catvod.crawler.Spider {

    private String siteUrl = "https://heiliao43.com";  // 默认最新主域
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
        // 父类 init 不抛异常，无需 throws
        super.init(context, extend);
        if (extend != null && !extend.isEmpty()) {
            siteUrl = extend.trim();
            if (!siteUrl.startsWith("http")) {
                siteUrl = "https://" + siteUrl;
            }
            if (siteUrl.endsWith("/")) {
                siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
            }
        }
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        // 当前站点常见分类（2025年底实测稳定路径）
        String[][] nav = {
                {"", "首页/最新"},
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
        if (!"1".equals(pg)) {
            url += "page/" + pg + "/";
        }

        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
            List<Vod> list = new ArrayList<>();

            Elements items = doc.select("article, .post, .archive-item, .entry");
            for (Element item : items) {
                Element link = item.selectFirst("a[href*=/archives/]");
                if (link == null) continue;

                String vodId = link.attr("href");
                if (!vodId.startsWith("http")) {
                    vodId = vodId.startsWith("/") ? vodId : "/" + vodId;
                }

                Element titleEl = item.selectFirst("h2, h3, .title, img[alt]");
                String vodName = "";
                if (titleEl != null) {
                    if ("img".equals(titleEl.tagName())) {
                        vodName = titleEl.attr("alt").trim();
                    } else {
                        vodName = titleEl.text().trim();
                    }
                }
                if (vodName.isEmpty()) vodName = link.text().trim();
                if (vodName.isEmpty()) continue;

                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) pic = img.absUrl("src");

                String remark = "";
                Element dateEl = item.selectFirst(".date, .time, .meta, .post-date");
                if (dateEl != null) remark = dateEl.text().trim();

                list.add(new Vod(vodId, vodName, pic.isEmpty() ? "https://via.placeholder.com/300x400?text=HL" : pic, remark));
            }

            int currentPage = Integer.parseInt(pg);
            return Result.get()
                    .vod(list)
                    .page(currentPage, currentPage + 1, 30, list.size() + 1000)
                    .string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        String id = ids.get(0);
        String url = siteUrl + (id.startsWith("http") ? "" : "") + id;

        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

            Vod vod = new Vod();
            vod.setVodId(id);

            Element titleEl = doc.selectFirst("h1.title, h1.entry-title, h1.post-title, h1");
            vod.setVodName(titleEl != null ? titleEl.text().trim() : "未知标题");

            String pic = "";
            Element ogImg = doc.selectFirst("meta[property=og:image]");
            if (ogImg != null) pic = ogImg.attr("content");
            if (pic.isEmpty()) {
                Element firstImg = doc.selectFirst("article img, .content img");
                if (firstImg != null) pic = firstImg.absUrl("src");
            }
            vod.setVodPic(pic.isEmpty() ? "https://via.placeholder.com/300x400?text=HL" : pic);

            Element contentEl = doc.selectFirst(".content, .entry-content, .post-content, article");
            String content = contentEl != null ? contentEl.text() : "";
            vod.setVodContent(content.length() > 300 ? content.substring(0, 300) + "..." : content);

            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();
            int idx = 1;

            Elements videos = doc.select("video source, video");
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
