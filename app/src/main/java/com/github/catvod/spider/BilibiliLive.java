package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BilibiliLive extends Spider {

    private final ArrayList<Area> areas = new ArrayList<>();
    private boolean areasLoaded;

    private ArrayList<Vod> a(Area area, int page) {
        String url = "https://api.live.bilibili.com/room/v1/Area/getRoomList?platform=web&parent_area_id=" + area.parentId + "&area_id=" + area.id + "&page=" + page;
        String json = OkHttp.string(url, c());
        ArrayList<Vod> list = new ArrayList<>();
        JSONObject obj = new JSONObject(json);
        Object data = obj.opt("data");
        JSONArray rooms;
        if (data instanceof JSONArray) {
            rooms = (JSONArray) data;
        } else if (data instanceof JSONObject) {
            rooms = ((JSONObject) data).optJSONArray("list");
        } else {
            rooms = null;
        }
        if (rooms == null) return list;
        for (int i = 0; i < rooms.length(); i++) {
            JSONObject room = rooms.optJSONObject(i);
            if (room == null) continue;
            String roomid = String.valueOf(room.optInt("roomid"));
            if ("0".equals(roomid)) {
                roomid = room.optString("roomid", "");
            }
            if (roomid.isEmpty()) continue;
            String title = room.optString("title", room.optString("uname", "直播"));
            String cover = e(room.optString("cover", room.optString("user_cover", "")));
            String uname = room.optString("uname", "");
            list.add(new Vod(roomid, title, cover, uname));
        }
        return list;
    }

    private ArrayList<Vod> b(int page) {
        d();
        for (Area area : areas) {
            if (area.name.contains("娱乐") || area.name.contains("网游") || area.name.contains("游戏")) {
                ArrayList<Vod> list = a(area, page);
                if (!list.isEmpty()) return list;
            }
        }
        if (areas.isEmpty()) return new ArrayList<>();
        return a(areas.get(0), page);
    }

    private Map<String, String> c() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0");
        headers.put("Referer", "https://live.bilibili.com/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/json,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        return headers;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return i(tid, pg);
    }

    private void d() {
        if (areasLoaded) return;
        String url = "https://api.live.bilibili.com/room/v1/Area/getList";
        String json = OkHttp.string(url, c());
        JSONArray data = new JSONObject(json).optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject group = data.optJSONObject(i);
                if (group == null) continue;
                JSONArray list = group.optJSONArray("list");
                if (list == null) continue;
                for (int j = 0; j < list.length(); j++) {
                    JSONObject area = list.optJSONObject(j);
                    if (area == null) continue;
                    int parentId = area.optInt("parent_id");
                    int id = area.optInt("id");
                    String name = area.optString("name", "");
                    areas.add(new Area(parentId, id, name));
                }
            }
        }
        areasLoaded = true;
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String id = ids.get(0);
            String url = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?room_id=" + id + "&platform=web&protocol=0,1&format=0,1,2&codec=0,1";
            JSONObject data = new JSONObject(OkHttp.string(url, c())).optJSONObject("data");
            if (data == null) return Result.error("无 data");
            JSONObject playurlInfo = data.optJSONObject("playurl_info");
            if (playurlInfo == null) return Result.error("无 playurl_info");
            JSONObject playurl = playurlInfo.optJSONObject("playurl");
            if (playurl == null) return Result.error("无 playurl");
            JSONArray stream = playurl.optJSONArray("stream");
            if (stream == null || stream.length() == 0) return Result.error("无 stream");
            ArrayList<String> list = new ArrayList<>();
            for (int i = 0; i < stream.length(); i++) {
                JSONObject streamObj = stream.optJSONObject(i);
                if (streamObj == null) continue;
                JSONArray format = streamObj.optJSONArray("format");
                if (format == null) continue;
                for (int j = 0; j < format.length(); j++) {
                    JSONObject formatObj = format.optJSONObject(j);
                    if (formatObj == null) continue;
                    JSONArray codec = formatObj.optJSONArray("codec");
                    if (codec == null) continue;
                    for (int k = 0; k < codec.length(); k++) {
                        JSONObject codecObj = codec.optJSONObject(k);
                        if (codecObj == null) continue;
                        JSONArray urlInfo = codecObj.optJSONArray("url_info");
                        if (urlInfo == null || urlInfo.length() < 2) continue;
                        JSONObject urlInfoObj = urlInfo.optJSONObject(1);
                        if (urlInfoObj == null) continue;
                        String host = urlInfoObj.optString("host", "");
                        String baseUrl = codecObj.optString("base_url", "");
                        String extra = urlInfoObj.optString("extra", "");
                        if (host.isEmpty() || baseUrl.isEmpty() || extra.isEmpty()) continue;
                        list.add((list.size() + 1) + "$" + host + baseUrl + extra);
                    }
                }
            }
            if (list.isEmpty()) return Result.error("未解析到播放地址");
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName("B站直播间");
            vod.setVodPic("https://i0.hdslb.com/bfs/live/newcover/" + id + ".jpg");
            vod.setVodPlayFrom("B站直播");
            vod.setVodPlayUrl(TextUtils.join("#", list));
            return Result.string(vod);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private String e(String url) {
        if (TextUtils.isEmpty(url)) return url;
        if (url.startsWith("http")) return url;
        return "https:" + url;
    }

    private Vod f(Element parent, Element link) {
        if (link == null) return null;
        String href = link.attr("href");
        String id;
        if (TextUtils.isEmpty(href)) {
            id = "";
        } else {
            int idx = href.indexOf("bilibili.com/");
            if (idx < 0) {
                id = "";
            } else {
                int start = idx + 13;
                int end = href.indexOf('?', start);
                if (end < 0) end = href.length();
                id = href.substring(start, end).replace("/", "").trim();
            }
        }
        if (id.isEmpty()) return null;
        String name = link.text().trim().replace("的直播间", "").replace("直播中", "").trim();
        String pic = "";
        if (parent != null) {
            Element img = parent.selectFirst("img");
            if (img != null) pic = e(img.attr("src"));
        }
        String uname = "";
        if (parent != null) {
            Element unameEl = parent.selectFirst("a.bili-live-card__info--uname");
            if (unameEl != null) uname = unameEl.text().trim();
        }
        return new Vod(id, name, pic, uname);
    }

    private ArrayList<Vod> g(String html) {
        ArrayList<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("div.video-list-item");
        for (Element item : items) {
            Element link = item.selectFirst("h3.bili-live-card__info--tit a");
            Vod vod = f(item, link);
            if (vod != null) list.add(vod);
        }
        if (list.isEmpty()) {
            Elements links = doc.select("h3.bili-live-card__info--tit a");
            for (Element link : links) {
                Element parent = link.closest("div.bili-live-card__info");
                if (parent == null) parent = link.parent();
                Vod vod = f(parent, link);
                if (vod != null) list.add(vod);
            }
        }
        return list;
    }

    private ArrayList<Vod> h(int page, String keyword) {
        d();
        if (areas.isEmpty()) return new ArrayList<>();
        keyword = keyword.trim();
        ArrayList<Vod> list = new ArrayList<>();
        if (keyword.isEmpty()) {
            int limit = Math.min(3, areas.size());
            for (int i = 0; i < limit; i++) {
                list.addAll(a(areas.get(i), page));
            }
            return list;
        }
        for (Area area : areas) {
            if (area.name.contains(keyword) || keyword.contains(area.name)) {
                list.addAll(a(area, page));
                if (list.size() >= 40) break;
            }
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        String[] names = {"全部", "网游", "娱乐", "电台", "虚拟", "生活", "知识", "科技", "聊天", "手工", "舞蹈", "美食"};
        for (String name : names) {
            String typeId = "全部".equals(name) ? "" : name;
            classes.add(new Class(typeId, name));
        }
        return Result.string(classes, new ArrayList<>());
    }

    private String i(String key, String pg) {
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }
        try {
            if (key == null) key = "";
            String url = "https://search.bilibili.com/live?keyword=" + URLEncoder.encode(key, "UTF-8") + "&page=" + page;
            ArrayList<Vod> list = g(OkHttp.string(url, c()));
            if (list.isEmpty()) list = h(page, key);
            if (list.isEmpty()) list = b(page);
            return Result.get().page(page, page + 1, 90, 999999).vod(list).string();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Override
    public void init(Context context, String extend) throws Exception {
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return Result.get().url(id).header(c()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return i(key, "1");
    }

    private static class Area {
        final int parentId;
        final int id;
        final String name;

        Area(int parentId, int id, String name) {
            this.parentId = parentId;
            this.id = id;
            this.name = name;
        }
    }
}
