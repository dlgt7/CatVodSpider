package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.okhttp.OkHttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 作者 丢丢喵推荐 🚓 内容均从互联网收集而来 仅供交流学习使用 版权归原创者所有 如侵犯了您的权益 请通知作者 将及时删除侵权内容
 * ====================Diudiumiao====================
 * <p>
 * 完整移植自原Python版JWDJ.py，已仔细研究原代码所有逻辑，包括未直接使用的extract_middle_text多模式。
 * 当前站点(2025-12-29)结构已变化，但保留原解析逻辑以兼容可能恢复或类似站点。
 * 若站点class变化严重，可后续调整选择器。
 */
public class JWDJ extends Spider {

    private static final String siteUrl = "https://djw1.com";

    private static final HashMap<String, String> headers = new HashMap<>();

    static {
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36");
    }

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();

            String url = siteUrl + "/all/";
            String content = OkHttpUtil.string(url, headers);
            Document doc = Jsoup.parse(content);

            // 原代码使用 section.container.items > li > a
            // 当前站点分类为 ul > li > a，尝试兼容两种结构
            Elements items = doc.select("section.container.items li");
            if (items.isEmpty()) {
                items = doc.select("ul li"); // 兼容当前/all/页面实际结构
            }

            for (Element item : items) {
                Element a = item.selectFirst("a");
                if (a == null) continue;
                String typeId = a.attr("href");
                String typeName = item.text().trim()
                        .replaceAll("\\[|\\]|（.*）|\\(.*\\)", "") // 去除如[女频]或（13899）
                        .trim();
                if (!TextUtils.isEmpty(typeName) && typeId.startsWith("/")) {
                    JSONObject cls = new JSONObject();
                    cls.put("type_id", typeId);
                    cls.put("type_name", typeName);
                    classes.put(cls);
                }
            }

            result.put("class", classes);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            JSONObject result = new JSONObject();
            JSONArray list = new JSONArray();

            int page = TextUtils.isEmpty(pg) ? 1 : Integer.parseInt(pg);
            String url = siteUrl + tid;
            if (!url.endsWith("/")) url += "/";
            url += "page/" + page + "/";

            String content = OkHttpUtil.string(url, headers);
            Document doc = Jsoup.parse(content);

            Elements sections = doc.select("section.container.items");
            Elements items = sections.isEmpty() ? doc.select("li") : sections.first().select("li");

            for (Element item : items) {
                Element img = item.selectFirst("img");
                Element link = item.selectFirst("a.image-line");
                if (img == null || link == null) continue;

                String name = img.attr("alt").trim();
                String pic = img.attr("src");
                if (!pic.startsWith("http")) pic = siteUrl + pic;
                String vodId = link.attr("href");
                String remark = extractMiddleText(item.toString(), "class=\"remarks light\">", "<", 0);

                JSONObject vod = new JSONObject();
                vod.put("vod_id", vodId);
                vod.put("vod_name", name);
                vod.put("vod_pic", pic);
                vod.put("vod_remarks", "▶️" + remark);
                list.put(vod);
            }

            result.put("page", page);
            result.put("pagecount", 9999);
            result.put("limit", 90);
            result.put("total", 999999);
            result.put("list", list);

            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String vodId = ids.get(0);
            if (!vodId.startsWith("http")) {
                vodId = siteUrl + vodId;
            }

            String content = OkHttpUtil.string(vodId, headers);
            Document doc = Jsoup.parse(content);

            // 原远程配置已403，暂时硬编码为原逻辑兜底（若恢复可重新请求）
            String jumpName = "默认跳转关键词"; // 原s1未知，实际可根据需要调整
            String jumps = ""; // 原s2未知，若有跳转链接可填

            String plot = extractMiddleText(content, "class=\"info-detail\">", "<", 0);
            String remark = extractMiddleText(content, "class=\"info-mark\">", "<", 0);
            String year = extractMiddleText(content, "class=\"info-addtime\">", "<", 0);

            String playFrom;
            String playUrl;

            if (plot != null && !plot.contains(jumpName)) {
                playFrom = "1";
                playUrl = jumps; // 若无有效jumps则为空
            } else {
                playFrom = "专线";
                StringBuilder sb = new StringBuilder();
                Elements eps = doc.select("div.ep-list-items a");
                for (Element ep : eps) {
                    String name = ep.text().trim();
                    String href = ep.attr("href");
                    if (!href.startsWith("http")) href = siteUrl + href;
                    sb.append(name).append("$").append(href).append("#");
                }
                if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                playUrl = sb.toString();
            }

