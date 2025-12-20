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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EACG1动漫网站爬虫
 * 网址：https://eacg1.com
 */
public class EACG1 extends Spider {

    private static final String siteUrl = "https://eacg1.com";
    private static final String cateUrl = siteUrl + "/vodclassification/";
    private static final String detailUrl = siteUrl + "/voddetails-";
    private static final String searchUrl = siteUrl + "/vodsearch/";

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl);
        return headers;
    }

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();
        try {
            String html = OkHttp.string(siteUrl, getHeaders());
            Document doc = Jsoup.parse(html);

            // 获取分类
            Elements navItems = doc.select(".fed-pops-list li a");
            for (Element item : navItems) {
                String typeId = item.attr("href");
                if (typeId.contains("/vodclassification/")) {
                    String tid = typeId.replace("/vodclassification/", "").replace(".html", "");
                    String typeName = item.text().trim();
                    if (!typeName.isEmpty() && !tid.isEmpty()) {
                        classes.add(new Class(tid, typeName));
                    }
                }
            }

            // 获取推荐内容
            Elements videoItems = doc.select(".fed-list-info .fed-list-item");
            for (Element item : videoItems) {
                Element a = item.select("a.fed-list-pics").first();
                if (a != null) {
                    String href = a.attr("href");
                    String id = href.replace("/voddetails-", "").replace(".html", "");
                    String name = a.attr("title");
                    if (name.isEmpty()) {
                        Element titleElement = item.select(".fed-list-title").first();
                        if (titleElement != null) {
                            name = titleElement.text().trim();
                        }
                    }
                    String pic = a.attr("data-original");
                    String remark = "";
                    Element remarkElement = item.select(".fed-list-remarks").first();
                    if (remarkElement != null) {
                        remark = remarkElement.text().trim();
                    }
                    if (!id.isEmpty() && !name.isEmpty()) {
                        list.add(new Vod(id, name, pic, remark));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();
        try {
            String url = cateUrl + tid + "-" + pg + ".html";
            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);

            Elements videoItems = doc.select(".fed-list-info .fed-list-item");
            for (Element item : videoItems) {
                Element a = item.select("a.fed-list-pics").first();
                if (a != null) {
                    String href = a.attr("href");
                    String id = href.replace("/voddetails-", "").replace(".html", "");
                    String name = a.attr("title");
                    if (name.isEmpty()) {
                        Element titleElement = item.select(".fed-list-title").first();
                        if (titleElement != null) {
                            name = titleElement.text().trim();
                        }
                    }
                    String pic = a.attr("data-original");
                    String remark = "";
                    Element remarkElement = item.select(".fed-list-remarks").first();
                    if (remarkElement != null) {
                        remark = remarkElement.text().trim();
                    }
                    if (!id.isEmpty() && !name.isEmpty()) {
                        list.add(new Vod(id, name, pic, remark));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) {
        List<Vod> list = new ArrayList<>();
        try {
            String id = ids.get(0);
            String url = detailUrl + id + ".html";
            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);

            Vod vod = new Vod();
            vod.setVodId(id);
            
            // 获取标题
            Element titleElement = doc.select("h1.fed-part-eone").first();
            if (titleElement != null) {
                vod.setVodName(titleElement.text().trim());
            }
            
            // 获取图片
            Element picElement = doc.select(".fed-deta-images a").first();
            if (picElement != null) {
                vod.setVodPic(picElement.attr("data-original"));
            }
            
            // 获取其他信息
            Elements infoElements = doc.select(".fed-deta-content ul li");
            for (Element element : infoElements) {
                String text = element.text().trim();
                if (text.startsWith("类型：")) {
                    vod.setTypeName(text.replace("类型：", ""));
                } else if (text.startsWith("年份：")) {
                    vod.setVodYear(text.replace("年份：", ""));
                } else if (text.startsWith("地区：")) {
                    vod.setVodArea(text.replace("地区：", ""));
                } else if (text.startsWith("主演：")) {
                    vod.setVodActor(text.replace("主演：", ""));
                } else if (text.startsWith("导演：")) {
                    vod.setVodDirector(text.replace("导演：", ""));
                } else if (text.startsWith("简介：")) {
                    vod.setVodContent(text.replace("简介：", ""));
                }
            }
            
            // 获取播放源和播放列表
            Map<String, String> playMap = new HashMap<>();
            Elements playSources = doc.select(".fed-tabs-item");
            for (Element source : playSources) {
                Element sourceNameElement = source.select(".fed-tabs-btns li").first();
                String sourceName = sourceNameElement != null ? sourceNameElement.text().trim() : "默认";
                
                List<String> playUrls = new ArrayList<>();
                Elements playLinks = source.select(".fed-play-item a");
                for (Element link : playLinks) {
                    String episodeName = link.text().trim();
                    String playUrl = link.attr("href");
                    if (playUrl.contains("/vodplay-")) {
                        // 提取播放ID
                        String playId = playUrl.replace("/vodplay-", "").replace(".html", "");
                        playUrls.add(episodeName + "$" + playId);
                    }
                }
                
                if (!playUrls.isEmpty()) {
                    playMap.put(sourceName, String.join("#", playUrls));
                }
            }
            
            if (!playMap.isEmpty()) {
                vod.setVodPlayFrom(String.join("$$$", playMap.keySet()));
                vod.setVodPlayUrl(String.join("$$$", playMap.values()));
            }
            
            list.add(vod);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String url = siteUrl + "/vodplay-" + id + ".html";
            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);
            
            // 查找播放链接
            Element playerElement = doc.select("#fed-play-iframe").first();
            if (playerElement != null) {
                String playUrl = playerElement.attr("src");
                if (!playUrl.isEmpty()) {
                    if (playUrl.startsWith("/")) {
                        playUrl = siteUrl + playUrl;
                    }
                    return Result.get().url(playUrl).parse().string();
                }
            }
            
            // 如果没找到iframe，尝试查找script标签中的播放链接
            Elements scripts = doc.select("script");
            for (Element script : scripts) {
                String scriptText = script.html();
                if (scriptText.contains("player_aaaa")) {
                    // 提取JSON数据
                    Pattern pattern = Pattern.compile("player_aaaa=(.*?)\}");
                    Matcher matcher = pattern.matcher(scriptText);
                    if (matcher.find()) {
                        String jsonStr = matcher.group(1) + "}";
                        // 解析JSON获取播放链接
                        // 这里可以根据实际的JSON结构调整解析逻辑
                        if (jsonStr.contains("url")) {
                            // 简单提取URL
                            Pattern urlPattern = Pattern.compile("\"url\":\"(.*?)\"");
                            Matcher urlMatcher = urlPattern.matcher(jsonStr);
                            if (urlMatcher.find()) {
                                String playUrl = urlMatcher.group(1).replace("\\", "");
                                return Result.get().url(playUrl).parse().string();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.get().url(id).parse().string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        List<Vod> list = new ArrayList<>();
        try {
            String url = searchUrl + URLEncoder.encode(key, "UTF-8") + ".html";
            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);

            Elements videoItems = doc.select(".fed-list-info .fed-list-item");
            for (Element item : videoItems) {
                Element a = item.select("a.fed-list-pics").first();
                if (a != null) {
                    String href = a.attr("href");
                    String id = href.replace("/voddetails-", "").replace(".html", "");
                    String name = a.attr("title");
                    if (name.isEmpty()) {
                        Element titleElement = item.select(".fed-list-title").first();
                        if (titleElement != null) {
                            name = titleElement.text().trim();
                        }
                    }
                    String pic = a.attr("data-original");
                    String remark = "";
                    Element remarkElement = item.select(".fed-list-remarks").first();
                    if (remarkElement != null) {
                        remark = remarkElement.text().trim();
                    }
                    if (!id.isEmpty() && !name.isEmpty() && name.contains(key)) {
                        list.add(new Vod(id, name, pic, remark));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }
}