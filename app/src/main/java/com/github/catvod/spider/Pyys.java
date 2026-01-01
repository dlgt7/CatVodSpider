package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Util;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigInteger;
import java.net.URLEncoder;
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
import java.util.concurrent.TimeUnit;

public class Pyys extends Spider {

    private String host = "";
    private final List<String> hosts = Arrays.asList("https://www.tjrongze.com", "https://www.jiabaide.cn", "https://cqzuoer.com");

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        JsonObject ext = Json.parse(extend).getAsJsonObject();
        if (ext.has("site")) {
            hosts.clear();
            hosts.addAll(Arrays.asList(ext.get("site").getAsString().split(",")));
        }
        host = hostLate(hosts);
    }

    private String hostLate(List<String> hosts) {
        ExecutorService executor = Executors.newFixedThreadPool(hosts.size());
        Map<String, Long> results = new HashMap<>();
        CountDownLatch latch = new CountDownLatch(hosts.size());
        for (String h : hosts) {
            executor.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    OkHttp.string(h + "/api/mw-movie/anonymous/get/filer/type", getHeaders(null));
                    long latency = System.currentTimeMillis() - start;
                    results.put(h, latency);
                } catch (Exception ignored) {
                    results.put(h, Long.MAX_VALUE);
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        executor.shutdown();
        String minHost = "";
        long minLatency = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : results.entrySet()) {
            if (entry.getValue() < minLatency) {
                minLatency = entry.getValue();
                minHost = entry.getKey();
            }
        }
        return minHost;
    }

    private HashMap<String, String> getHeaders(Map<String, String> param) {
        if (param == null) param = new HashMap<>();
        String t = String.valueOf(System.currentTimeMillis());
        param.put("key", "cb808529bae6b6be45ecfab29a4889bc");
        param.put("t", t);
        String signKey = js(param);
        String md5 = Crypto.md5(signKey);
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
        for (Map.Entry<String, String> entry : param.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private String sha1(String input) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            byte[] messageDigest = sha.digest(input.getBytes());
            BigInteger no = new BigInteger(1, messageDigest);
            String hashText = no.toString(16);
            while (hashText.length() < 40) {
                hashText = "0" + hashText;
            }
            return hashText;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JsonObject cData = JsonParser.parseString(OkHttp.string(host + "/api/mw-movie/anonymous/get/filer/type", getHeaders(null))).getAsJsonObject();
        JsonObject fData = JsonParser.parseString(OkHttp.string(host + "/api/mw-movie/anonymous/v1/get/filer/list", getHeaders(null))).getAsJsonObject();
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        JsonArray dataArray = cData.getAsJsonArray("data");
        for (JsonElement ele : dataArray) {
            JsonObject obj = ele.getAsJsonObject();
            String typeId = obj.get("typeId").getAsString();
            String typeName = obj.get("typeName").getAsString();
            classes.add(new Class(typeId, typeName));
            List<Filter> filterList = new ArrayList<>();
            JsonArray fArray = fData.getAsJsonArray("data");
            for (JsonElement fEle : fArray) {
                JsonObject fObj = fEle.getAsJsonObject();
                if (!fObj.get("typeId").getAsString().equals(typeId)) continue;
                String fName = fObj.get("filerName").getAsString();
                String fKey = fObj.get("filerKey").getAsString();
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
        List<Vod> list = new ArrayList<>();
        HashMap<String, String> params = new HashMap<>();
        params.put("pageNum", "1");
        params.put("pageSize", "20");
        params.put("sort", "2");
        params.put("typeId", "1");
        JsonObject homeData = JsonParser.parseString(OkHttp.post(host + "/api/mw-movie/anonymous/v1/get/video/list", js(params), getHeaders(params))).getAsJsonObject();
        list = getVodList(homeData.getAsJsonArray("data"));
        return Result.string(classes, list, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("pageNum", pg);
        params.put("pageSize", "20");
        params.put("typeId", tid);
        if (extend != null) {
            for (Map.Entry<String, String> entry : extend.entrySet()) {
                params.put(entry.getKey(), entry.getValue());
            }
        }
        JsonObject rsp = JsonParser.parseString(OkHttp.post(host + "/api/mw-movie/anonymous/v1/get/video/list", js(params), getHeaders(params))).getAsJsonObject();
        List<Vod> list = getVodList(rsp.getAsJsonArray("data"));
        int page = Integer.parseInt(pg);
        int pageCount = rsp.get("total").getAsInt() > 20 ? page + 1 : page;
        int limit = 20;
        int total = rsp.get("total").getAsInt();
        return Result.page(page, pageCount, limit, total).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        HashMap<String, String> params = new HashMap<>();
        params.put("videoId", id);
        JsonObject rsp = JsonParser.parseString(OkHttp.post(host + "/api/mw-movie/anonymous/v1/video/detail", js(params), getHeaders(params))).getAsJsonObject();
        JsonObject data = rsp.getAsJsonObject("data");
        String vodId = data.get("videoId").getAsString();
        String vodName = data.get("videoName").getAsString();
        String vodPic = data.get("videoImg").getAsString();
        String typeName = data.get("typeName").getAsString();
        String vodYear = data.get("videoYear").getAsString();
        String vodArea = data.get("videoArea").getAsString();
        String vodRemarks = data.get("videoRemarks").getAsString();
        String vodActor = data.get("videoActor").getAsString();
        String vodDirector = data.get("videoDirector").getAsString();
        String vodContent = data.get("videoContent").getAsString();
        Vod vod = new Vod(vodId, vodName, vodPic);
        vod.setTypeName(typeName);
        vod.setVodYear(vodYear);
        vod.setVodArea(vodArea);
        vod.setVodRemarks(vodRemarks);
        vod.setVodActor(vodActor);
        vod.setVodDirector(vodDirector);
        vod.setVodContent(vodContent);
        Map<String, String> sites = new LinkedHashMap<>();
        JsonArray lines = data.getAsJsonArray("lineList");
        for (JsonElement lineEle : lines) {
            JsonObject line = lineEle.getAsJsonObject();
            String from = line.get("lineName").getAsString();
            StringBuilder sb = new StringBuilder();
            JsonArray plays = line.getAsJsonArray("playList");
            for (JsonElement playEle : plays) {
                JsonObject play = playEle.getAsJsonObject();
                String name = play.get("playName").getAsString();
                String playId = play.get("playId").getAsString();
                if (sb.length() > 0) sb.append("#");
                sb.append(name).append("$").append(playId);
            }
            sites.put(from, sb.toString());
        }
        vod.setVodPlayFrom(String.join("$$$", sites.keySet()));
        vod.setVodPlayUrl(String.join("$$$", sites.values()));
        return Result.string(vod);
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
        JsonObject rsp = JsonParser.parseString(OkHttp.post(host + "/api/mw-movie/anonymous/v1/get/video/list", js(params), getHeaders(params))).getAsJsonObject();
        List<Vod> list = getVodList(rsp.getAsJsonArray("data"));
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("playId", id);
        JsonObject rsp = JsonParser.parseString(OkHttp.post(host + "/api/mw-movie/anonymous/v1/video/parse/url", js(params), getHeaders(params))).getAsJsonObject();
        JsonObject data = rsp.getAsJsonObject("data");
        String url = data.get("url").getAsString();
        int parse = data.has("parse") ? data.get("parse").getAsInt() : 0;
        return Result.get().url(url).parse(parse).string();
    }

    private List<Vod> getVodList(JsonArray array) {
        List<Vod> list = new ArrayList<>();
        for (JsonElement ele : array) {
            JsonObject obj = ele.getAsJsonObject();
            String vodId = obj.get("videoId").getAsString();
            String vodName = obj.get("videoName").getAsString();
            String vodPic = obj.get("videoImg").getAsString();
            String vodRemarks = obj.get("videoRemarks").getAsString();
            list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
        }
        return list;
    }
}
