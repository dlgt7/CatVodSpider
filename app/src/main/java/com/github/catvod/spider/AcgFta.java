package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ACG饭团爬虫（2025年12月最新适配版）
 * 网站: https://acgfta.com/
 * 
 * 当前网站特点（2025年底）：
 * - 首页：按星期分组的纯文本列表，格式如 [标题](/anime/xxxx.html) 更新至xx集
 * - 分类页（最近更新、榜单等）：纯文本列表，<a href="/anime/xxxx.html">标题</a> 备注
 * - 无图片、无卡片结构
 * - 详情页：结构已变化（旧选择器失效），本版暂不解析详情（直接用playerContent抛播放页）
 * - 播放：直接返回剧集页URL，由TVBox嗅探或解析器处理
 */
public class AcgFta extends Spider {

    private static String siteUrl = "https://acgfta.com";
    private static final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[(.*?)\\]\\(/anime/(\\d+\\.html)\\)");

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Referer", siteUrl);
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend)) {
            siteUrl = extend;
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();

        classes.add(new Class("ft/recent.html", "最近更新"));
        classes.add(new Class("ft/leaderboard.html", "榜单"));
        classes.add(new Class("ft/top-movie.html", "剧场版"));
        classes.add(new Class("ft/archive.html", "新番归档"));

        // 首页解析：纯文本，按星期分组，使用正则提取 [标题](/anime/xxxx.html)
        String html = OkHttp.string(siteUrl, getHeaders());
        Document doc = Jsoup.parse(html);

        Matcher matcher = LINK_PATTERN.matcher(doc.text());
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String vodId = "/anime/" + matcher.group(2);
            String remark = ""; // 备注在链接后文本中，暂不精确提取（可后续优化）

            list.add(new Vod(vodId, name, "", remark)); // 无图片
        }

        // 如果正则没抓全，fallback 用 a 链接
        if (list.isEmpty()) {
            Elements links = doc.select("a[href*=/anime/]");
            for (Element a : links) {
                String name = a.text().trim();
                if (!name.isEmpty() && !name.equals("饭团动漫")) {
                    list.add(new Vod(a.attr("href"), name, "", ""));
                }
            }
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "/" + tid;
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        // 分类页：直接抓所有 /anime/ 开头的 a 链接
        Elements links = doc.select("a[href*=/anime/]");
        for (Element a : links) {
            String name = a.text().trim();
            String href = a.attr("href");
            if (!name.isEmpty() && href.startsWith("/anime/")) {
                // 尝试从父元素或后续文本提取备注
                String remark = "";
                Element parent = a.parent();
                if (parent != null) {
                    String nextText = parent.ownText().trim(); // a 标签外的文本
                    if (nextText.contains("更新") || nextText.contains("集") || nextText.contains("全")) {
                        remark = nextText;
                    }
                }

                list.add(new Vod(href, name, "", remark));
            }
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids.isEmpty()) return "";

        String vodId = ids.get(0);
        String url = vodId.startsWith("http") ? vodId : siteUrl + vodId;

        // 由于详情页结构大变，暂不解析标题、图片、简介等
        // 直接返回空Vod，但保留vodId，以便播放时跳转
        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName("加载中..."); // 客户端会自动加载播放页
        vod.setVodPic("");
        vod.setVodPlayFrom("饭团动漫");
        vod.setVodPlayUrl("播放$/play/" + vodId.substring(vodId.lastIndexOf("/") + 1)); // 伪造，实际playerContent处理

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id 在 detail 中伪造，这里还原真实剧集页URL
        String realId = id.contains("/anime/") ? id : "/anime/" + id.replace(".html", ".html");
        String url = realId.startsWith("http") ? realId : siteUrl + realId;

        // 直接返回剧集页，由TVBox内置嗅探或webview解析播放
        return Result.get().url(url).parse(1).string(); // parse(1) 启用嗅探
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();

        String url = siteUrl + "/search.html?wd=" + URLEncoder.encode(key, "UTF-8");
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements links = doc.select("a[href*=/anime/]");
        for (Element a : links) {
            String name = a.text().trim();
            if (name.contains(key)) {
                list.add(new Vod(a.attr("href"), name, "", ""));
            }
        }

        return Result.string(list);
    }
}
