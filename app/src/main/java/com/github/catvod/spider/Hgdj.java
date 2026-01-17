package com.github.catvod.spider;

import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Hgdj extends Spider {

    private String xurl = "https://mov.cenguigui.cn";

    private Map<String, String> getHeaderx() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
        headers.put("Referer", xurl + "/");
        // 如果发现还需要其他 header（如 Origin），可在这里继续添加
        return headers;
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            List<Class> classes = new ArrayList<>();
            String[] names = {"都市", "古装", "逆袭", "重生", "甜宠", "虐恋", "战神", "神医", "总裁", "玄幻"};
            for (String name : names) {
                classes.add(new Class(name, name));
            }
            return Result.string(classes, new ArrayList<Vod>(), new JSONObject());
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String homeVideoContent() {
        return categoryContent("都市", "1", false, null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = Integer.parseInt(TextUtils.isEmpty(pg) ? "1" : pg);
            String url = xurl + "/duanju/api.php?name=" + tid + "&page=" + page;

            String json = OkHttp.string(url, getHeaderx());
            JSONObject dataObj = new JSONObject(json);
            JSONArray vodList = dataObj.optJSONArray("data");

            List<Vod> list = new ArrayList<>();
            if (vodList != null) {
                for (int i = 0; i < vodList.length(); i++) {
                    JSONObject vodObj = vodList.getJSONObject(i);
                    Vod vod = new Vod();
                    vod.setVodId(vodObj.getString("book_id"));
                    vod.setVodName(vodObj.getString("title"));
                    vod.setVodPic(vodObj.getString("cover"));
                    vod.setVodRemarks(vodObj.optString("type"));
                    list.add(vod);
                }
            }

            // 分页保守处理：有数据就认为还有下一页（最稳方案）
            boolean hasMore = !list.isEmpty();
            int nextPage = hasMore ? page + 1 : page;

            return Result.get()
                    .page(page, nextPage, 20, nextPage * 20)
                    .vod(list)
                    .string();

        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("分类加载失败: " + e.getMessage()).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String did = ids.get(0);
            String url = xurl + "/duanju/api.php?book_id=" + did;
            String json = OkHttp.string(url, getHeaderx());
            JSONObject data = new JSONObject(json);

            Vod vod = new Vod();
            vod.setVodId(did);
            vod.setVodName(data.optString("title"));
            vod.setVodPic(data.optString("cover"));
            vod.setVodActor(data.optString("author", "未知"));
            vod.setVodRemarks(data.optString("category", "未知"));
            vod.setVodYear(data.optString("duration", "未知"));
            vod.setVodContent(data.optString("desc", "内容简介加载中..."));

            vod.setVodPlayFrom("短剧专线");

            JSONArray episodes = data.optJSONArray("data");
            List<String> playList = new ArrayList<>();
            if (episodes != null) {
                for (int i = 0; i < episodes.length(); i++) {
                    JSONObject ep = episodes.getJSONObject(i);
                    playList.add(ep.getString("title") + "$" + ep.getString("video_id"));
                }
            }
            vod.setVodPlayUrl(TextUtils.join("#", playList));

            return Result.string(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("详情加载失败").string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        if (TextUtils.isEmpty(key) || key.trim().length() < 1) return "";
        return categoryContent(key, "1", false, null);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String apiUrl = xurl + "/duanju/api.php?video_id=" + id;
            String json = OkHttp.string(apiUrl, getHeaderx());
            JSONObject data = new JSONObject(json);
            String videoUrl = data.getJSONObject("data").getString("url");

            // 关键优化：尝试追踪 302 跳转，得到最终干净地址
            String finalUrl = OkHttp.getLocation(videoUrl, getHeaderx());
            if (!TextUtils.isEmpty(finalUrl)) {
                videoUrl = finalUrl;
            }

            Result result = Result.get()
                    .url(videoUrl)
                    .header(getHeaderx())
                    .parse(0);

            // 智能判断并声明正确的容器格式（解决 container unsupported 的核心）
            String lower = videoUrl.toLowerCase();
            if (lower.contains(".m3u8") || lower.contains("m3u8")) {
                result.m3u8();                    // application/x-mpegURL
            } else if (lower.contains(".mpd") || lower.contains("dash") || lower.contains(".mpd")) {
                result.dash();                    // application/dash+xml
            } else if (lower.contains(".flv")) {
                result.format("video/x-flv");
            } else {
                // 兜底：大部分短剧仍然是 mp4
                result.format("video/mp4");
            }

            // 如果上面仍然不行，可尝试万能兜底（注释掉即可切换）
            // result.format("application/octet-stream");

            return result.string();

        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("播放解析失败: " + e.getMessage()).string();
        }
    }
}
