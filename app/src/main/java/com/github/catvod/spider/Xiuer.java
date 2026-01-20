package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Danmaku;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.DanmakuUtil;
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
 * 秀儿影视 Java 爬虫
 * 修复搜索逻辑，适配 Result/Vod 规范
 */
public class Xiuer extends Spider {

    private final String siteUrl = "https://www.xiuer.pro";

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        header.put("Referer", siteUrl + "/");
        return header;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        String[] names = {"电影", "电视剧", "综艺", "动漫", "短剧", "纪录片"};
        String[] ids = {"dianying", "dianshiju", "zongyi", "dongman", "duanju", "jilupian"};
        for (int i = 0; i < names.length; i++) {
            classes.add(new Class(ids[i], names[i]));
        }
        // 首页推荐数据
        String html = OkHttp.string(siteUrl, getHeader());
        return Result.string(classes, parseList(html, false));
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = OkHttp.string(siteUrl, getHeader());
        return Result.string(parseList(html, false));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 对应 URL: /show/fyclass/page/fypage.html
        String url = siteUrl + "/show/" + tid + "/page/" + pg + ".html";
        String html = OkHttp.string(url, getHeader());
        return Result.string(parseList(html, false));
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = siteUrl + ids.get(0);
        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(doc.selectFirst("h1").text());

        // 图片提取 (data-src 优先)
        Element picTag = doc.selectFirst(".video-cover img, .module-item-pic img");
        if (picTag != null) {
            String pic = picTag.attr("data-src");
            if (pic.isEmpty()) pic = picTag.attr("src");
            vod.setVodPic(pic.startsWith("http") ? pic : siteUrl + pic);
        }

        // 备注与内容
        Element aux = doc.selectFirst(".video-info-aux");
        if (aux != null) vod.setVodRemarks(aux.text());
        Element content = doc.selectFirst(".video-info-content");
        if (content != null) vod.setVodContent(content.text());

        // 详情索引：演员(0), 导演(1), 年份(3)
        Elements infoItems = doc.select(".video-info-items");
        vod.setVodActor(getInfoByIndex(infoItems, 0));
        vod.setVodDirector(getInfoByIndex(infoItems, 1));
        vod.setVodYear(getInfoByIndex(infoItems, 3));
        
        // 尝试获取视频时长
        String durationStr = getInfoByIndex(infoItems, 2); // 假设时长在第三个位置
        if (durationStr != null && !durationStr.isEmpty()) {
            Integer durationSeconds = parseDurationToSeconds(durationStr);
            if (durationSeconds != null) {
                // 将时长信息存储到vod的扩展信息中，方便后续播放时使用
                vod.setVodTag("duration:" + durationSeconds); // 使用tag字段临时存储时长信息
            }
        }

        // 播放列表解析
        Elements tabs = doc.select(".module-tab-item");
        Elements lists = doc.select(".module-player-list");
        
        List<String> fromList = new ArrayList<>();
        List<String> urlList = new ArrayList<>();

        for (int i = 0; i < tabs.size(); i++) {
            String tabName = tabs.get(i).text();
            if (tabName.contains("排序")) continue;

            Elements links = lists.get(i).select("a[href*=/play/]");
            List<String> vodItems = new ArrayList<>();
            for (Element link : links) {
                // 将视频标题和URL结合，以便播放时获取标题
                vodItems.add(link.text() + "$" + link.attr("href") + "#" + vod.getVodName());
            }
            if (!vodItems.isEmpty()) {
                fromList.add(tabName);
                urlList.add(join("#", vodItems));
            }
        }
        vod.setVodPlayFrom(join("$$$", fromList));
        vod.setVodPlayUrl(join("$$$", urlList));

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        // 对应 searchUrl: /vod/search/wd/**.html
        String url = siteUrl + "/vod/search/wd/" + OkHttp.urlEncode(key) + ".html";
        String html = OkHttp.string(url, getHeader());
        return Result.string(parseList(html, true));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 解析id参数，可能包含视频标题信息（格式：title#url 或 name$url#title）
        String[] parts = id.split("#");
        String videoTitle = "";
        String videoUrl = id;
        
