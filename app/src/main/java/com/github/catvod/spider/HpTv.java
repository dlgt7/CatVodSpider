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
 * 韩片网(HpTv)爬虫
 * 站点地址: https://www.hanpian.tv
 */
public class HpTv extends Spider {

    private static String siteUrl = "https://www.hanpian.tv";
    private static String apiUrl = "https://www.hanpian.tv";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl);
        return headers;
    }

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
        if (extend != null && !extend.isEmpty()) {
            siteUrl = extend;
            apiUrl = extend;
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            List<Vod> list = new ArrayList<>();
            List<Class> classes = new ArrayList<>();
            
            // 添加分类
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
            
            // 获取首页推荐内容
            String url = siteUrl + "/";
            String html = OkHttp.string(url, getHeaders());
            if (html == null || html.isEmpty()) {
                return Result.error("无法获取首页内容");
            }
            
            Document doc = Jsoup.parse(html);
            
            // 解析首页视频列表
            Elements videoElements = doc.select(".mo-cols-lays .mo-cols-rows li");
            for (Element element : videoElements) {
                try {
                    Element aElement = element.select("a.mo-situ-pics").first();
                    Element imgElement = element.select("img").first();
                    Element nameElement = element.select("a.mo-situ-name").first();
                    Element remarkElement = element.select("span.mo-situ-rema").first();
                    
                    if (aElement == null || nameElement == null) continue;
                    
                    String pic = "";
                    if (imgElement != null) {
                        pic = imgElement.attr("src");
                        if (pic.isEmpty()) {
                            pic = imgElement.attr("data-original");
                        }
                    }
                    
                    String urlStr = aElement.attr("href");
                    String name = nameElement.text();
                    String remark = "";
                    if (remarkElement != null) {
                        remark = remarkElement.text();
                    }
                    
                    if (pic.startsWith("//")) {
                        pic = "https:" + pic;
                    } else if (pic.startsWith("/")) {
                        pic = siteUrl + pic;
                    }
                    
                    String id = "";
                    Pattern pattern = Pattern.compile("/vod/(\\d+)/");
                    Matcher matcher = pattern.matcher(urlStr);
                    if (matcher.find()) {
                        id = matcher.group(1);
                    }
                    
                    if (!id.isEmpty() && !name.isEmpty()) {
                        list.add(new Vod(id, name, pic, remark));
                    }
                } catch (Exception e) {
                    // 忽略单个视频解析错误
                }
            }
            
            return Result.string(classes, list);
        } catch (Exception e) {
            return Result.error("获取首页内容失败: " + e.getMessage());
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            List<Vod> list = new ArrayList<>();
            String url = "";
            
            // 构造分类URL
            if ("movie".equals(tid) || "tv".equals(tid) || "variety".equals(tid) || "anime".equals(tid)) {
                url = siteUrl + "/menu/" + tid + "/" + pg + "/";
            } else {
                // 其他分类如动作片、喜剧片等
                url = siteUrl + "/menu/" + tid + "/" + pg + "/";
            }
            
            String html = OkHttp.string(url, getHeaders());
            if (html == null || html.isEmpty()) {
                return Result.error("无法获取分类内容");
            }
            
            Document doc = Jsoup.parse(html);
            
            // 解析视频列表
            Elements videoElements = doc.select(".mo-cols-lays .mo-cols-rows li");
            for (Element element : videoElements) {
                try {
                    Element aElement = element.select("a.mo-situ-pics").first();
                    Element imgElement = element.select("img").first();
                    Element nameElement = element.select("a.mo-situ-name").first();
                    Element remarkElement = element.select("span.mo-situ-rema").first();
                    
                    if (aElement == null || nameElement == null) continue;
                    
                    String pic = "";
                    if (imgElement != null) {
                        pic = imgElement.attr("src");
                        if (pic.isEmpty()) {
                            pic = imgElement.attr("data-original");
                        }
                    }
                    
                    String urlStr = aElement.attr("href");
                    String name = nameElement.text();
                    String remark = "";
                    if (remarkElement != null) {
                        remark = remarkElement.text();
                    }
                    
                    if (pic.startsWith("//")) {
                        pic = "https:" + pic;
                    } else if (pic.startsWith("/")) {
                        pic = siteUrl + pic;
                    }
                    
                    String id = "";
                    Pattern pattern = Pattern.compile("/vod/(\\d+)/");
                    Matcher matcher = pattern.matcher(urlStr);
                    if (matcher.find()) {
                        id = matcher.group(1);
                    }
                    
                    if (!id.isEmpty() && !name.isEmpty()) {
                        list.add(new Vod(id, name, pic, remark));
                    }
                } catch (Exception e) {
                    // 忽略单个视频解析错误
                }
            }
            
            return Result.string(list);
        } catch (Exception e) {
            return Result.error("获取分类内容失败: " + e.getMessage());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("无效的视频ID");
            }
            
            String id = ids.get(0);
            String url = siteUrl + "/vod/" + id + "/";
            String html = OkHttp.string(url, getHeaders());
            if (html == null || html.isEmpty()) {
                return Result.error("无法获取视频详情");
            }
            
            Document doc = Jsoup.parse(html);
            
            Vod vod = new Vod();
            vod.setVodId(id);
            
            // 获取视频标题
            Element titleElement = doc.select("h1 a").first();
            if (titleElement != null) {
                vod.setVodName(titleElement.text().trim());
            }
            
            // 获取视频图片
            Element picElement = doc.select("a.mo-situ-pics img").first();
            if (picElement != null) {
                String pic = picElement.attr("src");
                if (pic.startsWith("//")) {
                    pic = "https:" + pic;
                } else if (pic.startsWith("/")) {
                    pic = siteUrl + pic;
                }
                vod.setVodPic(pic);
            }
            
            // 获取年份
            Elements infoElements = doc.select(".mo-deta-info ul li");
            for (Element info : infoElements) {
                String text = info.text();
                if (text.contains("年份:")) {
                    Pattern pattern = Pattern.compile("(\\d{4})");
                    Matcher matcher = pattern.matcher(text);
                    if (matcher.find()) {
                        vod.setVodYear(matcher.group(1));
                    }
                }
            }
            
            // 获取简介
            Element descElement = doc.select(".mo-word-info").first();
            if (descElement != null) {
                vod.setVodContent(descElement.text().trim());
            }
            
            // 获取演员
            for (Element info : infoElements) {
                String text = info.text();
                if (text.contains("主演:")) {
                    vod.setVodActor(text.replace("主演:", "").trim());
                }
            }
            
            // 获取导演
            for (Element info : infoElements) {
                String text = info.text();
                if (text.contains("导演:")) {
                    vod.setVodDirector(text.replace("导演:", "").trim());
                }
            }
            
            // 获取分类
            for (Element info : infoElements) {
                String text = info.text();
                if (text.contains("分类:")) {
                    vod.setVodTag(text.replace("分类:", "").trim());
                }
            }
            
            // 获取播放链接
            Elements playSources = doc.select(".mo-sort-head h2 a.mo-movs-btns");
            Elements playLists = doc.select(".mo-movs-item");
            
            StringBuilder vod_play_from = new StringBuilder();
            StringBuilder vod_play_url = new StringBuilder();
            
            for (int i = 0; i < playSources.size() && i < playLists.size(); i++) {
                String sourceName = playSources.get(i).text().trim();
                vod_play_from.append(sourceName).append("$$$");
                
                Elements epsElements = playLists.get(i).select("a");
                for (int j = 0; j < epsElements.size(); j++) {
                    Element epElement = epsElements.get(j);
                    String epName = epElement.text().trim();
                    String epUrl = epElement.attr("href");
                    
                    // 处理相对路径
                    if (epUrl.startsWith("/")) {
                        epUrl = epUrl.substring(1);
                    }
                    
                    vod_play_url.append(epName).append("$").append(epUrl);
                    vod_play_url.append(j < epsElements.size() - 1 ? "#" : "$$$");
                }
            }
            
            // 移除末尾的分隔符
            if (vod_play_from.length() > 3) {
                vod_play_from.delete(vod_play_from.length() - 3, vod_play_from.length());
            }
            if (vod_play_url.length() > 3) {
                vod_play_url.delete(vod_play_url.length() - 3, vod_play_url.length());
            }
            
            vod.setVodPlayFrom(vod_play_from.toString());
            vod.setVodPlayUrl(vod_play_url.toString());
            
            return Result.string(vod);
        } catch (Exception e) {
            return Result.error("获取视频详情失败: " + e.getMessage());
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            List<Vod> list = new ArrayList<>();
            String url = siteUrl + "/search/" + URLEncoder.encode(key, "UTF-8") + "-------------/";
            String html = OkHttp.string(url, getHeaders());
            if (html == null || html.isEmpty()) {
                return Result.error("搜索失败");
            }
            
            Document doc = Jsoup.parse(html);
            
            // 解析搜索结果
            Elements videoElements = doc.select(".mo-cols-lays .mo-cols-rows li");
            for (Element element : videoElements) {
                try {
                    Element aElement = element.select("a.mo-situ-pics").first();
                    Element imgElement = element.select("img").first();
                    Element nameElement = element.select("a.mo-situ-name").first();
                    Element remarkElement = element.select("span.mo-situ-rema").first();
                    
                    if (aElement == null || nameElement == null) continue;
                    
                    String pic = "";
                    if (imgElement != null) {
                        pic = imgElement.attr("src");
                        if (pic.isEmpty()) {
                            pic = imgElement.attr("data-original");
                        }
                    }
                    
                    String urlStr = aElement.attr("href");
                    String name = nameElement.text();
                    String remark = "";
                    if (remarkElement != null) {
                        remark = remarkElement.text();
                    }
                    
                    if (pic.startsWith("//")) {
                        pic = "https:" + pic;
                    } else if (pic.startsWith("/")) {
                        pic = siteUrl + pic;
                    }
                    
                    String id = "";
                    Pattern pattern = Pattern.compile("/vod/(\\d+)/");
                    Matcher matcher = pattern.matcher(urlStr);
                    if (matcher.find()) {
                        id = matcher.group(1);
                    }
                    
                    if (!id.isEmpty() && !name.isEmpty()) {
                        list.add(new Vod(id, name, pic, remark));
                    }
                } catch (Exception e) {
                    // 忽略单个视频解析错误
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
            if (id == null || id.isEmpty()) {
                return Result.error("播放链接为空");
            }
            
            // 如果id是完整URL，直接返回
            if (id.startsWith("http")) {
                return Result.get().url(id).header(getHeaders()).string();
            }
            
            // 处理相对路径
            String playUrl = id;
            if (id.startsWith("/")) {
                playUrl = siteUrl + id;
            } else {
                playUrl = siteUrl + "/" + id;
            }
            
            return Result.get().url(playUrl).header(getHeaders()).string();
        } catch (Exception e) {
            return Result.error("获取播放链接失败: " + e.getMessage());
        }
    }
}
