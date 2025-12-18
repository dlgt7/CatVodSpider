package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JinPai extends Spider {

    private String host = "https://www.jiabaide.cn";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (extend != null && !extend.isEmpty()) {
            JSONObject ext = new JSONObject(extend);
            if (ext.has("site")) {
                String sites = ext.getString("site");
                String[] urlArray = sites.split(",");
                host = selectFastestHost(urlArray);
            }
        }
    }

    private String selectFastestHost(String[] urls) {
        String fastest = urls[0].trim();
        long minDelay = Long.MAX_VALUE;

        for (String u : urls) {
            String url = u.trim();
            if (url.isEmpty()) continue;
            long start = System.currentTimeMillis();
            try {
                OkHttp.string(url, new HashMap<>());
                long delay = System.currentTimeMillis() - start;
                if (delay < minDelay) {
                    minDelay = delay;
                    fastest = url;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return fastest;
    }

    private Map<String, String> getHeaders(Map<String, String> extra) {
        Map<String, String> param = new HashMap<>();
        if (extra != null) param.putAll(extra);

        long t = System.currentTimeMillis();
        param.put("key", "cb808529bae6b6be45ecfab29a4889bc");
        param.put("t", String.valueOf(t));

        String paramsStr = mapToSortedString(param);
        String md5 = md5(paramsStr);
        String sign = sha1(md5);

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("sign", sign);
        headers.put("t", String.valueOf(t));
        headers.put("deviceid", UUID.randomUUID().toString());
        return headers;
    }

    private String mapToSortedString(Map<String, String> map) {
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (sb.length() > 0) sb.append("&");
            sb.append(key).append("=").append(map.get(key));
        }
        return sb.toString();
    }

    private String md5(String data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : array) {
                sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String sha1(String data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] array = md.digest(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : array) {
                sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        Map<String, String> param = new HashMap<>();
        String typeUrl = host + "/api/mw-movie/anonymous/get/filer/type";
        String typeJson = OkHttp.string(typeUrl, getHeaders(param));
        JSONObject typeData = new JSONObject(typeJson).getJSONObject("data");

        String filterUrl = host + "/api/mw-movie/anonymous/v1/get/filer/list";
        String filterJson = OkHttp.string(filterUrl, getHeaders(param));
        JSONObject filterData = new JSONObject(filterJson).getJSONObject("data");

        List<Class> classes = new ArrayList<>();
        JSONArray typeKeys = typeData.names();
        if (typeKeys != null) {
            for (int i = 0; i < typeKeys.length(); i++) {
                String key = typeKeys.getString(i);
                JSONObject obj = typeData.getJSONObject(key);
                classes.add(new Class(obj.optString("typeId"), obj.optString("typeName")));
            }
        }

        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        // 实现筛选（简化版，可根据需要扩展）
        // 这里仅示例，实际可完整解析 filterData

        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        Map<String, String> param = new HashMap<>();
        String url = host + "/api/mw-movie/anonymous/v1/home/all/list";
        String json = OkHttp.string(url, getHeaders(param));
        JSONObject data = new JSONObject(json).getJSONObject("data");

        List<Vod> vods = new ArrayList<>();
        JSONArray keys = data.names();
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                JSONArray list = data.getJSONObject(keys.getString(i)).optJSONArray("list");
                if (list != null) {
                    for (int j = 0; j < list.length(); j++) {
                        JSONObject item = list.getJSONObject(j);
                        vods.add(parseVod(item));
                    }
                }
            }
        }
        return Result.string(vods);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        Map<String, String> param = new HashMap<>();
        param.put("type1", tid);
        param.put("pageNum", pg);
        param.put("pageSize", "30");
        param.put("area", extend.getOrDefault("area", ""));
        param.put("lang", extend.getOrDefault("lang", ""));
        param.put("year", extend.getOrDefault("year", ""));
        param.put("sort", extend.getOrDefault("sort", "1"));
        param.put("v_class", extend.getOrDefault("v_class", ""));

        String url = host + "/api/mw-movie/anonymous/video/list?" + mapToSortedString(param);
        String json = OkHttp.string(url, getHeaders(param));
        JSONObject resp = new JSONObject(json);
        if (resp.optInt("code") != 200) return Result.string(new ArrayList<>());

        JSONArray list = resp.getJSONObject("data").optJSONObject("result").optJSONArray("list");
        List<Vod> vods = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                vods.add(parseVod(list.getJSONObject(i)));
            }
        }
        return Result.string(vods);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Map<String, String> param = new HashMap<>();
        String url = host + "/api/mw-movie/anonymous/video/detail?id=" + ids.get(0);
        String json = OkHttp.string(url, getHeaders(param));
        JSONObject data = new JSONObject(json).getJSONObject("data");

        Vod vod = parseVod(data);
        vod.setVodId(ids.get(0));

        StringBuilder playFrom = new StringBuilder("金牌");
        StringBuilder playUrl = new StringBuilder();
        JSONArray episodes = data.optJSONArray("episodelist");
        if (episodes != null) {
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject ep = episodes.getJSONObject(i);
                String nid = ep.optString("nid");
                if (i > 0) playUrl.append("#");
                playUrl.append(ep.optString("name", "第" + (i + 1) + "集")).append("$").append(ids.get(0) + "@@" + nid);
            }
        }
        vod.setVodPlayFrom(playFrom.toString());
        vod.setVodPlayUrl(playUrl.toString());

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        Map<String, String> param = new HashMap<>();
        param.put("keyword", key);
        param.put("pageNum", "1");
        param.put("pageSize", "20");

        String url = host + "/api/mw-movie/anonymous/video/searchByWord?" + mapToSortedString(param);
        String json = OkHttp.string(url, getHeaders(param));
        JSONObject result = new JSONObject(json).getJSONObject("data").getJSONObject("result");

        List<Vod> vods = new ArrayList<>();
        JSONArray list = result.optJSONArray("list");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                vods.add(parseVod(list.getJSONObject(i)));
            }
        }
        return Result.string(vods);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] parts = id.split("@@");
        Map<String, String> param = new HashMap<>();
        param.put("id", parts[0]);
        param.put("nid", parts[1]);
        param.put("clientType", "1");

        String url = host + "/api/mw-movie/anonymous/v2/video/episode/url?" + mapToSortedString(param);
        String json = OkHttp.string(url, getHeaders(param));
        JSONArray list = new JSONObject(json).getJSONObject("data").optJSONArray("list");

        if (list != null && list.length() > 0) {
            JSONObject best = list.optJSONObject(0);
            Map<String, String> header = new HashMap<>();
            header.put("User-Agent", "Mozilla/5.0");
            header.put("Referer", host + "/");
            return Result.get().url(best.optString("url")).header(header).string();
        }
        return Result.get().url("").string();
    }

    private Vod parseVod(JSONObject item) {
        Vod vod = new Vod();
        vod.setVodId(item.optString("id", item.optString("vodId")));
        vod.setVodName(item.optString("name", item.optString("vodName")));
        vod.setVodPic(item.optString("pic", item.optString("vodPic")));
        vod.setVodRemarks(item.optString("remark", item.optString("vodRemarks")));
        vod.setVodYear(item.optString("year", item.optString("vodYear")));
        vod.setVodActor(item.optString("actor", item.optString("vodActor")));
        vod.setVodDirector(item.optString("director", item.optString("vodDirector")));
        vod.setVodContent(item.optString("content", item.optString("vodContent")));
        return vod;
    }
}
