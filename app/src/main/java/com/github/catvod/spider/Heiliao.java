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

/**
 * 黑料网爬虫 (Heiliao)
 * 文件名必须为 Heiliao.java
 */
public class Heiliao extends Spider {

    private String siteUrl = "https://9dcw7.qkkzpxw.com/";
    private Map<String, String> headers;
    private Map<String, String> imgObj;

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36");
        imgObj = new HashMap<>();

        if (!TextUtils.isEmpty(extend)) {
            siteUrl = extend;
        }
    }

    private String request(String reqUrl) {
        try {
            return OkHttp.string(reqUrl, headers);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            String html = request(siteUrl);
            Document doc = Jsoup.parse(html);

            List<Class> classes = new ArrayList<>();
            Elements sliderItems = doc.select("a.slider-item");
            for (Element item : sliderItems) {
                String typeId = item.attr("href").replace(".html", "");
                if ("/".equals(typeId)) {
                    typeId = "/category/0";
                }
                String typeName = item.select("span").text();
                if (!TextUtils.isEmpty(typeName)) {
                    classes.add(new Class(typeId, typeName));
                }
            }
            // 使用 Result.string(classes) 避免 null 导致的歧义报错
            return Result.string(classes);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = (TextUtils.isEmpty(pg) || Integer.parseInt(pg) <= 0) ? 1 : Integer.parseInt(pg);
            String url = siteUrl + (tid.startsWith("/") ? "" : "/") + tid + "/" + page + ".html";
            List<Vod> videos = getVideos(url);
            // 使用 Builder 模式确保类型安全
            return Result.get().vod(videos).string();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = siteUrl + (id.startsWith("/") ? "" : "/") + id;
            String html = request(url);
            Document doc = Jsoup.parse(html);

            String content = "";
            Elements pTags = doc.select("div.detail-page p");
            if (pTags.size() >= 3) content = pTags.get(2).text();

            StringBuilder playUrls = new StringBuilder();
            Elements dplayer = doc.select("div.dplayer");
            for (int i = 0; i < dplayer.size(); i++) {
                String configText = dplayer.get(i).attr("config");
                if (!TextUtils.isEmpty(configText)) {
                    JSONObject config = new JSONObject(configText);
                    String videoUrl = config.getJSONObject("video").getString("url");
                    if (playUrls.length() > 0) playUrls.append("#");
                    playUrls.append("播放列表").append(i + 1).append("$").append(videoUrl);
                }
            }

            // 使用 Vod 的 Builder 或 构造函数
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodContent(content);
            vod.setVodPlayFrom("黑料直连");
            vod.setVodPlayUrl(playUrls.toString());

            return Result.string(vod);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            String url = siteUrl + "/index/search_article";
            Map<String, String> postData = new HashMap<>();
            postData.put("word", key);

            // 通过 OkHttp 获取搜索结果
            String response = OkHttp.post(url, postData, headers).getBody();
            JSONObject json = new JSONObject(response);
            JSONArray list = json.getJSONObject("data").getJSONArray("list");

            List<Vod> videos = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                // 使用 Vod(id, name, pic, remarks) 构造函数
                videos.add(new Vod(
                    "/archives/" + item.getString("id") + ".html",
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
        // 直接返回播放地址
        return Result.get().url(id).header(headers).string();
    }

    private List<Vod> getVideos(String url) {
        List<Vod> videos = new ArrayList<>();
        try {
            String html = request(url);
            Document doc = Jsoup.parse(html);
            Elements items = doc.select("a.cursor-pointer");
            for (Element item : items) {
                String href = item.attr("href");
                String title = item.select("div.title").text();
                String pic = item.select("img").attr("src");
                if (href.contains("/archives") && !TextUtils.isEmpty(title)) {
                    imgObj.put(href, pic);
                    // 封面通过 proxy 转换
                    videos.add(new Vod(href, title, getProxyUrl() + base64Encode(href), ""));
                }
            }
        } catch (Exception ignored) {}
        return videos;
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        String url = params.get("url");
        if (!TextUtils.isEmpty(url)) {
            String decodedId = base64Decode(url);
            if (imgObj.containsKey(decodedId)) {
                String imgData = imgObj.get(decodedId);
                if (imgData.contains("base64,")) {
                    String base64Img = imgData.split("base64,")[1];
                    byte[] content = Base64.decode(base64Img, Base64.DEFAULT);
                    return new Object[]{200, "image/jpeg", content};
                }
            }
        }
        return null;
    }

    private String getProxyUrl() {
        return "proxy://do=img&url=";
    }

    private String base64Encode(String text) {
        return Base64.encodeToString(text.getBytes(), Base64.NO_WRAP);
    }

    private String base64Decode(String text) {
        return new String(Base64.decode(text, Base64.NO_WRAP));
    }
}
