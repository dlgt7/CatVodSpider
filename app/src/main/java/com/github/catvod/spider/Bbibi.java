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
        if (referer != null && !referer.isEmpty()) {
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
        String content = fetch(SITE_URL);
        Document doc = Jsoup.parse(content);

        Pattern itemPattern = Pattern.compile("\\[([^\\]]+)\\]\\(/detail/\\?(\\d+)\\.html\\)\\s*####\\s*\\[([^\\]]+)\\](?:\\s*主演[:：]\\s*(.+))?");

        Matcher matcher = itemPattern.matcher(content);
        while (matcher.find()) {
            String remarks = matcher.group(1).trim();
            String vodId = matcher.group(2);
            String vodName = matcher.group(3).trim();
            String actors = matcher.group(4) != null ? matcher.group(4).trim() : "";

            String vodPic = "https://via.placeholder.com/300x400?text=" + vodName.substring(0, Math.min(vodName.length(), 10));

            String remark = remarks + (actors.isEmpty() ? "" : " | 主演: " + actors);
            list.add(new Vod(vodId, vodName, vodPic, remark));
        }

        return Result.string(CLASSES, list, FILTERS);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = SITE_URL + "/list/?" + tid + ".html";
        String content = fetch(url);
        List<Vod> list = new ArrayList<>();

        Pattern itemPattern = Pattern.compile("\\[([^\\]]+)\\]\\(/detail/\\?(\\d+)\\.html\\)\\s*####\\s*\\[([^\\]]+)\\](?:\\s*主演[:：]\\s*(.+))?");

        Matcher matcher = itemPattern.matcher(content);
        while (matcher.find()) {
            String remarks = matcher.group(1).trim();
            String vodId = matcher.group(2);
            String vodName = matcher.group(3).trim();
            String actors = matcher.group(4) != null ? matcher.group(4).trim() : "";

            String vodPic = "https://via.placeholder.com/300x400?text=" + vodName.substring(0, Math.min(vodName.length(), 10));

            String remark = remarks + (actors.isEmpty() ? "" : " | 主演: " + actors);
            list.add(new Vod(vodId, vodName, vodPic, remark));
        }

        return Result.get().vod(list).page(1, 1, list.size(), list.size()).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String detailUrl = SITE_URL + "/detail/?" + vodId + ".html";
        String content = fetch(detailUrl);

        Vod vod = new Vod();
        vod.setVodId(vodId);

        Document doc = Jsoup.parse(content);
        String title = doc.selectFirst("h1") != null ? doc.selectFirst("h1").text().trim() : doc.title().replace(" - 4K在线", "").trim();
        vod.setVodName(title);

        vod.setVodPic("https://via.placeholder.com/300x400?text=" + title.substring(0, Math.min(title.length(), 10)));

        vod.setVodYear(extract(content, "年份[:：]\\s*([\\d]{4})"));
        vod.setVodArea(extract(content, "地区[:：]\\s*([\\u4e00-\\u9fa5]+)"));
        vod.setVodDirector(extract(content, "导演[:：]\\s*([^\\r\\n主演]+)"));
        vod.setVodActor(extract(content, "主演[:：]\\s*([^\\r\\n导演]+)"));
        vod.setVodContent(doc.selectFirst("p") != null ? doc.selectFirst("p").text().trim() : "");

        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        List<Vod.VodPlayBuilder.PlayUrl> urls = new ArrayList<>();

        Pattern playPattern = Pattern.compile("\\[([^\\]]+)\\]\\(/video/\\?(" + vodId + "-\\d+-\\d+)\\.html\\)");
        Matcher playMatcher = playPattern.matcher(content);
        int index = 1;
        while (playMatcher.find()) {
            String name = playMatcher.group(1).trim();
            String playId = playMatcher.group(2);
            Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
            pu.name = name.isEmpty() ? "第" + index + "集" : name;
            pu.url = playId;
            urls.add(pu);
            index++;
        }

        if (urls.isEmpty()) {
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

        // 预请求播放页，带详情页Referer
        fetch(playUrl, detailUrl);

        // 源码显示播放器通过外部 /js/player/{pn}.html 加载（pn 来自 script var pn="dytt"）
        // 但静态无法提取真实URL，且无直链
        // 最佳方式：返回播放页URL + parse(1)，让Fongmi App嗅探捕获视频流
        return Result.get().url(playUrl).parse(1).chrome().string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return Result.string(new ArrayList<Vod>());
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }
}
