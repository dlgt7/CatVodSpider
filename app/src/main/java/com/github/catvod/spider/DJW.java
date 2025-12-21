package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
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

public class DJW extends Spider {

    private static final String siteUrl = "https://www.djw123.com";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");
        headers.put("Referer", siteUrl + "/");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        Document doc = Jsoup.parse(OkHttp.string(siteUrl + "/", getHeaders()));

        // 分类（常见短剧分类，从导航或首页提取）
        String[] typeNames = {"短剧", "女频恋爱", "古装仙侠", "现代都市", "反转爽", "脑洞悬疑", "年代穿越"};
        String[] typeIds = {"duanju", "nvpinlianai", "guzhuangxianxia", "xiandaidushi", "fanzhuanshuang", "naodongxuanyi", "niandaichuanyue"};
        for (int i = 0; i < typeNames.length; i++) {
            classes.add(new Class(typeIds[i], typeNames[i]));
        }

        // 首页推荐视频（从模块提取）
        List<Vod> vods = new ArrayList<>();
        Elements items = doc.select("div.module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.module-poster");
            if (a == null) continue;
            String title = a.attr("title");
            String vodId = a.attr("href").replace("/vod/", "").replace(".html", "");
            String pic = item.selectFirst("img").attr("data-original");
            if (pic.isEmpty()) pic = item.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = item.selectFirst("div.module-item-note").text();

            vods.add(new Vod(vodId, title, pic, remark));
        }

        return Result.string(classes, vods, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateUrl = siteUrl + "/type/" + tid + (pg.equals("1") ? ".html" : "-" + pg + ".html");
        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeaders()));

        List<Vod> vods = new ArrayList<>();
        Elements items = doc.select("div.module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.module-poster");
            if (a == null) continue;
            String title = a.attr("title");
            String vodId = a.attr("href").replace("/vod/", "").replace(".html", "");
            String pic = item.selectFirst("img").attr("data-original");
            if (pic.isEmpty()) pic = item.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = item.selectFirst("div.module-item-note").text();

            vods.add(new Vod(vodId, title, pic, remark));
        }

        // 分页（假设20页上限）
        int page = Integer.parseInt(pg);
        int pageCount = page + 1;

        return Result.get().vod(vods).page(page, pageCount, 24, vods.size() + 24).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String detailUrl = siteUrl + "/vod/" + id + ".html";
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeaders()));

        String title = doc.selectFirst("h1").text();
        String pic = doc.selectFirst("img.lazy").attr("data-original");
        if (!pic.startsWith("http")) pic = siteUrl + pic;

        Vod vod = new Vod(id, title, pic);

        // 播放线路
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        Elements tabs = doc.select("div.module-tab div.module-tab-item");
        Elements playLists = doc.select("div.module-play-list");

        for (int i = 0; i < tabs.size() && i < playLists.size(); i++) {
            String playFrom = tabs.get(i).text().trim();
            Elements eps = playLists.get(i).select("a");

            List<Vod.VodPlayBuilder.PlayUrl> playUrls = new ArrayList<>();
            for (Element ep : eps) {
                String epName = ep.text().trim();
                String epUrl = ep.attr("href"); // /play/xxx.html

                Vod.VodPlayBuilder.PlayUrl pu = new Vod.VodPlayBuilder.PlayUrl();
                pu.name = epName;
                pu.url = siteUrl + epUrl;
                playUrls.add(pu);
            }
            builder.append(playFrom, playUrls);
        }

        Vod.VodPlayBuilder.BuildResult br = builder.build();
        vod.setVodPlayFrom(br.vodPlayFrom);
        vod.setVodPlayUrl(br.vodPlayUrl);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = id; // id 是完整 /play/xxx.html
        Document doc = Jsoup.parse(OkHttp.string(playUrl, getHeaders()));

        // 提取 player_ script
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String content = script.html();
            if (content.contains("player_") && content.contains("url")) {
                try {
                    int start = content.indexOf("{");
                    int end = content.lastIndexOf("}") + 1;
                    String json = content.substring(start, end);
                    String url = json.split("\"url\":\"")[1].split("\"")[0].replace("\\/", "/");

                    if (url.startsWith("http")) {
                        return Result.get().url(url).m3u8().string();
                    }
                } catch (Exception ignored) {}
            }
        }

        // 兜底解析
        return Result.get().parse(1).url(playUrl).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String searchUrl = siteUrl + "/search.php?searchword=" + URLEncoder.encode(key, "UTF-8");
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeaders()));

        List<Vod> vods = new ArrayList<>();
        Elements items = doc.select("div.module-item");
        for (Element item : items) {
            Element a = item.selectFirst("a.module-poster");
            if (a == null) continue;
            String title = a.attr("title");
            String vodId = a.attr("href").replace("/vod/", "").replace(".html", "");
            String pic = item.selectFirst("img").attr("data-original");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = item.selectFirst("div.module-item-note").text();

            vods.add(new Vod(vodId, title, pic, remark));
        }

        return Result.string(vods);
    }
}
