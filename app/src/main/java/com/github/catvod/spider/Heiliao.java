package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 黑料网爬虫 (Heiliao)
 * 适配 111.txt(首页) 和 222.txt(详情页) 源码
 */
public class Heiliao extends Spider {

    private String siteUrl = "https://9dcw7.qkkzpxw.com/";
    private Map<String, String> headers;

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        headers = new HashMap<>();
        // 使用移动端UA，因为源码中有很多针对Android/iOS的判断
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36");
        if (!TextUtils.isEmpty(extend)) {
            siteUrl = extend;
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            String html = OkHttp.string(siteUrl, headers);
            Document doc = Jsoup.parse(html);
            List<Class> classes = new ArrayList<>();

            // 对应111.txt源码中的滑块导航
            Elements items = doc.select("a.slider-item");
            for (Element item : items) {
                String href = item.attr("href");
                String name = item.text().trim();
                // 过滤掉首页和其他非分类链接
                if (href.contains("/category/")) {
                    String id = href.replace(siteUrl, "").replaceAll("^/+", "");
                    classes.add(new Class(id, name));
                }
            }
            return Result.string(classes);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = Integer.parseInt(pg);
            // 构造分页：如 category/1/2.html
            String url = siteUrl + tid.replace(".html", "") + "/" + page + ".html";
            List<Vod> videos = getVideos(url);
            return Result.get().vod(videos).string();
        } catch (Exception e) {
            return "";
        }
    }

    private List<Vod> getVideos(String url) {
        List<Vod> videos = new ArrayList<>();
        try {
            String html = OkHttp.string(url, headers);
            Document doc = Jsoup.parse(html);
            // 匹配源码中的视频列表项
            Elements items = doc.select("a.cursor-pointer");
            for (Element item : items) {
                String href = item.attr("href").replaceAll("^/+", "");
                String title = item.select("div.title").text().trim();
                
                // 重点：源码中图片是在 onload 的 loadImg 函数里
                // <img ... onload="loadImg(this,'https://pic.xxx.cn/...')">
                String pic = "";
                String onload = item.select("img").attr("onload");
                Pattern p = Pattern.compile("loadImg\\(this,'(.*?)'\\)");
                Matcher m = p.matcher(onload);
                if (m.find()) {
                    pic = m.group(1);
                } else {
                    pic = item.select("img").attr("src");
                }

                if (!href.isEmpty() && !title.isEmpty()) {
                    videos.add(new Vod(href, title, pic, ""));
                }
            }
        } catch (Exception ignored) {}
        return videos;
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = siteUrl + id;
            String html = OkHttp.string(url, headers);
            Document doc = Jsoup.parse(html);

            String name = doc.select("h1").text().trim();
            String pic = doc.select("div.detail-content img").attr("src");
            String content = doc.select("div.detail-content").text().trim();

            // 对应222.txt源码：提取 DPlayer 配置中的 url
            String playUrl = "";
            Pattern p = Pattern.compile("url: ['\"](https?.*?\\.m3u8.*?)['\"]");
            Matcher m = p.matcher(html);
            if (m.find()) {
                playUrl = m.group(1);
            }

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(name);
            vod.setVodPic(pic);
            vod.setVodContent(content);
            vod.setVodPlayFrom("黑料直连");
            vod.setVodPlayUrl("立即播放$" + playUrl);

            return Result.string(vod);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            String url = siteUrl + "/index/search_article";
            Map<String, String> params = new HashMap<>();
            params.put("word", key);
            // 使用 OkHttp.post 发送搜索请求
            String response = OkHttp.post(url, params, headers).getBody();
            JSONObject json = new JSONObject(response);
            JSONArray list = json.getJSONObject("data").getJSONArray("list");

            List<Vod> videos = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                videos.add(new Vod(
                    "archives/" + item.getString("id") + ".html",
                    item.getString("title"),
                    item.getString("thumb"),
                    item.optString("created_date", "")
                ));
            }
            return Result.get().vod(videos).string();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        // id 就是解析出来的 m3u8 地址
        return Result.get().url(id).header(headers).string();
    }
}
