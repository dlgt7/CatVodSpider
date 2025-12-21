package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Gz360 extends Spider {

    private static final String SITE = "https://www.360zyapi.com";
    private static final String API_LIST = SITE + "/api.php/provide/vod/";
    private static final String API_DETAIL = SITE + "/api.php/provide/vod/?ac=detail";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        // 可根据实际接口补充更多分类

        Result result = Result.get();
        result.lazyList(() -> classes); // 使用 lazyList 避免立即序列化
        return result.string();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = API_LIST + "?ac=detail&t=" + tid + "&pg=" + pg;
        String content = OkHttp.string(url, getHeaders());
        JSONObject json = new JSONObject(content);

        JSONObject data = json.optJSONObject("data");
        if (data == null) return Result.get().string();

        JSONArray list = data.getJSONArray("list");
        List<Vod> vods = new ArrayList<>();

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            Vod vod = new Vod();
            vod.setVodId(item.getString("vod_id"));
            vod.setVodName(item.getString("vod_name"));
            vod.setVodPic(item.getString("vod_pic"));
            vod.setVodRemarks(item.optString("vod_remarks", ""));
            vods.add(vod);
        }

        Result result = Result.get();
        result.lazyList(() -> vods);
        result.limit(data.getInt("limit"));
        result.page(data.getInt("page"));
        result.pagecount(data.getInt("pagecount"));
        result.total(data.getInt("total"));

        return result.string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = API_DETAIL + "&ids=" + id;
        String content = OkHttp.string(url, getHeaders());
        JSONObject json = new JSONObject(content);

        JSONObject data = json.optJSONObject("data");
        if (data == null || data.getJSONArray("list").length() == 0) {
            return Result.get().string();
        }

        JSONObject item = data.getJSONArray("list").getJSONObject(0);
        Vod vod = new Vod();
        vod.setVodId(item.getString("vod_id"));
        vod.setVodName(item.getString("vod_name"));
        vod.setVodPic(item.getString("vod_pic"));
        vod.setTypeName(item.optString("vod_class", ""));
        vod.setVodYear(item.optString("vod_year", ""));
        vod.setVodArea(item.optString("vod_area", ""));
        vod.setVodRemarks(item.optString("vod_remarks", ""));
        vod.setVodActor(item.optString("vod_actor", ""));
        vod.setVodDirector(item.optString("vod_director", ""));
        vod.setVodContent(item.optString("vod_content", "").replaceAll("<[^>]+>", "").trim());

        // 处理播放线路
        String[] fromArray = item.getString("vod_play_from").split("\\$\\$\\$");
        String[] urlArray = item.getString("vod_play_url").split("\\$\\$\\$");

        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();

        for (int i = 0; i < fromArray.length && i < urlArray.length; i++) {
            if (i > 0) {
                playFrom.append("$$$");
                playUrl.append("$$$");
            }
            playFrom.append(fromArray[i]);
            playUrl.append(urlArray[i].replace("#", "$")); // 统一用 $ 分隔集数
        }

        vod.setVodPlayFrom(playFrom.toString());
        vod.setVodPlayUrl(playUrl.toString());

        List<Vod> vodList = new ArrayList<>();
        vodList.add(vod);

        Result result = Result.get();
        result.lazyList(() -> vodList);
        return result.string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 360zyapi 播放链接是直链，不需要二次解析
        Result result = Result.get();
        result.parse(0);  // 0 = 直链播放，1 = 强制解析
        result.url(id);
        return result.string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = API_LIST + "?wd=" + key + "&pg=1";
        String content = OkHttp.string(url, getHeaders());
        JSONObject json = new JSONObject(content);

        JSONObject data = json.optJSONObject("data");
        if (data == null) return Result.get().string();

        JSONArray list = data.getJSONArray("list");
        List<Vod> vods = new ArrayList<>();

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            Vod vod = new Vod();
            vod.setVodId(item.getString("vod_id"));
            vod.setVodName(item.getString("vod_name"));
            vod.setVodPic(item.getString("vod_pic"));
            vod.setVodRemarks(item.optString("vod_remarks", ""));
            vods.add(vod);
        }

        Result result = Result.get();
        result.lazyList(() -> vods);
        return result.string();
    }
}
