package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

/**
 * 甜圈短剧 - 最终修复版（兼容 CatVodSpider 项目）
 * 修复点：init 方法添加 throws Exception（父类 Spider 的 init(Context) throws Exception）
 * 已适配当前站点：https://mov.cenguigui.cn
 * 使用项目内置 OkHttp：com.github.catvod.net.OkHttp
 * 2025年12月14日 测试完全正常（站点和API可用，播放m3u8直链正常）
 */
public class TianQuan extends Spider {
    private static final String siteUrl = "https://mov.cenguigui.cn";
    private static final String apiPath = "/duanju/api.php";

    @Override
    public void init(Context context) throws Exception {
        super.init(context);
    }

    protected HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");
        headers.put("sec-ch-ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"134\", \"Google Chrome\";v=\"134\"");
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua-platform", "\"macOS\"");
        headers.put("DNT", "1");
        headers.put("Sec-Fetch-Site", "cross-site");
        headers.put("Sec-Fetch-Mode", "no-cors");
        headers.put("Sec-Fetch-Dest", "video");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        return headers;
    }

    private String fetchApi(String classname, String offset) {
        String url = siteUrl + apiPath + "?classname=" + URLEncoder.encode(classname) + "&offset=" + offset;
        return OkHttp.string(url, getHeaders());
    }

    private String fetchApiByBookId(String bookId) {
        String url = siteUrl + apiPath + "?book_id=" + bookId;
        return OkHttp.string(url, getHeaders());
    }

    private String fetchApiByVideoId(String videoId) {
        String url = siteUrl + apiPath + "?video_id=" + videoId;
        return OkHttp.string(url, getHeaders());
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONArray classes = new JSONArray();

            classes.put(new JSONObject().put("type_id", "推荐榜").put("type_name", "🔥 推荐榜"));

            String[] typeNames = {"新剧", "逆袭", "霸总", "现代言情", "打脸虐渣", "豪门恩怨", "神豪", "马甲",
                    "都市日常", "战神归来", "小人物", "女性成长", "大女主", "穿越", "都市修仙", "强者回归", "亲情",
                    "古装", "重生", "闪婚", "赘婿逆袭", "虐恋", "追妻", "天下无敌", "家庭伦理", "萌宝", "古风权谋",
                    "职场", "奇幻脑洞", "异能", "无敌神医", "古风言情", "传承觉醒", "现言甜宠", "奇幻爱情", "乡村",
                    "历史古代", "王妃", "高手下山", "娱乐圈", "强强联合", "破镜重圆", "暗恋成真", "民国", "欢喜冤家",
                    "系统", "真假千金", "龙王", "校园", "穿书", "女帝", "团宠", "年代爱情", "玄幻仙侠", "青梅竹马",
                    "悬疑推理", "皇后", "替身", "大叔", "喜剧", "剧情"};

            for (String name : typeNames) {
                classes.put(new JSONObject().put("type_id", name).put("type_name", "🎬 " + name));
            }

            String content = fetchApi("推荐榜", "0");
            JSONObject apiData = new JSONObject(content);
            JSONArray videos = new JSONArray();

            if (apiData.has("data")) {
                JSONArray dataList = apiData.getJSONArray("data");
                int limit = Math.min(dataList.length(), 20);
                for (int i = 0; i < limit; i++) {
                    JSONObject item = dataList.getJSONObject(i);
                    JSONObject v = new JSONObject();
                    v.put("vod_id", item.optString("book_id"));
                    v.put("vod_name", item.optString("title"));
                    v.put("vod_pic", item.optString("cover"));
                    v.put("vod_remarks", item.optString("sub_title", "") +
                            (item.optInt("episode_cnt", 0) > 0 ? " | " + item.optInt("episode_cnt") + "集" : ""));
                    videos.put(v);
                }
            }

            JSONObject result = new JSONObject();
            result.put("class", classes);
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = Integer.parseInt(pg);
            String offset = String.valueOf(page - 1);
            String content = fetchApi(tid, offset);
            JSONObject apiData = new JSONObject(content);

            JSONArray videos = new JSONArray();
            if (apiData.has("data")) {
                JSONArray dataList = apiData.getJSONArray("data");
                for (int i = 0; i < dataList.length(); i++) {
                    JSONObject item = dataList.getJSONObject(i);
                    JSONObject v = new JSONObject();
                    v.put("vod_id", item.optString("book_id"));
                    v.put("vod_name", item.optString("title"));
                    v.put("vod_pic", item.optString("cover"));
                    v.put("vod_remarks", item.optString("sub_title", "") +
                            (item.optInt("episode_cnt", 0) > 0 ? " | " + item.optInt("episode_cnt") + "集" : ""));
                    videos.put(v);
                }
            }

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("pagecount", page + 1);
            result.put("limit", 30);
            result.put("total", Integer.MAX_VALUE);
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String bookId = ids.get(0);
            String content = fetchApiByBookId(bookId);
            JSONObject apiData = new JSONObject(content);

            JSONObject vod = new JSONObject();
            vod.put("vod_id", bookId);
            vod.put("vod_name", apiData.optString("title"));
            vod.put("vod_pic", apiData.optString("cover"));

            String typeName = "";
            if (apiData.has("category_schema")) {
                JSONArray catArray = apiData.getJSONArray("category_schema");
                List<String> catNames = new ArrayList<>();
                for (int i = 0; i < catArray.length(); i++) {
                    JSONObject cat = catArray.getJSONObject(i);
                    catNames.add(cat.optString("name"));
                }
                typeName = TextUtils.join("/", catNames);
            }
            vod.put("type_name", typeName);

            vod.put("vod_year", apiData.optString("time", ""));
            vod.put("vod_remarks", apiData.optString("duration", ""));
            vod.put("vod_content", apiData.optString("video_desc", apiData.optString("desc", "")));

            TreeMap<String, String> playMap = new TreeMap<>(Comparator.comparingInt(o -> 0));
            if (apiData.has("data")) {
                JSONArray episodes = apiData.getJSONArray("data");
                List<String> items = new ArrayList<>();
                for (int i = 0; i < episodes.length(); i++) {
                    JSONObject ep = episodes.getJSONObject(i);
                    String title = ep.optString("title", "第" + (i + 1) + "集");
                    String vid = ep.optString("video_id");
                    if (!TextUtils.isEmpty(vid)) {
                        items.add(title + "$" + vid);
                    }
                }
                if (!items.isEmpty()) {
                    playMap.put("爱看短剧", TextUtils.join("#", items));
                }
            }

            if (!playMap.isEmpty()) {
                vod.put("vod_play_from", TextUtils.join("$$$", playMap.keySet()));
                vod.put("vod_play_url", TextUtils.join("$$$", playMap.values()));
            }

            JSONObject result = new JSONObject();
            result.put("list", new JSONArray().put(vod));
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String content = fetchApiByVideoId(id);
            JSONObject apiData = new JSONObject(content);

            JSONObject result = new JSONObject();
            if (apiData.has("data")) {
                JSONObject data = apiData.getJSONObject("data");
                String url = data.optString("url");
                if (!TextUtils.isEmpty(url)) {
                    result.put("parse", 0);
                    result.put("playUrl", "");
                    result.put("url", url);
                    JSONObject header = new JSONObject();
                    header.put("User-Agent", getHeaders().get("User-Agent"));
                    result.put("header", header.toString());
                }
            }
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        return categoryContent(key, pg, false, null);
    }
}
