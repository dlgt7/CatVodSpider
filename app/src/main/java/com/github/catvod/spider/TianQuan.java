package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.UA;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;

/**
 * 甜圈短剧 - 增强修复版
 * 整合了 AntiCrawlerEnhancer 自动同步 Cookie 和 WebView 预热功能
 */
public class TianQuan extends Spider {
    private static final String siteUrl = "https://mov.cenguigui.cn";
    private static final String apiPath = "/duanju/api.php";

    @Override
    public void init(Context context) throws Exception {
        super.init(context);
        // 1. 初始化反爬增强器，确保环境就绪
        AntiCrawlerEnhancer.get().init(context);
        // 2. 启动 WebView 预热，模拟真实用户访问首页以通过 JS 挑战并获取有效 Cookie
        WebViewHelper.warmup(siteUrl, () -> {
            SpiderDebug.log("天圈短剧：WebView 预热挑战已完成，Cookie 已自动存入系统。");
        });
    }

    /**
     * 核心修改：使用增强型请求工具
     * 它会自动添加随机延迟、同步 WebView 的 Cookie、注入浏览器指纹
     */
    private String fetchApi(String url) {
        return AntiCrawlerEnhancer.get().enhancedGet(url, null);
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONArray classes = new JSONArray();
            // 定义分类
            String[] typeNames = {"推荐榜", "新剧", "逆袭", "霸总", "现代言情", "打脸虐渣", "豪门恩怨", "神豪", "马甲"};
            for (String name : typeNames) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("type_id", name);
                jsonObject.put("type_name", name);
                classes.put(jsonObject);
            }

            // 获取首页推荐数据
            String url = siteUrl + apiPath + "?classname=" + URLEncoder.encode("推荐榜", "UTF-8") + "&offset=0";
            String content = fetchApi(url);
            
            JSONObject result = new JSONObject();
            result.put("class", classes);
            result.put("list", parseVideoList(content));
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
            // API offset 通常从 0 开始计数
            int offset = (page - 1) * 10; 
            String url = siteUrl + apiPath + "?classname=" + URLEncoder.encode(tid, "UTF-8") + "&offset=" + offset;
            
            String content = fetchApi(url);
            
            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("list", parseVideoList(content));
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    private JSONArray parseVideoList(String content) throws Exception {
        JSONArray videos = new JSONArray();
        if (TextUtils.isEmpty(content)) return videos;
        
        JSONObject apiData = new JSONObject(content);
        if (apiData.has("data")) {
            JSONArray dataList = apiData.getJSONArray("data");
            for (int i = 0; i < dataList.length(); i++) {
                JSONObject item = dataList.getJSONObject(i);
                JSONObject v = new JSONObject();
                v.put("vod_id", item.optString("book_id"));
                v.put("vod_name", item.optString("title"));
                v.put("vod_pic", item.optString("cover"));
                v.put("vod_remarks", item.optString("sub_title"));
                videos.put(v);
            }
        }
        return videos;
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String url = siteUrl + apiPath + "?book_id=" + ids.get(0);
            String content = fetchApi(url);
            JSONObject apiData = new JSONObject(content);
            
            if (apiData.has("data")) {
                JSONObject data = apiData.getJSONObject("data");
                JSONObject vod = new JSONObject();
                vod.put("vod_id", data.optString("book_id"));
                vod.put("vod_name", data.optString("title"));
                vod.put("vod_pic", data.optString("cover"));
                vod.put("type_name", data.optString("classname"));
                vod.put("vod_content", data.optString("intro"));
                
                // 剧集处理
                JSONArray chapters = data.optJSONArray("chapters");
                StringBuilder playList = new StringBuilder();
                if (chapters != null) {
                    for (int i = 0; i < chapters.length(); i++) {
                        JSONObject chapter = chapters.getJSONObject(i);
                        playList.append(chapter.optString("title"))
                                .append("$")
                                .append(chapter.optString("video_id"));
                        if (i < chapters.length() - 1) playList.append("#");
                    }
                }
                vod.put("vod_play_from", "甜圈源码");
                vod.put("vod_play_url", playList.toString());

                JSONObject result = new JSONObject();
                result.put("list", new JSONArray().put(vod));
                return result.toString();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // 获取播放地址
            String url = siteUrl + apiPath + "?video_id=" + id;
            String content = fetchApi(url);
            JSONObject apiData = new JSONObject(content);

            if (apiData.has("data")) {
                JSONObject data = apiData.getJSONObject("data");
                String playUrl = data.optString("url");
                
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", playUrl);
                // 必须保证播放器的 UA 和请求 API 的 UA 一致，否则某些 m3u8 会报 403
                JSONObject header = new JSONObject();
                header.put("User-Agent", UA.DEFAULT);
                result.put("header", header.toString());
                return result.toString();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return categoryContent(key, "1", false, null);
    }
}
