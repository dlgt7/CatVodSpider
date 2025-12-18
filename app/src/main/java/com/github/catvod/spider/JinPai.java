package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JinPai extends Spider {

    private static final String siteUrl = "https://www.cfkj86.com";  // 优先这个，稳定

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));
        List<Vod> vods = new ArrayList<>();

        // 放宽选择器，兼容多种模板
        Elements items = doc.select(".module-item, .vodlist_item, .hl-item-thumb, .fed-list-item");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String vid = a.attr("href");
            String name = a.attr("title");
            if (name.isEmpty()) name = item.selectFirst(".module-item-title, h3, .title").text();
            String pic = item.selectFirst("img").attr("data-original");
            if (pic.isEmpty()) pic = item.selectFirst("img").attr("data-src");
            if (pic.isEmpty()) pic = item.selectFirst("img").attr("src");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = item.selectFirst(".module-item-note, .module-item-text, .pic-text, .remark").text();
            if (!name.isEmpty()) {
                vods.add(new Vod(vid, name, pic, remark));
            }
        }

        return Result.string(classes, vods);
    }

    // categoryContent、detailContent、searchContent 同理放宽选择器（略，复制上次代码即可）

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return Result.get().url(siteUrl + id).header(getHeaders()).string();  // 嗅探
    }
}
