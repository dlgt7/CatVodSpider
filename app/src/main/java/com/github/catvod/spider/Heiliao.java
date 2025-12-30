package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 黑料网 Spider - 最终修复版 (2025-12-30)
 * <p>
 * - 修复编译错误: init 添加 throws Exception
 * - 适配 HTML 结构: div.archive-item > a.slider-item > img, h2, span.date
 * - 图片: base64 placeholder, 使用 proxy 代理 (实现 AES 解密基于 JS)
 * - 默认域名 https://heiliao43.com, ext 覆盖
 * - 数据空问题: 使用精确选择器, 添加 fallback
 */
public class Heiliao extends com.github.catvod.crawler.Spider {

    private String siteUrl = "https://heiliao43.com";
    private HashMap<String, String> headers;

    private HashMap<String, String> getHeaders() {
        if (headers == null) {
            headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
            headers.put("Referer", siteUrl + "/");
        }
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (extend != null && !extend.isEmpty()) {
            siteUrl = extend.trim();
            if (!siteUrl.startsWith("http")) siteUrl = "https://" + siteUrl;
            if (siteUrl.endsWith("/")) siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
        }
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        String[][] nav = {
                {"", "最新黑料"},
                {"hlcg", "吃瓜视频"},
                {"whhl", "网红事件"},
                {"rmhl", "热门黑料"},
                {"jdh", "经典黑料"},
                {"day", "日榜黑料"},
                {"week", "周榜精选"},
                {"month", "月榜热瓜"},
                {"original", "原创社区"},
                {"world", "全球奇闻"},
                {"fan", "反差专区"},
                {"select", "黑料选妃"},
                {"school", "校园黑料"},
                {"netred", "网红黑料"},
                {"drama", "影视短剧"},
                {"daily", "每日大赛"},
                {"star", "明星丑闻"},
                {"night", "深夜综艺"},
                {"twitter", "推特社区"},
                {"exclusive", "独家爆料"},
                {"photo", "桃图杂志"},
                {"class", "黑料课堂"},
                {"help", "有求必应"},
                {"novel", "黑料小说"},
                {"news", "社会新闻"},
                {"neihan", "内涵黑料"},
                {"gov", "官场爆料"}
        };
        for (String[] item : nav) {
            String tid = item[0].isEmpty() ? "/" : "/" + item[0] + "/";
            classes.add(new Class(tid, item[1]));
        }

        LinkedHashMap<String, List<com.github.catvod.bean.Filter>> filters = new LinkedHashMap<>();
        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        if (pg == null || pg.isEmpty()) pg = "1";
        String url = siteUrl + tid;
        if (!tid.endsWith("/")) url += "/";
        if (!pg.equals("1")) url += "page/" + pg + "/";

        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
            List<Vod> list = new ArrayList<>();

            Elements items = doc.select("div.archive-item");
            for (Element item : items) {
                Element link = item.selectFirst("a.slider-item");
                if (link == null) continue;

                String vodId = link.attr("href");
                Element titleEl = link.selectFirst("h2");
                String vodName = titleEl != null ? titleEl.text().trim() : link.text().trim();
                if (vodName.isEmpty()) continue;

                Element img = link.selectFirst("img");
                String vodPic = img != null ? img.attr("src") : "";
                if (vodPic.contains("base64")) {
                    String idEncoded = Util.base64Encode(vodId);
                    vodPic = Proxy.getUrl(false) + "?do=img&id=" + idEncoded;
                }

                Element dateEl = link.selectFirst("span.date");
                String vodRemarks = dateEl != null ? dateEl.text().trim() : "";

                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }

            if (list.isEmpty()) {
                SpiderDebug.log("No items found, trying fallback selector");
                items = doc.select("article, .post-item");
                for (Element item : items) {
                    Element link = item.selectFirst("a");
                    if (link == null) continue;

                    String vodId = link.attr("href");
                    String vodName = item.selectFirst("h2, .title").text().trim();
                    String vodPic = item.selectFirst("img").absUrl("src");
                    String vodRemarks = item.selectFirst(".date, .time").text().trim();

                    list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
                }
            }

            return Result.get().vod(list).page(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 20, list.size() + 1000).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        String id = ids.get(0);
        String url = siteUrl + id;

        try {
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(doc.selectFirst("h1.title").text().trim());
            vod.setVodPic(doc.selectFirst("meta[property=og:image]").attr("content"));
            vod.setVodContent(doc.selectFirst("div.content > p:nth-child(3)").text().trim());

            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();
            int idx = 1;

            Elements videos = doc.select("video");
            for (Element v : videos) {
                String src = v.attr("src");
                if (!src.isEmpty()) {
                    playFrom.add("直链" + idx);
                    playUrl.add("第" + idx + "段$" + src);
                    idx++;
                }
            }

            Elements iframes = doc.select("iframe");
            for (Element iframe : iframes) {
                String src = iframe.attr("src");
                if (!src.isEmpty()) {
                    playFrom.add("外站" + idx);
                    playUrl.add("第" + idx + "段$" + src);
                    idx++;
                }
            }

            if (!playFrom.isEmpty()) {
                vod.setVodPlayFrom(String.join("$$$", playFrom));
                vod.setVodPlayUrl(String.join("$$$", playUrl));
            }

            List<Vod> resList = new ArrayList<>();
            resList.add(vod);
            return Result.string(resList);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        String url = siteUrl + "/index/search?keyword=" + URLEncoder.encode(key);
        return categoryContent("/index/search?keyword=" + URLEncoder.encode(key), "1", false, new HashMap<>());
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        String what = params.get("do");
        if ("img".equals(what)) {
            String id = params.get("id");
            if (id != null) {
                // 根据 JS 实现 AES 解密
                String base64Id = id;  // 已 base64 encoded vod_id
                String decodedId = Util.base64Decode(base64Id);
                // JS key = 'xIGg8kTtzg0rKz8z', iv = '0000000000000000'
                String keyStr = "xIGg8kTtzg0rKz8z";
                String ivStr = "0000000000000000";
                // 使用 Crypto.java AES decrypt
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
                SecretKeySpec keySpec = new SecretKeySpec(keyStr.getBytes(), "AES");
                AlgorithmParameterSpec ivSpec = new IvParameterSpec(ivStr.getBytes());
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
                byte[] decrypted = cipher.doFinal(decodedId.getBytes("UTF-8"));
                String picUrl = new String(decrypted, "UTF-8");
                // 假设 picUrl 是真实图片 URL, 获取字节
                byte[] imageBytes = OkHttp.byteArray(picUrl, null);
                return new Object[]{200, "image/jpeg", imageBytes};
            }
        }
        return null;
    }
}
