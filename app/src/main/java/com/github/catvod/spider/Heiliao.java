package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 黑料网爬虫 (HeiLiao)
 * 修复了字段引用、方法名及OkHttp调用问题
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
            return Result.string(classes, null);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = (TextUtils.isEmpty(pg) || Integer.parseInt(pg) <= 0) ? 1 : Integer.parseInt(pg);
            String url = siteUrl + "/" + tid + "/" + page + ".html";
            List<Vod> videos = getVideos(url);
            return Result.string(videos);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = siteUrl + id;
            String html = request(url);
            Document doc = Jsoup.parse(html);

            String content = "";
            Elements contentElems = doc.select("div.detail-page > div > p");
            if (contentElems.size() >= 3) {
                content = contentElems.get(2).text();
            }

            StringBuilder playUrls = new StringBuilder();
            Elements dplayerElems = doc.select("div.dplayer");
            for (int i = 0; i < dplayerElems.size(); i++) {
                Element item = dplayerElems.get(i);
                String configText = item.attr("config");
                if (!TextUtils.isEmpty(configText)) {
                    try {
                        JSONObject config = new JSONObject(configText);
                        String videoUrl = config.getJSONObject("video").getString("url");
                        if (i > 0) playUrls.append("#");
                        playUrls.append("播放列表").append(i + 1).append("$").append(videoUrl);
                    } catch (JSONException ignored) {}
                }
            }

            Vod vod = new Vod();
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
            postData.put("page", "1");

            String response = OkHttp.post(url, postData, headers).getBody();
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray list = jsonResponse.getJSONObject("data").getJSONArray("list");

            List<Vod> videos = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                videos.add(new Vod("/archives/" + item.getString("id") + ".html", item.getString("title"), item.getString("thumb"), item.getString("created_date")));
            }
            return Result.string(videos);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).parse(0).header(headers).string();
    }

    private List<Vod> getVideos(String url) {
        List<Vod> videos = new ArrayList<>();
        try {
            String html = request(url);
            Document doc = Jsoup.parse(html);

            Elements cursorPointers = doc.select("a.cursor-pointer");
            for (Element item : cursorPointers) {
                String href = item.attr("href");
                String title = item.select("div.title").text();
                String pic = item.select("img").attr("src");

                if (href.startsWith("/archives") && !TextUtils.isEmpty(title)) {
                    imgObj.put(href, pic);
                    videos.add(new Vod(href, title, getProxyUrl() + base64Encode(href), ""));
                }
            }
        } catch (Exception ignored) {}
        return videos;
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        String what = params.get("what");
        String url = params.get("url");
        if ("img".equals(what)) {
            String decodedId = base64Decode(url);
            if (imgObj.containsKey(decodedId)) {
                String base64Img = imgObj.get(decodedId).replaceFirst("^data:image/\\w+;base64,", "");
                byte[] content = Base64.decode(base64Img, Base64.DEFAULT);
                return new Object[]{200, "image/jpeg", content};
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
