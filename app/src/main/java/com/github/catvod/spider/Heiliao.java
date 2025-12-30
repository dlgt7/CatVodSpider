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
 * 黑料网 Spider - 修复版 (2025-12-30)
 * <p>
 * 基于上传 HTML 文档结构适配:
 * - 分类: 硬编码从文档提取
 * - 列表: div.archive-item > a > h2, img, .date
 * - 详情: h1.title, meta[og:image], div.content p
 * <p>
 * 移除 init throws Exception 以修复编译错误。
 * 默认域名 https://heiliao43.com, 用 ext 覆盖。
 */
public class Heiliao extends com.github.catvod.crawler.Spider {

    private String siteUrl = "https://heiliao43.com";
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
        String[][] nav = {
                {"", "最新黑料"},
                {"hlcg", "吃瓜视频"},
                {"whhl", "网红事件"},
                {"rmhl", "热门黑料"},
                {"jdh", "经典黑料"},
                {"day", "日榜黑料"},
                {"week", "周榜精选"},
                {"month", "月榜热瓜"},
                {"original", "原创社区"},
                {"world", "全球奇闻"},
                {"fan", "反差专区"},
                {"select", "黑料选妃"},
                {"school", "校园黑料"},
                {"netred", "网红黑料"},
                {"drama", "影视短剧"},
                {"daily", "每日大赛"},
                {"star", "明星丑闻"},
                {"night", "深夜综艺"},
                {"twitter", "推特社区"},
                {"exclusive", "独家爆料"},
                {"photo", "桃图杂志"},
                {"class", "黑料课堂"},
                {"help", "有求必应"},
                {"novel", "黑料小说"},
                {"news", "社会新闻"},
                {"neihan", "内涵黑料"},
                {"gov", "官场爆料"}
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
        if (!pg.equals("1")) url += "page/" + pg + "/";

        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
            List<Vod> list = new ArrayList<>();

            Elements items = doc.select("div.archive-item, article.archive-item, .post-item");
            for (Element item : items) {
                Element link = item.selectFirst("a");
                if (link == null) continue;

                String vodId = link.attr("href");
                Element titleEl = item.selectFirst("h2, .title");
                String vodName = titleEl != null ? titleEl.text().trim() : link.text().trim();
                if (vodName.isEmpty()) continue;

                Element img = item.selectFirst("img");
                String vodPic = img != null ? img.absUrl("src") : "";

                Element dateEl = item.selectFirst(".date, .time");
                String vodRemarks = dateEl != null ? dateEl.text().trim() : "";

                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }

            return Result.get().vod(list).page(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 20, list.size() + 1000).string();
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
            Element titleEl = doc.selectFirst("h1.title, h1.entry-title");
            vod.setVodName(titleEl != null ? titleEl.text().trim() : "未知");

            Element ogImg = doc.selectFirst("meta[property=og:image]");
            String vodPic = ogImg != null ? ogImg.attr("content") : "";
            vod.setVodPic(vodPic);

            Element contentEl = doc.selectFirst("div.content, div.entry-content");
            String vodContent = contentEl != null ? contentEl.text().trim() : "";
            vod.setVodContent(vodContent);

            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();
            int idx = 1;

            Elements videos = doc.select("video source, video");
            for (Element v : videos) {
                String src = v.attr("src");
                if (!src.isEmpty()) {
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
        String url = siteUrl + "/index/search?keyword=" + URLEncoder.encode(key);
        return categoryContent("/index/search?keyword=" + URLEncoder.encode(key), "1", false, new HashMap<>());
    }
}
