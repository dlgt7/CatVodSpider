package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.Class;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChangZhang extends Spider {

    private String siteUrl = "https://czzy.top";
    private final Map<String, String> headers = new HashMap<>();

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
        if (!extend.isEmpty()) {
            siteUrl = extend.trim();
            if (siteUrl.endsWith("/")) siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
        }
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        headers.put("Referer", siteUrl + "/");
    }

    private String fetch(String url) {
        return OkHttp.string(url, headers);
    }

    private String post(String url, Map<String, String> data) {
        return OkHttp.post(url, data, headers).getBody();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        String[] typeIds = {"dbtop250", "zuixindianying", "benyueremen", "gcj", "meijutt", "hanjutv", "fanju", "dongmanjuchangban"};
        String[] typeNames = {"豆瓣电影Top250", "最新电影", "热映中", "国产剧", "美剧", "韩剧", "番剧", "动漫"};
        for (int i = 0; i < typeIds.length; i++) {
            classes.add(new Class(typeIds[i], typeNames[i]));
        }
        List<Vod> list = getVideos(siteUrl + "/");
        return Result.get().classes(classes).list(list).string();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, Map<String, String> extend) throws Exception {
        int page = pg.isEmpty() ? 1 : Integer.parseInt(pg);
        String url = siteUrl + "/" + tid + (page > 1 ? "/page/" + page : "");
        List<Vod> list = getVideos(url);
        return Result.get().list(list).string();
    }

    private List<Vod> getVideos(String url) {
        String html = fetch(url);
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("div.bt_img.mi_ne_kd ul li a");
        List<Vod> videos = new ArrayList<>();
        for (Element item : items) {
            String href = item.attr("href");
            String id = href.startsWith("http") ? href : siteUrl + href;
            Element img = item.selectFirst("img");
            String name = img.attr("alt").trim();
            String pic = img.attr("data-original");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = "";
            Element rem = item.selectFirst("div.jidi span");
            if (rem != null) remark = rem.text().trim();
            videos.add(new Vod().setVodId(id).setVodName(name).setVodPic(pic).setVodRemarks(remark));
        }
        return videos;
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String html = fetch(id);
        Document doc = Jsoup.parse(html);

        String title = doc.selectFirst("h1.title").text().trim();
        String pic = doc.selectFirst("div.vod-img img").attr("data-original");
        if (!pic.startsWith("http")) pic = siteUrl + pic;
        String content = doc.selectFirst("div.yp_context p").text().trim();

        Elements lis = doc.select("ul.moviedteail_list li");
        StringBuilder typeSb = new StringBuilder();
        for (Element a : lis.get(0).select("a")) {
            typeSb.append(a.text().trim()).append(" ");
        }
        String typeName = typeSb.toString().trim();

        String area = lis.size() > 1 ? lis.get(1).selectFirst("a").text().trim() : "";
        String year = lis.size() > 2 ? lis.get(2).selectFirst("a").text().trim() : "";

        String director = lis.size() > 4 ? lis.get(4).text().replace("导演：", "").trim() : "";
        String actor = lis.size() > 5 ? lis.get(5).text().replace("主演：", "").trim() : "";

        StringBuilder playUrlSb = new StringBuilder();
        for (Element a : doc.select("div.paly_list_btn a")) {
            String text = a.text().trim();
            String href = a.attr("href");
            if (!href.startsWith("http")) href = siteUrl + href;
            playUrlSb.append(text).append("$").append(href).append("#");
        }
        if (playUrlSb.length() > 0) playUrlSb.deleteCharAt(playUrlSb.length() - 1);

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(title);
        vod.setVodPic(pic);
        vod.setVodContent(content);
        vod.setTypeName(typeName);
        vod.setVodArea(area);
        vod.setVodYear(year);
        vod.setVodDirector(director);
        vod.setVodActor(actor);
        vod.setVodPlayFrom("厂长");
        vod.setVodPlayUrl(playUrlSb.toString());

        return Result.get().list(vod).string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String url = siteUrl + "/xssearch?q=" + key;
        String html = fetch(url);

        // 处理人机验证（加法验证码）
        if (html.contains("人机验证")) {
            Matcher m = Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)").matcher(html);
            if (m.find()) {
                int sum = Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2));
                Map<String, String> data = new HashMap<>();
                data.put("result", String.valueOf(sum));
                headers.put("Referer", url);
                html = post(url, data);
            }
        }

        List<Vod> list = getVideosFromHtml(html);
        return Result.get().list(list).string();
    }

    private List<Vod> getVideosFromHtml(String html) {
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("div.bt_img.mi_ne_kd ul li a");
        List<Vod> videos = new ArrayList<>();
        for (Element item : items) {
            String href = item.attr("href");
            String id = href.startsWith("http") ? href : siteUrl + href;
            Element img = item.selectFirst("img");
            String name = img.attr("alt").trim();
            String pic = img.attr("data-original");
            if (!pic.startsWith("http")) pic = siteUrl + pic;
            String remark = "";
            Element rem = item.selectFirst("div.jidi span");
            if (rem != null) remark = rem.text().trim();
            videos.add(new Vod().setVodId(id).setVodName(name).setVodPic(pic).setVodRemarks(remark));
        }
        return videos;
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String html = fetch(id);
        Document doc = Jsoup.parse(html);

        // 优先 AES 加密（当前主流）
        for (Element script : doc.select("script")) {
            String scriptText = script.html();
            if (scriptText.contains("md5.enc.Utf8")) {
                try {
                    Pattern p = Pattern.compile("\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"");
                    Matcher m = p.matcher(scriptText);
                    if (m.find()) {
                        String encrypted = m.group(1);
                        String keyStr = m.group(2);
                        String ivStr = "1234567890983456";

                        String decrypted = aesDecrypt(encrypted, keyStr, ivStr);
                        String playUrl = decrypted.split("url:\"")[1].split("\"")[0];
                        return Result.get().url(playUrl).string();
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
        }

        // 备用：iframe 逆序十六进制
        Element iframe = doc.selectFirst("div.videoplay iframe");
        if (iframe != null) {
            String src = iframe.attr("src");
            if (!src.startsWith("http")) src = siteUrl + src;
            String iframeHtml = fetch(src);
            try {
                String code = iframeHtml.split("\"data\":\"")[1].split("\"")[0];
                code = new StringBuilder(code).reverse().toString();
                StringBuilder temp = new StringBuilder();
                for (int i = 0; i < code.length(); i += 2) {
                    temp.append((char) Integer.parseInt(code.substring(i, i + 2), 16));
                }
                String decoded = temp.toString();
                int len = decoded.length();
                String playUrl = decoded.substring(0, (len - 7) / 2) + decoded.substring((len - 7) / 2 + 7);
                return Result.get().url(playUrl).string();
            } catch (Exception ignored) {}
        }

        // 兜底直接解析
        return Result.get().url(id).parse(1).string();
    }

    private String aesDecrypt(String encryptedBase64, String keyStr, String ivStr) {
        try {
            byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = ivStr.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);

            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec iv = new IvParameterSpec(ivBytes);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }
}
