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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 韩片网(HanPian)爬虫 - 2025年12月适配版本
 * 当前有效域名: https://www.hanpian.pro
 * 支持通过 extend 参数更换域名
 */
public class HpTv extends Spider {

    private String siteUrl = "https://www.hanpian.pro";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl + "/");
        return headers;
    }

    @Override
    public void init(Context context, String extend) {
        try {
            super.init(context, extend);
            if (extend != null && !extend.isEmpty()) {
                siteUrl = extend.endsWith("/") ? extend.substring(0, extend.length() - 1) : extend;
            }
        } catch (Exception e) {
            // 忽略或记录日志，视项目而定
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            List<Vod> list = new ArrayList<>();
            List<Class> classes = new ArrayList<>();

            classes.add(new Class("movie", "电影"));
            classes.add(new Class("tv", "电视剧"));
            classes.add(new Class("variety", "综艺"));
            classes.add(new Class("anime", "动漫"));
            classes.add(new Class("dz", "动作片"));
            classes.add(new Class("xj", "喜剧片"));
            classes.add(new Class("aq", "爱情片"));
            classes.add(new Class("kh", "科幻片"));
            classes.add(new Class("kb", "恐怖片"));
            classes.add(new Class("jq", "剧情片"));
            classes.add(new Class("zz", "战争片"));
            classes.add(new Class("jl", "纪录片"));

            String html = OkHttp.string(siteUrl + "/", getHeaders());
            Document doc = Jsoup.parse(html);

            Elements items = doc.select(".mo-cols-lays .mo-cols-rows li");
            for (Element item : items) {
                Element a = item.selectFirst("a.mo-situ-pics");
                Element img = item.selectFirst("img");
                Element nameEl = item.selectFirst("a.mo-situ-name");
                Element remarkEl = item.selectFirst("span.mo-situ-rema");

                if (a == null || nameEl == null) continue;

                String pic = img != null ? (img.attr("data-src").isEmpty() ? (img.attr("src").isEmpty() ? img.attr("data-original") : img.attr("src")) : img.attr("data-src")) : "";
                if (pic.startsWith("//")) pic = "https:" + pic;
                else if (pic.startsWith("/")) pic = siteUrl + pic;
                else if (!pic.startsWith("http")) pic = siteUrl + pic;

                String href = a.attr("href");
                String name = nameEl.text();
                String remark = remarkEl != null ? remarkEl.text() : "";

                Matcher m = Pattern.compile("/vod/(\\d+)/").matcher(href);
                if (m.find()) {
                    String id = m.group(1);
                    list.add(new Vod(id, name, pic, remark));
                }
            }

            return Result.string(classes, list);
        } catch (Exception e) {
            return Result.error("首页加载失败: " + e.getMessage());
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            List<Vod> list = new ArrayList<>();
            int pageIndex = Integer.parseInt(pg) - 1;
            String url = siteUrl + "/menu/" + tid + "/" + pageIndex + "/";

            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);

            Elements items = doc.select(".mo-cols-lays .mo-cols-rows li");
            for (Element item : items) {
                Element a = item.selectFirst("a.mo-situ-pics");
                Element img = item.selectFirst("img");
                Element nameEl = item.selectFirst("a.mo-situ-name");
                Element remarkEl = item.selectFirst("span.mo-situ-rema");

                if (a == null || nameEl == null) continue;

                String pic = img != null ? (img.attr("data-src").isEmpty() ? (img.attr("src").isEmpty() ? img.attr("data-original") : img.attr("src")) : img.attr("data-src")) : "";
                if (pic.startsWith("//")) pic = "https:" + pic;
                else if (pic.startsWith("/")) pic = siteUrl + pic;
                else if (!pic.startsWith("http")) pic = siteUrl + pic;

                String href = a.attr("href");
                String name = nameEl.text();
                String remark = remarkEl != null ? remarkEl.text() : "";

                Matcher m = Pattern.compile("/vod/(\\d+)/").matcher(href);
                if (m.find()) {
                    String id = m.group(1);
                    list.add(new Vod(id, name, pic, remark));
                }
            }

            return Result.string(list);
        } catch (Exception e) {
            return Result.error("分类加载失败: " + e.getMessage());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = siteUrl + "/vod/" + id + "/";
            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);

            Vod vod = new Vod();
            vod.setVodId(id);

            Element titleEl = doc.selectFirst("h1 a");
            if (titleEl != null) vod.setVodName(titleEl.text().trim());

            Element picEl = doc.selectFirst("a.mo-situ-pics img");
            if (picEl != null) {
                String pic = picEl.attr("data-src").isEmpty() ? (picEl.attr("src").isEmpty() ? picEl.attr("data-original") : picEl.attr("src")) : picEl.attr("data-src");
                if (pic.startsWith("//")) pic = "https:" + pic;
                else if (pic.startsWith("/")) pic = siteUrl + pic;
                else if (!pic.startsWith("http")) pic = siteUrl + pic;
                vod.setVodPic(pic);
            }

            Elements infoLis = doc.select(".mo-deta-info ul li");
            for (Element li : infoLis) {
                String text = li.text();
                if (text.contains("年份:")) {
                    Matcher m = Pattern.compile("\\d{4}").matcher(text);
                    if (m.find()) vod.setVodYear(m.group());
                } else if (text.contains("主演:")) {
                    vod.setVodActor(text.replace("主演:", "").trim());
                } else if (text.contains("导演:")) {
                    vod.setVodDirector(text.replace("导演:", "").trim());
                } else if (text.contains("分类:")) {
                    vod.setVodTag(text.replace("分类:", "").trim());
                }
            }

            Element descEl = doc.selectFirst(".mo-word-info");
            if (descEl != null) vod.setVodContent(descEl.text().trim());

            StringBuilder from = new StringBuilder();
            StringBuilder playUrl = new StringBuilder();

            Elements sources = doc.select(".mo-sort-head h2 a.mo-movs-btns");
            Elements lists = doc.select(".mo-movs-item");

            for (int i = 0; i < sources.size() && i < lists.size(); i++) {
                String sourceName = sources.get(i).text().trim();
                from.append(sourceName).append("$$$");

                Elements eps = lists.get(i).select("a");
                for (int j = 0; j < eps.size(); j++) {
                    Element ep = eps.get(j);
                    String epName = ep.text().trim();
                    String epHref = ep.attr("href");
                    playUrl.append(epName).append("$").append(epHref);
                    playUrl.append(j < eps.size() - 1 ? "#" : "$$$");
                }
            }

            if (from.length() > 3) from.delete(from.length() - 3, from.length());
            if (playUrl.length() > 3) playUrl.delete(playUrl.length() - 3, playUrl.length());

            vod.setVodPlayFrom(from.toString());
            vod.setVodPlayUrl(playUrl.toString());

            return Result.string(vod);
        } catch (Exception e) {
            return Result.error("详情加载失败: " + e.getMessage());
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            List<Vod> list = new ArrayList<>();
            String url = siteUrl + "/search/" + URLEncoder.encode(key, "UTF-8") + "/";

            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);

            Elements items = doc.select(".mo-cols-lays .mo-cols-rows li");
            for (Element item : items) {
                Element a = item.selectFirst("a.mo-situ-pics");
                Element img = item.selectFirst("img");
                Element nameEl = item.selectFirst("a.mo-situ-name");
                Element remarkEl = item.selectFirst("span.mo-situ-rema");

                if (a == null || nameEl == null) continue;

                String pic = img != null ? (img.attr("data-src").isEmpty() ? (img.attr("src").isEmpty() ? img.attr("data-original") : img.attr("src")) : img.attr("data-src")) : "";
                if (pic.startsWith("//")) pic = "https:" + pic;
                else if (pic.startsWith("/")) pic = siteUrl + pic;
                else if (!pic.startsWith("http")) pic = siteUrl + pic;

                String href = a.attr("href");
                String name = nameEl.text();
                String remark = remarkEl != null ? remarkEl.text() : "";

                Matcher m = Pattern.compile("/vod/(\\d+)/").matcher(href);
                if (m.find()) {
                    String id = m.group(1);
                    list.add(new Vod(id, name, pic, remark));
                }
            }

            return Result.string(list);
        } catch (Exception e) {
            return Result.error("搜索失败: " + e.getMessage());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String playUrl = id.startsWith("http") ? id : (id.startsWith("/") ? siteUrl + id : siteUrl + "/" + id);

            HashMap<String, String> headers = getHeaders();
            headers.put("Referer", siteUrl + "/");

            return Result.get().url(playUrl).header(headers).parse(1).string();
        } catch (Exception e) {
            return Result.error("播放失败: " + e.getMessage());
        }
    }
}