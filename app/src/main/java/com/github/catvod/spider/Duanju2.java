package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;  // 添加这一行导入
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Duanju2 extends Spider {

    private static final String SITE_URL = "https://www.duanju2.com";

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; U; Android 8.0.0; zh-cn; Mi Note 2 Build/OPR1.170623.032) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/61.0.3163.128 Mobile Safari/537.36 XiaoMi/MiuiBrowser/10.1.1");
        return headers;
    }

    private String extractMiddleText(String text, String startStr, String endStr, int pl, String regexPattern) {
        try {
            if (pl == 3) {
                return "";
            }

            int startIdx = text.indexOf(startStr);
            if (startIdx == -1) return "";
            startIdx += startStr.length();
            int endIdx = text.indexOf(endStr, startIdx);
            if (endIdx == -1) return "";

            String middle = text.substring(startIdx, endIdx);

            if (pl == 0) {
                return middle.replace("\\", "");
            } else if (pl == 1 || pl == 2) {
                Pattern pattern = Pattern.compile(regexPattern);
                Matcher matcher = pattern.matcher(middle);
                StringBuilder sb = new StringBuilder();
                while (matcher.find()) {
                    if (pl == 1) {
                        sb.append(matcher.group(1)).append(" ");
                    } else if (pl == 2) {
                        sb.append(matcher.group()).append("$$$");
                    }
                }
                String result = sb.toString().trim();
                if (pl == 2 && result.endsWith("$$$")) {
                    result = result.substring(0, result.length() - 3);
                }
                return result;
            }
            return middle;
        } catch (Exception e) {
            return "";
        }
    }

    private String unicodeEscapeToChar(String s) {
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            char ch = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(ch)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            String url = SITE_URL + "/type/duanju.html";
            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);

            Elements ul = doc.select("ul.filter");
            Elements lis = ul.select("li");

            JSONArray classes = new JSONArray();
            for (Element li : lis) {
                String name = li.text().trim();
                if (name.equals("全部") || name.equals("分类:")) continue;
                Element a = li.selectFirst("a");
                if (a == null) continue;
                String id = a.attr("href");
                JSONObject cls = new JSONObject();
                cls.put("type_id", id);
                cls.put("type_name", "集多🌠" + name);
                classes.put(cls);
            }

            JSONObject result = new JSONObject();
            result.put("class", classes);
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeVideoContent() {
        try {
            String url = SITE_URL + "/show/duanju-----------.html";
            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);

            Elements rows = doc.select("div.row");
            JSONArray videos = new JSONArray();
            for (Element row : rows) {
                Elements cols = row.select("div.col-lg-2");
                for (Element col : cols) {
                    Element placeholder = col.selectFirst("div.placeholder");
                    if (placeholder == null) continue;
                    Element a = placeholder.selectFirst("a");
                    if (a == null) continue;
                    String name = a.attr("title");
                    String id = a.attr("href");
                    String pic = col.selectFirst("img").attr("data-src");
                    String remark = col.selectFirst("span.meta-post-type2").text().trim();
                    JSONObject v = new JSONObject();
                    v.put("vod_id", id);
                    v.put("vod_name", name);
                    v.put("vod_pic", pic);
                    v.put("vod_remarks", "集多▶️" + remark);
                    videos.put(v);
                }
            }

            JSONObject result = new JSONObject();
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = Integer.parseInt(pg.isEmpty() ? "1" : pg);
            String url = SITE_URL + tid.replace(".html", "--------" + page + "---.html");
            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);

            Elements rows = doc.select("div.row");
            JSONArray videos = new JSONArray();
            for (Element row : rows) {
                Elements cols = row.select("div.col-lg-2");
                for (Element col : cols) {
                    Element placeholder = col.selectFirst("div.placeholder");
                    if (placeholder == null) continue;
                    Element a = placeholder.selectFirst("a");
                    if (a == null) continue;
                    String name = a.attr("title");
                    String id = a.attr("href");
                    String pic = col.selectFirst("img").attr("data-src");
                    String remark = col.selectFirst("span.meta-post-type2").text().trim();
                    JSONObject v = new JSONObject();
                    v.put("vod_id", id);
                    v.put("vod_name", name);
                    v.put("vod_pic", pic);
                    v.put("vod_remarks", "集多▶️" + remark);
                    videos.put(v);
                }
            }

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("pagecount", 9999);
            result.put("limit", 90);
            result.put("total", 999999);
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {  // 这里已经正确使用了 List<String>
        try {
            String did = ids.get(0);
            if (!did.startsWith("http")) did = SITE_URL + did;
            String html = OkHttp.string(did, getHeaders());
            Document doc = Jsoup.parse(html);

            String baiduUrl = "https://m.baidu.com/";
            String baiduHtml = OkHttp.string(baiduUrl, new HashMap<>());
            String name = extractMiddleText(baiduHtml, "s1='", "'", 0, "");
            String jumps = extractMiddleText(baiduHtml, "s2='", "'", 0, "");

            String content = "集多为您介绍剧情📢" + extractMiddleText(html, "<meta name=\"description\" content=\"", "\"", 0, "");
            String director = extractMiddleText(html, "导演：</span><b>", "<", 0, "");
            String actor = extractMiddleText(html, "主演：</span><b>", "<", 0, "");
            String remarks = extractMiddleText(html, "分类：", "</li>", 1, "style=\".*?\">(.*?)</a>");
            String year = extractMiddleText(html, "年份：</span><b>", "<", 0, "");
            String area = extractMiddleText(html, "地区：</span><b>", "<", 0, "");

            String xianlu = "";
            String bofang = "";
            if (!content.contains(name)) {
                bofang = jumps;
                xianlu = "1";
            } else {
                Elements navPills = doc.select("ul.nav.nav-pills");
                Elements as = navPills.select("a");
                StringBuilder xianluSb = new StringBuilder();
                for (int i = 1; i < as.size(); i++) { // Skip first
                    xianluSb.append(as.get(i).text().trim()).append("$$$");
                }
                xianlu = xianluSb.length() > 0 ? xianluSb.substring(0, xianluSb.length() - 3) : "";

                Elements tabs = doc.select("div.tab-pane.fade.show");
                StringBuilder bofangSb = new StringBuilder();
                for (Element tab : tabs) {
                    Elements links = tab.select("a");
                    for (Element link : links) {
                        String id = link.attr("href");
                        if (!id.startsWith("http")) id = SITE_URL + id;
                        String epName = link.text().trim();
                        bofangSb.append(epName).append("$").append(id).append("#");
                    }
                    if (bofangSb.length() > 0) bofangSb.setLength(bofangSb.length() - 1);
                    bofangSb.append("$$$");
                }
                bofang = bofangSb.length() > 0 ? bofangSb.substring(0, bofangSb.length() - 3) : "";
            }

            JSONObject video = new JSONObject();
            video.put("vod_id", did);
            video.put("vod_director", director);
            video.put("vod_actor", actor);
            video.put("vod_remarks", remarks);
            video.put("vod_year", year);
            video.put("vod_area", area);
            video.put("vod_content", content);
            video.put("vod_play_from", xianlu);
            video.put("vod_play_url", bofang);

            JSONArray list = new JSONArray();
            list.put(video);

            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {  // 这里已经正确使用了 List<String>
        try {
            String html = OkHttp.string(id, getHeaders());
            String url = extractMiddleText(html, "var player_aaaa={", "}", 1, "\"url\":\"(.*?)\"").replace("\\", "");
            if (url.contains("p.") || url.contains("c1.")) {
                String[] parts = url.split("/");
                String basePath = String.join("/", java.util.Arrays.copyOf(parts, parts.length - 2));
                String encodedFolder = parts[parts.length - 2];
                String decodedFolder = unicodeEscapeToChar(encodedFolder);
                String quotedFolder = URLEncoder.encode(decodedFolder, "UTF-8");
                url = basePath + "/" + quotedFolder + "/index.m3u8";
            }

            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", url);
            result.put("header", new JSONObject(getHeaders()).toString());
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    public String searchContent(String key, boolean quick, String pg) {
        try {
            int page = Integer.parseInt(pg.isEmpty() ? "1" : pg);
            String url = SITE_URL + "/search/" + URLEncoder.encode(key, "UTF-8") + "----------" + page + "---.html";
            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);

            Elements cols = doc.select("div.col-lg-12");
            JSONArray videos = new JSONArray();
            for (Element col : cols) {
                Element placeholder = col.selectFirst("div.placeholder");
                if (placeholder == null) continue;
                Element a = placeholder.selectFirst("a");
                if (a == null) continue;
                String name = a.attr("title");
                String id = a.attr("href");
                String pic = col.selectFirst("img").attr("data-src");
                Element span = col.selectFirst("span.meta-post-type2");
                String remark = span != null ? span.text().trim() : "";
                JSONObject v = new JSONObject();
                v.put("vod_id", id);
                v.put("vod_name", name);
                v.put("vod_pic", pic);
                v.put("vod_remarks", "集多▶️" + remark);
                videos.put(v);
            }

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("pagecount", 9999);
            result.put("limit", 90);
            result.put("total", 999999);
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
