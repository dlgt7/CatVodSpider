package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Danmaku;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
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
        Long durationSeconds = null;
        if (durationStr != null && !durationStr.isEmpty()) {
            durationSeconds = parseDurationToSeconds(durationStr);
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
                // 改进ID拼接格式：将剧集标题、URL和总标题及可能的时长信息都包含进去
                String episodeTitle = link.text(); // 如 "第1集" 或 "HD中字"
                String episodeUrl = link.attr("href");
                String seriesTitle = vod.getVodName(); // 如 "凡人修仙传"
                
                // 组合格式: "集数标题$播放URL#系列标题#时长秒数"
                String combinedId = episodeTitle + "$" + episodeUrl + "#" + seriesTitle;
                if (durationSeconds != null) {
                    combinedId += "#" + durationSeconds;
                }
                
                vodItems.add(combinedId);
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
        // 改进ID解析逻辑，支持多段格式：episodeTitle$url#seriesTitle#durationSeconds
        String[] parts = id.split("#");
        String episodeTitle = "";
        String url = id; // 默认使用整个id作为url
        String seriesTitle = "";
        long durationSeconds = 0;
        
        if (parts.length >= 1) {
            // 解析 "集数标题$播放URL" 部分
            String[] nameUrlParts = parts[0].split("\\$");
            if (nameUrlParts.length >= 2) {
                episodeTitle = nameUrlParts[0]; // 如 "第1集"
                url = nameUrlParts[1]; // 播放URL
            } else {
                url = nameUrlParts[0];
            }
        }
        
        if (parts.length >= 2) {
            seriesTitle = parts[1]; // 系列标题，如 "凡人修仙传"
        }
        
        if (parts.length >= 3) {
            try {
                durationSeconds = Long.parseLong(parts[2]); // 时长（秒）
            } catch (NumberFormatException e) {
                durationSeconds = 0;
            }
        }
        
        // 构建搜索标题：将系列标题和集数标题结合起来，提高匹配精度
        String searchTitle = seriesTitle;
        if (!episodeTitle.isEmpty() && !episodeTitle.equals(seriesTitle)) {
            searchTitle = seriesTitle + " " + episodeTitle; // 如 "凡人修仙传 第1集"
        }

        // 使用新版 DanmakuUtil 接口
        List<Danmaku> dms = new ArrayList<>();
        try {
            // 1. 尝试添加原生弹幕源（如果存在的话）
            // 注意：这里只是示例，实际环境中需要确认秀儿是否有弹幕接口
            /*
            dms.add(Danmaku.create()
                    .name("秀儿原生")
                    .url(siteUrl + "/api/dm?id=" + url)
                    .source(siteKey)
                    .priority(20));
            */
            
            // 2. 自动匹配 B 站弹幕 (使用重构后的新方法)
            // 传入改进后的标题和实际时长（秒转换为毫秒），dms 列表会被自动填充
            DanmakuUtil.appendBili(searchTitle, durationSeconds * 1000, dms); // 转换为毫秒
            
        } catch (Exception e) {
            SpiderDebug.log(e);
        }

        // 3. 合并、去重并排序 (使用重构后的新方法)
        List<Danmaku> finalDms = DanmakuUtil.merge(dms);

        return Result.get()
                .url(url.startsWith("http") ? url : siteUrl + url)
                .parse()
                .danmaku(finalDms)
                .header(getHeader())
                .string();
    }

    /**
     * 解析持续时间字符串为秒数
     */
    private Long parseDurationToSeconds(String durationStr) {
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
                
                return (long) (hours * 3600 + minutes * 60);
            }
            
            // 尝试匹配纯数字分钟
            Pattern numPattern = Pattern.compile("(\\d+)");
            Matcher numMatcher = numPattern.matcher(durationStr);
            if (numMatcher.find()) {
                int minutes = Integer.parseInt(numMatcher.group(1));
                // 假设如果没有单位，默认是分钟
                if (minutes < 600) { // 如果小于600，认为是分钟
                    return (long) (minutes * 60);
                } else {
                    return (long) minutes; // 否则是秒
                }
            }
        } catch (Exception e) {
            System.err.println("解析时长失败: " + e.getMessage());
        }
        
        return null;
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