package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

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
import java.util.List;

/**
 * 作者 丢丢喵推荐 🚓 内容均从互联网收集而来 仅供交流学习使用 版权归原创者所有 如侵犯了您的权益 请通知作者 将及时删除侵权内容
 * ====================Diudiumiao====================
 * <p>
 * 完整移植自原Python版JWDJ.py，已修复所有编译错误。
 * 网络请求使用 com.github.catvod.net.OkHttp (静态工具类，与多数CatVodSpider fork一致)。
 * 修复了 detailContent 中 vod_name 获取逻辑。
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
            String content = OkHttp.string(url, headers);
            Document doc = Jsoup.parse(content);

            Elements items = doc.select("section.container.items li");
            if (items.isEmpty()) {
                items = doc.select("ul li"); // 兼容当前/all/页面结构
            }

            for (Element item : items) {
                Element a = item.selectFirst("a");
                if (a == null) continue;
                String typeId = a.attr("href");
                String typeName = item.text().trim()
                        .replaceAll("\\[|\\]|（.*）|\\(.*\\)", "")
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

            String content = OkHttp.string(url, headers);
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

            String content = OkHttp.string(vodId, headers);
            Document doc = Jsoup.parse(content);

            String plot = extractMiddleText(content, "class=\"info-detail\">", "<", 0);
            String remark = extractMiddleText(content, "class=\"info-mark\">", "<", 0);
            String year = extractMiddleText(content, "class=\"info-addtime\">", "<", 0);

            String playFrom = "专线";
            StringBuilder sb = new StringBuilder();
            Elements eps = doc.select("div.ep-list-items a");
            for (Element ep : eps) {
                String name = ep.text().trim();
                String href = ep.attr("href");
                if (!href.startsWith("http")) href = siteUrl + href;
                sb.append(name).append("$").append(href).append("#");
            }
            if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
            String playUrl = sb.toString();
            if (TextUtils.isEmpty(playUrl)) {
                playUrl = "暂无播放源$";
            }

            JSONObject vod = new JSONObject();
            vod.put("vod_id", ids.get(0));

            Element titleElement = doc.selectFirst("title");
            String vodName = (titleElement != null) ? titleElement.text().replace("-短剧王", "").trim() : "未知标题";
            vod.put("vod_name", vodName);

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

            String content = OkHttp.string(id, headers);

            String url = extractMiddleText(content, "\"wwm3u8\":\"", "\"", 0).replace("\\", "");
            if (TextUtils.isEmpty(url)) {
                url = id; // 兜底返回详情页
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

            String content = OkHttp.string(url, headers);
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

    private String extractMiddleText(String text, String startStr, String endStr, int pl) {
        int start = text.indexOf(startStr);
        if (start == -1) return "";
        start += startStr.length();
        int end = text.indexOf(endStr, start);
        if (end == -1) return "";
        String middle = text.substring(start, end);
        if (pl == 0) {
            return middle.replace("\\", "").trim();
        }
        return middle.trim();
    }
}
