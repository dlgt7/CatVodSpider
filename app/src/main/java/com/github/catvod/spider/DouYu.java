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

public class DouYu extends Spider {

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; Redmi K30) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        return headers;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = "https://m.douyu.com/api/room/list?page=" + pg + "&type=" + tid;
        String json = OkHttp.string(url, getHeaders());
        JSONObject obj = new JSONObject(json);
        JSONArray list = obj.getJSONObject("data").getJSONArray("list");

        List<Vod> vods = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String rid = item.getString("rid");
            String title = item.optString("roomName", "");
            String pic = item.optString("roomSrc", "");
            if (!pic.startsWith("http")) pic = "https:" + pic;
            String remark = item.optString("nickname", "");
            vods.add(new Vod(rid, title, pic, remark));
        }
        return Result.string(vods);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPlayFrom("斗鱼");
        vod.setVodPlayUrl("直播$" + ids.get(0));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = "https://live.iill.top/douyu/" + id;
        return Result.get().url(playUrl).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        Map<String, String> postHeaders = new HashMap<>(getHeaders());
        postHeaders.put("Content-Type", "application/json");

        String postData = "{\"sk\":\"" + key + "\",\"offset\":0,\"limit\":20,\"did\":\"bf1f5579c0b2f6066af0bee200051531\"}";
        String json = OkHttp.post("https://m.douyu.com/api/search/liveRoom", postData, postHeaders).getBody();  // 修复：加 .getBody()

        JSONObject obj = new JSONObject(json);
        JSONArray list = obj.getJSONObject("data").getJSONArray("list");

        List<Vod> vods = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String rid = item.optString("roomId", "");
            String title = item.optString("roomName", "");
            String pic = item.optString("roomSrc", "");
            if (!pic.startsWith("http")) pic = "https:" + pic;
            String remark = item.optString("nickname", "");
            if (!rid.isEmpty()) {
                vods.add(new Vod(rid, title, pic, remark));
            }
        }
        return Result.string(vods);
    }
}