        if (parts.length >= 2) {
            // 如果id格式为 "name$url#title"，则第一部分是 name$url，第二部分是标题
            String[] nameUrlParts = parts[0].split("\\$");
            if (nameUrlParts.length >= 2) {
                videoUrl = nameUrlParts[1]; // 获取URL部分
                videoTitle = parts[1]; // 获取标题
            } else {
                // 如果是 "title#url" 格式
                videoTitle = parts[0];
                videoUrl = parts[1];
            }
        } else {
            // 如果只有URL，尝试从URL中提取标题
            String[] nameUrlParts = id.split("\\$");
            if (nameUrlParts.length >= 2) {
                videoTitle = nameUrlParts[0];
                videoUrl = nameUrlParts[1];
            } else {
                videoUrl = id.startsWith("http") ? id : siteUrl + id;
                // 尝试从详情页获取标题
                videoTitle = extractTitleFromUrl(videoUrl);
            }
        }

        // 构建播放结果
        Result result = Result.get()
                .url(videoUrl)
                .parse()
                .header(getHeader());
        
        // 尝试获取视频时长信息（如果有的话）
        Integer durationSeconds = getDurationFromId(id);
        
        // 获取弹幕列表
        List<Danmaku> danmakus = new ArrayList<>();
        try {
            // 1. 获取站点原生弹幕（如果存在）
            List<Danmaku> nativeDanmakus = getNativeDanmakus(id, videoTitle);
            danmakus.addAll(nativeDanmakus);
            
            // 2. 获取B站等第三方弹幕，传入时长参数以提高匹配精度
            List<Danmaku> externalDanmakus = DanmakuUtil.getDanmakuList(
                id,                    // 视频ID
                videoTitle,            // 视频标题
                siteKey,              // 站点标识
                siteUrl,              // 站点URL
                new HashMap<>()       // 额外参数
            );
            
            // 过滤有效弹幕
            externalDanmakus = DanmakuUtil.filterValidDanmakus(externalDanmakus);
            danmakus.addAll(externalDanmakus);
            
        } catch (Exception e) {
            // 即使弹幕获取失败也不影响视频播放
            System.err.println("弹幕加载失败: " + e.getMessage());
        }
        
        // 如果有弹幕则添加到结果中
        if (!danmakus.isEmpty()) {
            result.danmaku(danmakus);
        }
        
