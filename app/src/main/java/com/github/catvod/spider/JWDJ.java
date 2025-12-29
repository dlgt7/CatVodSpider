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
 * 修复分类空白问题（2025-12-29 当前站点结构变化）。
 * https://djw1.com/all/ 分类现在是纯 <a> 标签列表，无 <ul><li> 包裹。
 * 调整选择器直接抓取所有 <a> 链接，并清理名称（去除（数字））。
 */
public class JWDJ extends Spider {

    private static final String siteUrl = "https://djw1.com";

    private static final HashMap<String, String> headers = new HashMap<>();

    static {
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36");
    }

    @Override
    public void init(Context context, String extend) {
        // 多数 fork 项目 init 为空，不调用 super
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();

            String url = siteUrl + "/all/";
            String content = OkHttp.string(url, headers);
            Document doc = Jsoup.parse(content);

            // 当前 /all/ 页面分类是直接的 <a href="/tag/.../">名称（数字）</a> ，无容器
            Elements catLinks = doc.select("a[href^=/tag/]");
            // 备选：如果有其他链接，可进一步过滤
            for (Element a : catLinks) {
                String typeId = a.attr("href");
                String typeName = a.text().trim().replaceAll("\\（\\d+\\）|\\(\\d+\\)$", "").trim();
                if (!TextUtils.isEmpty(typeName) && typeId.startsWith("/tag/")) {
                    JSONObject cls = new JSONObject();
                    cls.put("type_id", typeId);
                    cls.put("type_name", typeName);
                    classes.put(cls);
                }
            }

            // 如果仍为空，可添加常见分类硬编码兜底（可选）
            if (classes.length() == 0) {
                String[] commonCats = {"女频", "男频", "逆袭", "重生", "战神", "豪门", "古装", "现代言情"};
                String[] paths = {"/tag/%e5%a5%b3%e9%a2%91/", "/tag/%e7%94%b7%e9%a2%91/", "/tag/%e9%80%86%e8%a2%ad/", "/tag/%e9%87%8d%e7%94%9f/", "/tag/%e6%88%98%e7%a5%9e/", "/tag/%e8%b1%aa%e9%97%a8/", "/tag/%e5%8f%a4%e8%a3%85/", "/tag/%e7%8e%b0%e4%bb%a3%e8%a8%80%e6%83%85/"};
                for (int i = 0; i < commonCats.length; i++) {
                    JSONObject cls = new JSONObject();
                    cls.put("type_id", paths[i]);
                    cls.put("type_name", commonCats[i]);
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

    // 以下方法保持不变（列表、详情、播放、搜索）
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

            // 当前列表结构可能为 section 或直接 ul/li
            Elements items = doc.select("section.container.items li");
            if (items.isEmpty()) {
                items = doc.select("ul li"); // 兼容
            }

            for (Element item : items) {
                Element img = item.selectFirst("img");
                Element link = item.selectFirst("a");
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

            String content = OkHttp.string(url, headers);
            Document doc = Jsoup.parse(content);

            Elements items = doc.select("section.container.items li");
            if (items.isEmpty()) {
                items = doc.select("ul li");
            }

            for (Element item : items) {
                Element img = item.selectFirst("img");
                Element link = item.selectFirst("a");
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
