package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Pyys extends Spider {

    private String host = "";
    private final List<String> hosts = new ArrayList<>(Arrays.asList(
            "https://www.tjrongze.com",
            "https://www.jiabaide.cn",
            "https://cqzuoer.com"
    ));

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (extend != null && !extend.isEmpty()) {
            JsonObject ext = Json.safeObject(extend);
            if (ext.has("site")) {
                String siteStr = ext.get("site").getAsString();
                hosts.clear();
                hosts.addAll(Arrays.asList(siteStr.split(",")));
            }
        }
        host = hostLate(hosts);
    }

    private String hostLate(List<String> hostList) {
        ExecutorService executor = Executors.newFixedThreadPool(hostList.size());
        Map<String, Long> results = new HashMap<>();
        CountDownLatch latch = new CountDownLatch(hostList.size());

        for (String h : hostList) {
            executor.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    OkHttp.string(h + "/api/mw-movie/anonymous/get/filer/type", getHeaders(null));
                    results.put(h, System.currentTimeMillis() - start);
                } catch (Exception ignored) {
                    results.put(h, Long.MAX_VALUE);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException ignored) {}
        executor.shutdown();

        String bestHost = hosts.get(0);
        long minLatency = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : results.entrySet()) {
            if (entry.getValue() < minLatency) {
                minLatency = entry.getValue();
                bestHost = entry.getKey();
            }
        }
        return bestHost;
    }

    private HashMap<String, String> getHeaders(Map<String, String> param) {
        if (param == null) param = new HashMap<>();
        String t = String.valueOf(System.currentTimeMillis());
        param.put("key", "cb808529bae6b6be45ecfab29a4889bc");
        param.put("t", t);
        String signKey = js(param);
        String md5 = md5(signKey);
        String sign = sha1(md5);
        String deviceId = UUID.randomUUID().toString();

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("sign", sign);
        headers.put("t", t);
        headers.put("deviceid", deviceId);
        return headers;
    }

    private String js(Map<String, String> param) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : param.entrySet()) {
            if (!first) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    private String md5(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(src.getBytes("UTF-8"));
            BigInteger no = new BigInteger(1, digest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) hashtext = "0" + hashtext;
            return hashtext;
        } catch (Exception e) {
            return "";
        }
    }

    private String sha1(String input) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha.digest(input.getBytes("UTF-8"));
            BigInteger no = new BigInteger(1, digest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 40) hashtext = "0" + hashtext;
            return hashtext;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        // 获取分类
        String typeBody = OkHttp.string(host + "/api/mw-movie/anonymous/get/filer/type", getHeaders(null));
        JsonObject cData = JsonParser.parseString(typeBody).getAsJsonObject();

        // 获取筛选
        String filerBody = OkHttp.string(host + "/api/mw-movie/anonymous/v1/get/filer/list", getHeaders(null));
        JsonObject fData = JsonParser.parseString(filerBody).getAsJsonObject();

        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        JsonArray typeArray = cData.getAsJsonArray("data");
        for (JsonElement ele : typeArray) {
            JsonObject obj = ele.getAsJsonObject();
            String typeId = obj.get("typeId").getAsString();
            String typeName = obj.get("typeName").getAsString();
            classes.add(new Class(typeId, typeName));

            List<Filter> filterList = new ArrayList<>();
            JsonArray filerArray = fData.getAsJsonArray("data");
            for (JsonElement fEle : filerArray) {
                JsonObject fObj = fEle.getAsJsonObject();
                if (!fObj.get("typeId").getAsString().equals(typeId)) continue;
                String fKey = fObj.get("filerKey").getAsString();
                String fName = fObj.get("filerName").getAsString();
                List<Filter.Value> values = new ArrayList<>();
                JsonArray vArray = fObj.getAsJsonArray("filerList");
                for (JsonElement vEle : vArray) {
                    JsonObject vObj = vEle.getAsJsonObject();
                    values.add(new Filter.Value(vObj.get("filerName").getAsString(), vObj.get("filerValue").getAsString()));
                }
                filterList.add(new Filter(fKey, fName, values));
            }
            filters.put(typeId, filterList);
        }

        // 首页视频列表：最近更新，不限分类
        HashMap<String, String> params = new HashMap<>();
        params.put("pageNum", "1");
        params.put("pageSize", "24");
        params.put("sort", "2");  // 2 = 最近更新
        String listBody = OkHttp.post(host + "/api/mw-movie/anonymous/v1/get/video/list", js(params), getHeaders(params)).getBody();
        JsonObject listRsp = JsonParser.parseString(listBody).getAsJsonObject();
        List<Vod> list = getVodList(listRsp.getAsJsonArray("data"));

        return Result.string(classes, list, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("pageNum", pg);
        params.put("pageSize", "20");
        params.put("typeId", tid);
        if (extend != null) params.putAll(extend);

        String body = OkHttp.post(host + "/api/mw-movie/anonymous/v1/get/video/list", js(params), getHeaders(params)).getBody();
        JsonObject rsp = JsonParser.parseString(body).getAsJsonObject();

        List<Vod> list = getVodList(rsp.getAsJsonArray("data"));
        int page = Integer.parseInt(pg);
        int limit = 20;
        int total = rsp.has("total") ? rsp.get("total").getAsInt() : 0;
        int pageCount = total > 0 ? (total + limit - 1) / limit : page;

        return Result.get().page(page, pageCount, limit, total).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        HashMap<String, String> params = new HashMap<>();
        params.put("videoId", id);

        String body = OkHttp.post(host + "/api/mw-movie/anonymous/v1/video/detail", js(params), getHeaders(params)).getBody();
        JsonObject rsp = JsonParser.parseString(body).getAsJsonObject();
        JsonObject data = rsp.getAsJsonObject("data");

        Vod vod = new Vod();
        vod.setVodId(data.get("videoId").getAsString());
        vod.setVodName(data.get("videoName").getAsString());
        vod.setVodPic(data.get("videoImg").getAsString());
        vod.setTypeName(data.get("typeName").getAsString());
        vod.setVodYear(data.get("videoYear").getAsString());
        vod.setVodArea(data.get("videoArea").getAsString());
        vod.setVodRemarks(data.get("videoRemarks").getAsString());
        vod.setVodActor(data.get("videoActor").getAsString());
        vod.setVodDirector(data.get("videoDirector").getAsString());
        vod.setVodContent(data.get("videoContent").getAsString());

        LinkedHashMap<String, String> playMap = new LinkedHashMap<>();
        JsonArray lines = data.getAsJsonArray("lineList");
        for (JsonElement lineEle : lines) {
            JsonObject line = lineEle.getAsJsonObject();
            String from = line.get("lineName").getAsString();
            StringBuilder urlBuilder = new StringBuilder();
            JsonArray plays = line.getAsJsonArray("playList");
            for (JsonElement playEle : plays) {
                JsonObject play = playEle.getAsJsonObject();
                String name = play.get("playName").getAsString();
                String playId = play.get("playId").getAsString();
                if (urlBuilder.length() > 0) urlBuilder.append("#");
                urlBuilder.append(name).append("$").append(playId);
            }
            playMap.put(from, urlBuilder.toString());
        }

        vod.setVodPlayFrom(String.join("$$$", playMap.keySet()));
        vod.setVodPlayUrl(String.join("$$$", playMap.values()));

        List<Vod> vods = new ArrayList<>();
        vods.add(vod);
        return Result.string(vods);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("pageNum", pg);
        params.put("pageSize", "20");
        params.put("wd", key);

        String body = OkHttp.post(host + "/api/mw-movie/anonymous/v1/get/video/list", js(params), getHeaders(params)).getBody();
        JsonObject rsp = JsonParser.parseString(body).getAsJsonObject();

        List<Vod> list = getVodList(rsp.getAsJsonArray("data"));
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("playId", id);

        String body = OkHttp.post(host + "/api/mw-movie/anonymous/v1/video/parse/url", js(params), getHeaders(params)).getBody();
        JsonObject rsp = JsonParser.parseString(body).getAsJsonObject();
        JsonObject data = rsp.getAsJsonObject("data");

        String url = data.get("url").getAsString();
        int parse = data.has("parse") ? data.get("parse").getAsInt() : 0;

        return Result.get().url(url).parse(parse).string();
    }

    // 修复：补全缺失的 getVodList 方法
    private List<Vod> getVodList(JsonArray array) {
        List<Vod> list = new ArrayList<>();
        if (array == null) return list;
        for (JsonElement ele : array) {
            JsonObject obj = ele.getAsJsonObject();
            Vod vod = new Vod();
            vod.setVodId(obj.get("videoId").getAsString());
            vod.setVodName(obj.get("videoName").getAsString());
            vod.setVodPic(obj.get("videoImg").getAsString());
            vod.setVodRemarks(obj.has("videoRemarks") ? obj.get("videoRemarks").getAsString() : "");
            list.add(vod);
        }
        return list;
    }
}
