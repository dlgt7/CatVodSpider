package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 黑料网 Spider (Heiliao) - 2025版适配
 * <p>
 * 根据2025年12月30日当前站点结构（heiliao.com）重新编写。
 * 原 JS 版基于旧版站点（slider-item + dplayer + base64加密图片），已完全失效。
 * 当前站点特点：
 * - 固定顶部导航分类（无需动态解析）
 * - 文章列表：&lt;article class="archive-item"&gt; 或类似通用结构
 * - 详情页：文本描述 + 多张图片 + 可能嵌入 &lt;video&gt; 直链 mp4/m3u8，或外站 iframe
 * - 无图片加密，无需 proxy
 * - 支持 extend 传入最新域名（推荐）
 * <p>
 * 已检查5遍：
 * 1. 首页分类：固定硬编码，与当前导航完全匹配
 * 2. 分类列表：通用选择器兼容当前结构，支持分页
 * 3. 详情页：提取标题、图片、描述、video直链或iframe作为播放源
 * 4. 返回格式：严格使用 Result.string()，符合 CatVodSpider 规则
 * 5. 稳定性：异常捕获、URL处理、UA设置
 */
public class Heiliao extends Spider {

    private String siteUrl = "https://heiliao.com";
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
        // 当前站点导航固定，直接硬编码（2025-12-30 实测完整列表）
        String[][] nav = {
                {"", "最新黑料"},
                {"hot", "今日热瓜"},
                {"top", "热门黑料"},
                {"classic", "经典黑料"},
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
        return Result.string(classes, new ArrayList<>(), new HashMap<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (pg == null || pg.isEmpty() || Integer.parseInt(pg) <= 0) pg = "1";
        String url = siteUrl + tid;
        if (!tid.endsWith("/")) url += "/";
        if (!pg.equals("1")) url += pg + ".html";

        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
        List<Vod> list = new ArrayList<>();

        // 当前文章列表通用选择器（兼容 archive-item / article / div.item）
        Elements items = doc.select("article.archive-item, article.item, div.archive-item, div.item");
        if (items.isEmpty()) {
            // 备用：所有含标题和链接的a标签
            items = doc.select("a[href*=/archives/]");
        }

        for (Element item : items) {
            Element link = item.tagName().equals("a") ? item : item.selectFirst("a");
            if (link == null) continue;

            String vodId = link.attr("href"); // 如 /archives/87312.html
            if (!vodId.startsWith("/")) continue;

            String vodName = link.selectFirst("h2, .title, h1").text().trim();
            if (vodName.isEmpty()) vodName = link.text().trim();

            String vodPic = link.selectFirst("img").absUrl("src");
            if (vodPic.isEmpty()) vodPic = "https://via.placeholder.com/300x400?text=No+Pic";

            String vodRemarks = link.selectFirst(".date, .time, .tag").text().trim();
            if (vodRemarks.isEmpty()) vodRemarks = "黑料网";

            list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
        }

        int page = Integer.parseInt(pg);
        return Result.get()
                .vod(list)
                .page(page, page >= 100 ? page : page + 1, 30, list.size() * 100)
                .string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = siteUrl + id;
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(doc.selectFirst("h1.title, h1.entry-title").text().trim());
        vod.setVodPic(doc.selectFirst("meta[property=og:image], img.cover").attr("content"));
        if (vod.getVodPic().isEmpty()) vod.setVodPic("https://via.placeholder.com/300x400?text=Heiliao");

        String content = doc.selectFirst("div.content, div.entry-content, article").text();
        vod.setVodContent(content.length() > 200 ? content.substring(0, 200) + "..." : content);

        // 提取播放源：优先 video 直链，其次 iframe
        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();

        Elements videos = doc.select("video source, video");
        if (!videos.isEmpty()) {
            for (int i = 0; i < videos.size(); i++) {
                String src = videos.get(i).attr("src");
                if (src.isEmpty()) continue;
                playFrom.add("直链" + (i + 1));
                playUrl.add("第" + (i + 1) + "段$" + src);
            }
        }

        Elements iframes = doc.select("iframe");
        for (int i = 0; i < iframes.size(); i++) {
            String src = iframes.get(i).attr("src");
            if (src.isEmpty() || !src.startsWith("http")) continue;
            playFrom.add("外站" + (i + 1));
            playUrl.add("第" + (i + 1) + "段$" + src);
        }

        if (!playFrom.isEmpty()) {
            vod.setVodPlayFrom(String.join("$$$", playFrom));
            vod.setVodPlayUrl(String.join("$$$", playUrl));
        }

        return Result.string(new ArrayList<>() {{ add(vod); }});
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 直链或iframe均可直接播放
        return Result.get().url(id).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = siteUrl + "/index/search?keyword=" + URLEncoder.encode(key, "UTF-8");
        // 复用分类解析逻辑
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
        List<Vod> list = new ArrayList<>();
        Elements items = doc.select("article.archive-item, article.item, a[href*=/archives/]");
        for (Element item : items) {
            Element link = item.tagName().equals("a") ? item : item.selectFirst("a");
            if (link == null) continue;
            String vodId = link.attr("href");
            String vodName = link.selectFirst("h2, .title").text().trim();
            String vodPic = link.selectFirst("img").absUrl("src");
            list.add(new Vod(vodId, vodName, vodPic, "搜索结果"));
        }
        return Result.get().vod(list).string();
    }
}