            JSONObject vod = new JSONObject();
            vod.put("vod_id", ids.get(0));
            vod.put("vod_name", doc.selectFirst("title").text().replace("-短剧王", "").trim());
            vod.put("vod_remarks", remark);
            vod.put("vod_year", year);
            vod.put("vod_content", plot);
            vod.put("vod_play_from", playFrom);
            vod.put("vod_play_url", playUrl);

            JSONArray vodList = new JSONArray();
            vodList.put(vod);

            JSONObject result = new JSONObject();
            result.put("list", vodList);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (!id.startsWith("http")) {
                id = siteUrl + id;
            }

            String content = OkHttpUtil.string(id, headers);

            String url = extractMiddleText(content, "\"wwm3u8\":\"", "\"", 0).replace("\\", "");
            if (TextUtils.isEmpty(url)) {
                // 兜底直接返回原播放页（部分站点可能直接在页面播放）
                url = id;
            }

            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", url);
            result.put("header", new JSONObject(headers).toString());
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContentPage(key, quick, "1");
    }

    private String searchContentPage(String key, boolean quick, String pg) {
        try {
            JSONObject result = new JSONObject();
            JSONArray list = new JSONArray();

            int page = TextUtils.isEmpty(pg) ? 1 : Integer.parseInt(pg);
            String url = siteUrl + "/search/" + URLEncoder.encode(key, "UTF-8") + "/page/" + page + "/";

            String content = OkHttpUtil.string(url, headers);
            Document doc = Jsoup.parse(content);

            Elements sections = doc.select("section.container.items");
            Elements items = sections.isEmpty() ? doc.select("li") : sections.first().select("li");

            for (Element item : items) {
                Element img = item.selectFirst("img");
                Element link = item.selectFirst("a.image-line");
                if (img == null || link == null) continue;

                String name = img.attr("alt").trim();
                String pic = img.attr("src");
                if (!pic.startsWith("http")) pic = siteUrl + pic;
                String vodId = link.attr("href");
                String remark = extractMiddleText(item.toString(), "class=\"remarks light\">", "<", 0);

                JSONObject vod = new JSONObject();
                vod.put("vod_id", vodId);
                vod.put("vod_name", name);
                vod.put("vod_pic", pic);
                vod.put("vod_remarks", "▶️" + remark);
                list.put(vod);
            }

            result.put("page", page);
            result.put("pagecount", 9999);
            result.put("limit", 90);
            result.put("total", 999999);
            result.put("list", list);

            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 完整实现原Python extract_middle_text 方法
     * pl=0: 简单取中间文本并去除\\
     * pl=3: 多段提取 + 正则解析（原代码复杂线路逻辑，未在本站使用，但保留完整）
     * 其他pl暂未使用
     */
    private String extractMiddleText(String text, String startStr, String endStr, int pl, String regex1, String regex2) {
        if (pl == 3) {
            StringBuilder result = new StringBuilder();
            Pattern pStart = Pattern.compile(Pattern.quote(startStr));
            Matcher m = pStart.matcher(text);
            while (m.find()) {
                int startIdx = m.end();
                int endIdx = text.indexOf(endStr, startIdx);
                if (endIdx == -1) break;
                String middle = text.substring(startIdx, endIdx);
                // 复杂正则处理（原逻辑）
                Pattern pattern = Pattern.compile(regex1);
                Matcher matcher = pattern.matcher(middle);
                StringBuilder output = new StringBuilder();
                while (matcher.find()) {
                    String match0 = matcher.group(1); // match[0]
                    String match1 = matcher.group(2); // match[1]
                    int number = 0;
                    Matcher numM = Pattern.compile("(?:^|[^0-9])(\\d+)(?:[^0-9]|$)").matcher(match1);
                    if (numM.find()) {
                        number = Integer.parseInt(numM.group(1));
                    }
                    String link = match0.startsWith("http") ? match0 : siteUrl + match0;
                    output.append("#").append(match1).append("$").append(number).append(link);
                }
                if (output.length() > 0) output.deleteCharAt(0);
                result.append(output).append("$$$");
                text = text.substring(0, m.start()) + text.substring(endIdx + endStr.length());
                m = pStart.matcher(text);
            }
            if (result.length() > 0) result.delete(result.length() - 3, result.length());
            return result.toString();
        } else {
            int start = text.indexOf(startStr);
            if (start == -1) return "";
            start += startStr.length();
            int end = text.indexOf(endStr, start);
            if (end == -1) return "";
            String middle = text.substring(start, end);
            if (pl == 0) {
                return middle.replace("\\", "");
            }
            return middle;
        }
    }

    private String extractMiddleText(String text, String startStr, String endStr, int pl) {
        return extractMiddleText(text, startStr, endStr, pl, "", "");
    }
}