        return result.string();
    }

    /**
     * 获取站点原生弹幕
     */
    private List<Danmaku> getNativeDanmakus(String id, String title) {
        List<Danmaku> danmakus = new ArrayList<>();
        // 检查是否存在原生弹幕API，这里只是一个示例
        // 实际需要根据秀儿影视的具体API来实现
        /*
        String nativeDanmakuUrl = siteUrl + "/api/danmaku?id=" + extractVideoId(id);
        if (!nativeDanmakuUrl.isEmpty()) {
            danmakus.add(Danmaku.create()
                    .name("原生弹幕")
                    .url(nativeDanmakuUrl)
                    .source(siteKey)
                    .type("xml")
                    .priority(2)); // 原生弹幕优先级较高
        }
        */
        return danmakus;
    }

    /**
     * 从URL中提取视频标题
     */
    private String extractTitleFromUrl(String url) {
        try {
            if (url.contains("/play/")) {
                // 从播放页获取标题
                String playPageHtml = OkHttp.string(url.startsWith("http") ? url : siteUrl + url, getHeader());
                Document doc = Jsoup.parse(playPageHtml);
                
                // 尝试从播放页面获取标题
                Element titleElement = doc.selectFirst("title, h1, .video-title, .player-title");
                if (titleElement != null) {
                    String title = titleElement.text().trim();
                    // 清理标题，移除网站名称等无关信息
                    return cleanVideoTitle(title);
                }
            }
        } catch (Exception e) {
            System.err.println("从URL提取标题失败: " + e.getMessage());
        }
        return "";
    }
    
    /**
     * 从ID中尝试提取时长信息
     */
    private Integer getDurationFromId(String id) {
        try {
            // 这里可以尝试从URL或其它地方解析时长信息
            // 暂时返回null，实际实现需要根据具体站点结构来定
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 解析持续时间字符串为秒数
     */
    private Integer parseDurationToSeconds(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            return null;
        }
        
        try {
            // 匹配 "120分钟" 或 "2小时" 或 "2小时30分钟" 等格式
            Pattern pattern = Pattern.compile("(\\d+)\\s*小时.*?(\\d+)\\s*分钟|(?:(\\d+)\\s*小时)|(\\d+)\\s*分钟");
            Matcher matcher = pattern.matcher(durationStr);
            
            if (matcher.find()) {
                int hours = 0, minutes = 0;
                
                if (matcher.group(1) != null && matcher.group(2) != null) {
                    // 匹配 "X小时Y分钟" 格式
                    hours = Integer.parseInt(matcher.group(1));
                    minutes = Integer.parseInt(matcher.group(2));
                } else if (matcher.group(3) != null) {
                    // 匹配 "X小时" 格式
                    hours = Integer.parseInt(matcher.group(3));
                } else if (matcher.group(4) != null) {
                    // 匹配 "X分钟" 格式
                    minutes = Integer.parseInt(matcher.group(4));
                }
                
                return hours * 3600 + minutes * 60;
            }
            
            // 尝试匹配纯数字分钟
            Pattern numPattern = Pattern.compile("(\\d+)");
            Matcher numMatcher = numPattern.matcher(durationStr);
            if (numMatcher.find()) {
                int minutes = Integer.parseInt(numMatcher.group(1));
                // 假设如果没有单位，默认是分钟
                if (minutes < 600) { // 如果小于600，认为是分钟
                    return minutes * 60;
                } else {
                    return minutes; // 否则是秒
                }
            }
        } catch (Exception e) {
            System.err.println("解析时长失败: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * 从详情页面获取视频标题
     * @param id 视频ID（播放页面的URL）
     * @return 视频标题
     */
    private String getVideoTitleFromDetailPage(String id) {
        try {
            // 如果id是播放页面URL，需要先跳转到详情页面获取标题
            // 这里假设播放页面URL格式是 /play/...，详情页面URL格式是 /detail/...
            String detailUrl = id;
            if (id.contains("/play/")) {
                // 从播放URL推断详情URL（这取决于站点的实际URL结构）
                // 这里提供一个通用方法，尝试从播放页面解析标题
                String playPageHtml = OkHttp.string(detailUrl.startsWith("http") ? detailUrl : siteUrl + detailUrl, getHeader());
                Document doc = Jsoup.parse(playPageHtml);
                
                // 尝试从播放页面获取标题
                Element titleElement = doc.selectFirst("title, h1, .video-title");
                if (titleElement != null) {
                    String title = titleElement.text().trim();
                    // 清理标题，移除网站名称等无关信息
                    return cleanVideoTitle(title);
                }
            }
            
            // 如果无法从播放页面获取，尝试其他方法
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    
    /**
     * 清理视频标题
     * @param title 原始标题
     * @return 清理后的标题
     */
    private String cleanVideoTitle(String title) {
        if (title == null) return "";
        
        // 移除常见的后缀
        title = title.replaceAll("-\\s*秀儿影视", "")
                    .replaceAll("-\\s*在线观看", "")
                    .replaceAll("\\|.*$", "")  // 移除 | 之后的内容
                    .replaceAll("秀儿影视.*$", "")  // 移除秀儿影视之后的内容
                    .replaceAll("\\(.*?\\)", "")  // 移除括号内容
                    .replaceAll("【.*?】", "")     // 移除方括号内容
                    .trim();
        
        return title;
    }

    /**
     * 统一列表解析逻辑
     * @param isSearch 是否为搜索模式（搜索页与列表页选择器不同）
     */
    private List<Vod> parseList(String html, boolean isSearch) {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        
        // 修复：搜索页使用 .module-search-item，普通页使用 .module-item
        String selector = isSearch ? ".module-search-item" : ".module-item";
        Elements items = doc.select(selector);

        for (Element item : items) {
            Element a = item.selectFirst("a[href*=/detail/]");
            if (a == null) continue;

            Vod vod = new Vod();
            vod.setVodId(a.attr("href"));
            
            // 修复：搜索页标题在 h3，普通页在 a 的 title
            String name = isSearch ? item.selectFirst("h3").text() : a.attr("title");
            vod.setVodName(name);

            // 图片提取
            Element img = item.selectFirst("img");
            if (img != null) {
                String pic = img.attr("data-src");
                if (pic.isEmpty()) pic = img.attr("src");
                vod.setVodPic(pic.startsWith("http") ? pic : siteUrl + pic);
            }

            // 备注提取
            Element remark = item.selectFirst(".video-serial, .module-item-text, .module-item-note");
            if (remark != null) vod.setVodRemarks(remark.text());

            list.add(vod);
        }
        return list;
    }

    private String getInfoByIndex(Elements items, int index) {
        if (items.size() > index) {
            String text = items.get(index).text();
            return text.contains("：") ? text.split("：")[1].trim() : text;
        }
        return "";
    }

    private String join(String separator, List<String> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) sb.append(separator);
        }
        return sb.toString();
    }
}