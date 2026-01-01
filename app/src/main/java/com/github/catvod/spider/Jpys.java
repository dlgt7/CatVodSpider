package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class Jpys extends Spider {

    private String host;
    private final String KEY = "cb808529bae6b6be45ecfab29a4889bc";

    @Override
    public void init(Context context, String extend) {
        try {
            // 修复点：显式捕获 super.init 的异常
            super.init(context, extend);
            String hosts = "https://www.jiabaide.cn,https://cqzuoer.com";
            if (!TextUtils.isEmpty(extend) && extend.trim().startsWith("{")) {
                JSONObject ext = new JSONObject(extend);
                if (ext.has("site")) hosts = ext.getString("site");
            }
            this.host = getBestHost(hosts);
        } catch (Exception e) {
            SpiderDebug.log(e);
            this.host = "https://www.jiabaide.cn";
        }
    }

    private String getBestHost(String hosts) {
        String[] urls = hosts.split(",");
        if (urls.length == 1) return urls[0].trim();
        String best = urls[0].trim();
        long minTime = Long.MAX_VALUE;
        for (String url : urls) {
            long start = System.currentTimeMillis();
            try {
                OkHttp.newCall(url.trim(), "tag").close();
                long delay = System.currentTimeMillis() - start;
                if (delay < minTime) {
                    minTime = delay;
                    best = url.trim();
                }
            } catch (Exception ignored) {}
        }
        return best;
    }

    private Map<String, String> getHeaders(Map<String, String> params) {
        if (params == null) params = new TreeMap<>();
        String t = String.valueOf(System.currentTimeMillis());
        params.put("key", KEY);
        params.put("t", t);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }

        String sign = sha1(md5(sb.toString()));
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        headers.put("sign", sign);
        headers.put("t", t);
        headers.put("deviceid", UUID.randomUUID().toString());
        return headers;
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            String typeUrl = host + "/api/mw-movie/anonymous/get/filer/type";
            String listUrl = host + "/api/mw-movie/anonymous/v1/get/filer/list";

            JSONObject cdata = new JSONObject(OkHttp.string(typeUrl, getHeaders(new TreeMap<>())));
            JSONObject fdata = new JSONObject(OkHttp.string(listUrl, getHeaders(new TreeMap<>())));

            List<Class> classes = new ArrayList<>();
            JSONArray types = cdata.getJSONArray("data");
            for (int i = 0; i < types.length(); i++) {
                JSONObject obj = types.getJSONObject(i);
                classes.add(new Class(obj.getString("typeId"), obj.getString("typeName")));
            }

            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
            JSONObject filterData = fdata.getJSONObject("data");
            Iterator<String> keys = filterData.keys();
            while (keys.hasNext()) {
                String tid = keys.next();
                JSONObject d = filterData.getJSONObject(tid);
                List<Filter> fList = new ArrayList<>();
                fList.add(new Filter("type", "类型", getFilterValues(d.getJSONArray("typeList"), "itemText", "itemValue")));
                fList.add(new Filter("area", "地区", getFilterValues(d.getJSONArray("districtList"), "itemText", "itemText")));
                fList.add(new Filter("year", "年份", getFilterValues(d.getJSONArray("yearList"), "itemText", "itemText")));
                filters.put(tid, fList);
            }
            // 优化点：实例化 Result，补上空 vod 列表，符合最新 Result.java
            return new Result().classes(classes).filters(filters).vod(new ArrayList<>()).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            Map<String, String> params = new TreeMap<>();
            params.put("pageNum", pg);
            params.put("pageSize", "30");
            params.put("type1", tid);
            // 优化点：直接合并参数
            params.putAll(extend);

            StringBuilder url = new StringBuilder(host + "/api/mw-movie/anonymous/video/list?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                url.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }

            String content = OkHttp.string(url.toString(), getHeaders(params));
            JSONObject data = new JSONObject(content).getJSONObject("data");
            return new Result().vod(parseVods(data.getJSONArray("list"))).page(Integer.parseInt(pg), 0, 30, data.getInt("total")).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            Map<String, String> params = new TreeMap<>();
            params.put("id", id);
            
            String content = OkHttp.string(host + "/api/mw-movie/anonymous/video/detail?id=" + id, getHeaders(params));
            JSONObject data = new JSONObject(content).getJSONObject("data");
            
            Vod vod = new Vod();
            vod.setVodId(data.optString("id"));
            vod.setVodName(data.optString("vodName"));
            vod.setVodPic(data.optString("vodPic"));
            vod.setTypeName(data.optString("typeName"));
            vod.setVodYear(data.optString("vodYear"));
            vod.setVodArea(data.optString("vodArea"));
            vod.setVodActor(data.optString("vodActor"));
            vod.setVodDirector(data.optString("vodDirector"));
            vod.setVodContent(data.optString("vodContent"));
            vod.setVodRemarks(data.optString("vodRemarks"));
            
            JSONArray episodes = data.getJSONArray("episodelist");
            List<String> playUrls = new ArrayList<>();
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject ep = episodes.getJSONObject(i);
                playUrls.add(ep.getString("name") + "$" + id + "@@" + ep.getString("nid"));
            }
            vod.setVodPlayFrom("金牌影院");
            vod.setVodPlayUrl(TextUtils.join("#", playUrls));

            return new Result().vod(Arrays.asList(vod)).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String[] ids = id.split("@@");
            Map<String, String> params = new TreeMap<>();
            params.put("clientType", "1");
            params.put("id", ids[0]);
            params.put("nid", ids[1]);

            String url = host + "/api/mw-movie/anonymous/v2/video/episode/url?clientType=1&id=" + ids[0] + "&nid=" + ids[1];
            String content = OkHttp.string(url, getHeaders(params));
            JSONObject data = new JSONObject(content).getJSONObject("data");
            String playUrl = data.getJSONArray("list").getJSONObject(0).getString("url");
            
            // 优化点：parse(0) 明确直链
            return new Result().url(playUrl).header(getHeaders(null)).parse(0).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            Map<String, String> params = new TreeMap<>();
            params.put("keyword", key);
            params.put("pageNum", "1");
            params.put("pageSize", "30");
            params.put("sourceCode", "1");

            String url = host + "/api/mw-movie/anonymous/video/searchByWord?keyword=" + key + "&pageNum=1&pageSize=30&sourceCode=1";
            String content = OkHttp.string(url, getHeaders(params));
            JSONArray list = new JSONObject(content).getJSONObject("data").getJSONObject("result").getJSONArray("list");
            return new Result().vod(parseVods(list)).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    private List<Vod> parseVods(JSONArray array) throws Exception {
        List<Vod> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            Vod vod = new Vod();
            vod.setVodId(obj.optString("id"));
            vod.setVodName(obj.optString("vodName"));
            vod.setVodPic(obj.optString("vodPic"));
            vod.setVodRemarks(obj.optString("vodRemarks"));
            list.add(vod);
        }
        return list;
    }

    private List<Filter.Value> getFilterValues(JSONArray array, String nKey, String vKey) throws Exception {
        List<Filter.Value> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            values.add(new Filter.Value(obj.getString(nKey), obj.getString(vKey)));
        }
        return values;
    }

    private String md5(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(src.getBytes());
            return String.format("%032x", new BigInteger(1, bytes));
        } catch (Exception e) { return ""; }
    }

    private String sha1(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(src.getBytes());
            return String.format("%040x", new BigInteger(1, bytes));
        } catch (Exception e) { return ""; }
    }
}
