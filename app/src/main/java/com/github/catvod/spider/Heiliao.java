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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 黑料网爬虫 (HeiLiao) - Java版实现
 * 对应原JS文件: heiliao_open.js
 */
public class HeiLiao extends Spider {

    private String siteUrl = "https://9dcw7.qkkzpxw.com/";
    private String siteKey = "";
    private int siteType = 0;
    private Map<String, String> headers;
    private Map<String, String> imgObj;

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36");
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

            JSONObject result = new JSONObject();
            result.put("class", Result.objectArray(classes));
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String homeVideoContent() {
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            if (TextUtils.isEmpty(pg) || Integer.parseInt(pg) <= 0) {
                pg = "1";
            }
            String url = siteUrl + "/" + tid + "/" + pg + ".html";
            List<Vod> videos = getVideos(url);

            JSONObject result = new JSONObject();
            result.put("list", Result.objectArray(videos));
            result.put("page", Integer.parseInt(pg));
            result.put("limit", videos.size());
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
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
                        playUrls.append((i + 1)).append("$").append(videoUrl);
                    } catch (JSONException e) {
                        // 忽略解析错误
                    }
                }
            }

            JSONObject vod = new JSONObject();
            vod.put("vod_content", content);
            vod.put("vod_play_from", "Leospring");
            vod.put("vod_play_url", playUrls.toString());

            JSONObject result = new JSONObject();
            JSONArray list = new JSONArray();
            list.put(vod);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, String quick, String pg) {
        try {
            String url = siteUrl + "/index/search_article";
            if (TextUtils.isEmpty(pg) || Integer.parseInt(pg) <= 0) {
                pg = "1";
            }

            Map<String, String> postData = new HashMap<>();
            postData.put("word", key);
            postData.put("page", pg);

            String response = OkHttp.post(url, postData, headers).getBody();
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray list = jsonResponse.getJSONObject("data").getJSONArray("list");

            List<Vod> videos = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                Vod vod = new Vod();
                vod.setVod_id("/archives/" + item.getString("id") + ".html");
                vod.setVod_name(item.getString("title"));
                vod.setVod_pic(item.getString("thumb"));
                vod.setVod_remarks(item.getString("created_date"));
                videos.add(vod);
            }

            JSONObject result = new JSONObject();
            result.put("list", Result.objectArray(videos));
            result.put("page", Integer.parseInt(pg));
            result.put("limit", videos.size());
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", id);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private List<Vod> getVideos(String url) {
        List<Vod> videos = new ArrayList<>();
        try {
            String html = request(url);
            Document doc = Jsoup.parse(html);
            imgObj = new HashMap<>();

            Elements cursorPointers = doc.select("a.cursor-pointer");
            for (int i = 0; i < cursorPointers.size(); i++) {
                Element item = cursorPointers.get(i);
                String href = item.attr("href");
                String title = item.select("div.title").text();
                String pic = item.select("img").attr("src");

                if (href.startsWith("/archives") && !TextUtils.isEmpty(title)) {
                    imgObj.put(href, pic);
                    Vod vod = new Vod();
                    vod.setVod_id(href);
                    vod.setVod_name(title);
                    vod.setVod_pic(getProxyUrl() + base64Encode(href));
                    videos.add(vod);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return videos;
    }

    // 代理功能处理图片
    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        String what = params.get("what");
        String url = params.get("url");
        if ("img".equals(what)) {
            if (url.startsWith("http")) {
                // 实际的图片代理处理逻辑
                String decodedUrl = url; // 从proxy URL中获取原始图片URL
                Map<String, String> imgHeaders = new HashMap<>();
                imgHeaders.put("Referer", "https://api.douban.com/");
                imgHeaders.put("User-Agent", Util.MOBILE_UA);

                byte[] content = OkHttp.bytes(decodedUrl, imgHeaders);
                return new Object[]{200, "image/jpeg", content};
            } else {
                // 从缓存获取图片
                if (imgObj.containsKey(url)) {
                    String base64Img = imgObj.get(url).replaceFirst("^data:image/\\w+;base64,", "");
                    byte[] content = Base64.decode(base64Img, Base64.DEFAULT);
                    return new Object[]{200, "image/jpeg", content};
                }
            }
        }
        return null;
    }

    private String getProxyUrl() {
        // 返回代理URL前缀
        return "proxy://do=img&siteKey=heiliao&url=";
    }

    private String base64Encode(String text) {
        return Base64.encodeToString(text.getBytes(), Base64.NO_WRAP);
    }

    private String base64Decode(String text) {
        return new String(Base64.decode(text, Base64.NO_WRAP));
    }

    // AES解密方法
    private String picdecrypt(String str, String keyStr, String ivStr) {
        try {
            // 如果没有提供密钥和IV，则直接返回原字符串
            if (keyStr == null || keyStr.isEmpty() || ivStr == null || ivStr.isEmpty()) {
                return str;
            }
            byte[] keyBytes = keyStr.getBytes();
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivStr.getBytes());
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.decode(str, Base64.NO_WRAP));
            return new String(decrypted);
        } catch (Exception e) {
            e.printStackTrace();
            return str;
        }
    }
}
