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
        // 可以添加更多分类，如果有 filter 需求可构建 LinkedHashMap<String, List<Filter>>
        return Result.get().classes(classes).string();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = API_LIST + "?ac=detail&t=" + tid + "&pg=" + pg;
        String content = OkHttp.string(url, getHeaders());

        JSONObject data = new JSONObject(content).getJSONObject("data");
        JSONArray list = data.getJSONArray("list");

        List<Vod> vods = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String vodId = item.getString("vod_id");
            String vodName = item.getString("vod_name");
            String vodPic = item.getString("vod_pic");
            String vodRemarks = item.optString("vod_remarks", "");

            Vod vod = new Vod();
            vod.setVodId(vodId);
            vod.setVodName(vodName);
            vod.setVodPic(vodPic);
            vod.setVodRemarks(vodRemarks);
            vods.add(vod);
        }

        int limit = data.getInt("limit");
        int page = data.getInt("page");
        int pagecount = data.getInt("pagecount");
        int total = data.getInt("total");

        return Result.get().list(vods).limit(limit).page(page).pagecount(pagecount).total(total).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = API_DETAIL + "&ids=" + id;
        String content = OkHttp.string(url, getHeaders());

        JSONObject data = new JSONObject(content).getJSONObject("data");
        JSONArray list = data.getJSONArray("list");
        JSONObject item = list.getJSONObject(0);

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
        vod.setVodContent(item.optString("vod_content", ""));

        // 播放列表
        HashMap<String, String> playFrom = new HashMap<>();
        HashMap<String, String> playUrl = new HashMap<>();

        String playListStr = item.getString("vod_play_url");
        String[] fromList = item.getString("vod_play_from").split("\\$\\$\\$");
        String[] urlList = playListStr.split("\\$\\$\\$");

        for (int i = 0; i < fromList.length && i < urlList.length; i++) {
            String from = fromList[i];
            String urls = urlList[i].replace("#", "$"); // 统一为 $ 分隔
            playFrom.put(from, from);
            playUrl.put(from, urls);
        }

        vod.setVodPlayFrom(String.join("$$$", playFrom.values()));
        vod.setVodPlayUrl(String.join("$$$", playUrl.values()));

        List<Vod> vodList = new ArrayList<>();
        vodList.add(vod);

        return Result.get().list(vodList).string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 此源直接返回播放链接，无需解析
        return Result.get().parse(false).url(id).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = API_LIST + "?wd=" + key + "&pg=1";
        String content = OkHttp.string(url, getHeaders());

        JSONObject data = new JSONObject(content).getJSONObject("data");
        JSONArray list = data.getJSONArray("list");

        List<Vod> vods = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String vodId = item.getString("vod_id");
            String vodName = item.getString("vod_name");
            String vodPic = item.getString("vod_pic");
            String vodRemarks = item.optString("vod_remarks", "");

            Vod vod = new Vod();
            vod.setVodId(vodId);
            vod.setVodName(vodName);
            vod.setVodPic(vodPic);
            vod.setVodRemarks(vodRemarks);
            vods.add(vod);
        }

        return Result.get().list(vods).string();
    }
}
