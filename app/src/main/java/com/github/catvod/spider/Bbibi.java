package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bbibi extends Spider {

    private static final String SITE_URL = "https://www.bbibi.cc";

    private static final List<Class> CLASSES = Arrays.asList(
            new Class("1", "电影"),
            new Class("2", "电视剧"),
            new Class("3", "综艺"),
            new Class("4", "动漫")
    );

    private final LinkedHashMap<String, List<Filter>> FILTERS = new LinkedHashMap<>(); // 无过滤器

    private Map<String, String> getHeader(String referer) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        if (referer != null) {
            headers.put("Referer", referer);
        }
        return headers;
    }

    private String fetch(String url, String referer) throws Exception {
        return OkHttp.string(url, getHeader(referer));
    }

    private String fetch(String url) throws Exception {
        return fetch(url, SITE_URL);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(fetch(SITE_URL));

        // 使用正则匹配纯文本列表格式
        Pattern itemPattern = Pattern.compile("\\[([^\\]]*)\\]\\s*\\(/detail/\\?(\\d+)\\.html\\)\\s*####\\s*\\[([^\\]]+)\\](?:\\s*主演[:：](.*))?");
        Matcher matcher = itemPattern.matcher(doc.text());

        while (matcher.find()) {
            String remarks = matcher.group(1).trim(); // 如 "9.5 正片" 或更新状态
            String vodId = matcher.group(2);
            String vodName = matcher.group(3).trim();
            String actors = matcher.group(4) != null ? matcher.group(4).trim() : "";

            String vodPic = "https://via.placeholder.com/300x400?text=" + vodName.substring(0, Math.min(10, vodName.length()));

            String remark = remarks + (actors.isEmpty() ? "" : " 主演：" + actors);
            list.add(new Vod(vodId, vodName, vodPic, remark));
        }

        return Result.string(CLASSES, list, FILTERS);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 站点无明显分页，分类页固定
        String url = SITE_URL + "/list/?" + tid + ".html";
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(fetch(url));

        Pattern itemPattern = Pattern.compile("\\[([^\\]]*)\\]\\s*\\(/detail/\\?(\\d+)\\.html\\)\\s*####\\s*\\[([^\\]]+)\\](?:\\s*主演[:：](.*))?");
        Matcher matcher = itemPattern.matcher(doc.text());

        while (matcher.find()) {
            String remarks = matcher.group(1).trim();
            String vodId = matcher.group(2);
            String vodName = matcher.group(3).trim();
            String actors = matcher.group(4) != null ? matcher.group(4).trim() : "";

            String vodPic = "https://via.placeholder.com/300x400?text=" + vodName.substring(0, Math.min(10, vodName.length()));

            String remark = remarks + (actors.isEmpty() ? "" : " 主演：" + actors);
            list.add(new Vod(vodId, vodName, vodPic, remark));
        }

        return Result.get().vod(list).page(1, 1, list.size(), list.size()).string(); // 无分页
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String detailUrl = SITE_URL + "/detail/?" + vodId + ".html";
        Document doc = Jsoup.parse(fetch(detailUrl));

        Vod vod = new Vod();
        vod.setVodId(vodId);

        // 标题从文本或h1提取
        String title = doc.selectFirst("h1") != null ? doc.selectFirst("h1").text().trim() : doc.title().replace(" - 4K在线", "").trim();
        vod.setVodName(title);

        vod.setVodPic("https://via.placeholder.com/300x400?text=" + title.substring(0, Math.min(10, title.length())));

        String text = doc.text();

        vod.setVodYear(extract(text, "年份[:：]\\s*([\\d]{4})"));
        vod.setVodArea(extract(text, "地区[:：]\\s*([^\\s主演导演]+)"));
        vod.setVodDirector(extract(text, "导演[:：]\\s*([^主演]+)"));
        vod.setVodActor(extract(text, "主演[:：]\\s*([^导演]+)"));
        vod.setVodContent(doc.selectFirst("p, .desc") != null ? doc.selectFirst("p, .desc").text().trim() : "");

        // 播放列表：提取 /video/?{id}-{sid}-{nid}.html 链接
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        List<Vod.VodPlayBuilder.PlayUrl> urls = new ArrayList<>();

        Pattern playPattern = Pattern.compile("\\[/video/\\?(" + vodId + "-\\d+-\\d+)\\.html\\]");
        Matcher playMatcher = playPattern.matcher(text);
        int epIndex = 1;
        while (playMatcher.find()) {
            String playId = playMatcher.group(1);
            Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
            pu.name = "第" + epIndex + "集";
            pu.url = playId;
            urls.add(pu);
            epIndex++;
        }

        if (urls.isEmpty()) {
            // 默认单集
            Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
            pu.name = "正片";
            pu.url = vodId + "-0-0";
            urls.add(pu);
        }

        builder.append("正片", urls);
        Vod.VodPlayBuilder.BuildResult res = builder.build();
        vod.setVodPlayFrom(res.vodPlayFrom);
        vod.setVodPlayUrl(res.vodPlayUrl);

        return Result.string(Arrays.asList(vod));
    }

    private String extract(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String detailUrl = SITE_URL + "/detail/?" + id.split("-")[0] + ".html";
        String playUrl = SITE_URL + "/video/?" + id + ".html";

        // 使用详情页作为Referer请求播放页
        fetch(playUrl, detailUrl); // 预请求，模拟跳转（可能设置Cookie或触发）

        // 返回播放页URL + parse(1) + chrome，让App嗅探/内置解析器处理
        return Result.get().url(playUrl).parse(1).chrome().string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        // 站点无搜索功能，暂返回空
        return Result.string(new ArrayList<Vod>());
    }
}
