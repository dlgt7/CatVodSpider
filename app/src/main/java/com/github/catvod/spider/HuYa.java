package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HuYa extends Spider {

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        return headers;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = "https://live.cdn.huya.com/liveHttpUI/getLiveList?iGid=" + tid + "&iPageNo=" + pg + "&iPageSize=120";
        String json = OkHttp.string(url, getHeaders());
        JSONObject obj = new JSONObject(json);
        JSONArray list = obj.getJSONArray("vList");

        List<Vod> vods = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String rid = item.getString("lProfileRoom");
            String title = item.optString("sIntroduction", "");
            String pic = item.optString("sScreenshot", "");
            if (pic.startsWith("//")) pic = "https:" + pic;
            else if (!pic.startsWith("http")) pic = "https:" + pic;
            String remark = item.optString("sNick", "");
            vods.add(new Vod(rid, title, pic, remark));
        }
        return Result.string(vods);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPlayFrom("虎牙");
        vod.setVodPlayUrl("直播$" + ids.get(0));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 可替换为更稳定的代理线路
        String playUrl = "https://www.goodiptv.club/huya/" + id;
        return Result.get().url(playUrl).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = "https://search.cdn.huya.com/?m=Search&do=getSearchContent&q=" + key + "&typ=-5&rows=40";
        String json = OkHttp.string(url, getHeaders());
        JSONObject obj = new JSONObject(json);
        JSONArray docs = obj.getJSONObject("response").getJSONArray("3").getJSONObject("docs");

        List<Vod> vods = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject item = docs.getJSONObject(i);
            String rid = item.optString("room_id", "");
            String title = item.optString("game_nick", "");
            String pic = item.optString("game_screenshot", "");
            if (pic.startsWith("//")) pic = "https:" + pic;
            String remark = item.optString("gameName", "");
            if (!rid.isEmpty()) {
                vods.add(new Vod(rid, title, pic, remark));
            }
        }
        return Result.string(vods);
    }
}
