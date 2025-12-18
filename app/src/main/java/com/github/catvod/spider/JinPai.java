package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JinPai extends Spider {

    private String host = "https://www.jiabaide.cn";  // 当前主域名，可在ext中配置多个

    private Map<String, String> getHeaders(Map<String, String> param) {
        long t = System.currentTimeMillis();
        param.put("key", "cb808529bae6b6be45ecfab29a4889bc");
        param.put("t", String.valueOf(t));

        String paramsStr = paramsToStr(param);
        String md5 = md5(paramsStr);
        String sign = sha1(md5);

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.61 Safari/537.36");
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("sign", sign);
        headers.put("t", String.valueOf(t));
        headers.put("deviceid", UUID.randomUUID().toString());
        return headers;
    }

    private String paramsToStr(Map<String, String> param) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : param.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
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
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (extend != null && !extend.isEmpty()) {
            JSONObject extObj = new JSONObject(extend);
            if (extObj.has("site")) {
                String sites = extObj.getString("site");
                // 简单取第一个，或实现延迟测试逻辑
                host = sites.split(",")[0].trim();
            }
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        Map<String, String> param = new HashMap<>();
        String typeUrl = host + "/api/mw-movie/anonymous/get/filer/type";
        String json = OkHttp.string(typeUrl, getHeaders(param));
        JSONObject cdata = new JSONObject(json).getJSONObject("data");

        String listUrl = host + "/api/mw-movie/anonymous/v1/get/filer/list";
        String fjson = OkHttp.string(listUrl, getHeaders(param));
        JSONObject fdata = new JSONObject(fjson).getJSONObject("data");

        List<Class> classes = new ArrayList<>();
        JSONArray typeArray = cdata.names();
        if (typeArray != null) {
            for (int i = 0; i < typeArray.length(); i++) {
                String key = typeArray.getString(i);
                JSONObject obj = cdata.getJSONObject(key);
                classes.add(new Class(obj.getString("typeId"), obj.getString("typeName")));
            }
        }

        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        // 过滤器逻辑类似Python，简化实现（根据fdata构建）
        // 这里省略详细过滤器构建，如需完整可进一步扩展

        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        String url = host + "/api/mw-movie/anonymous/v1/home/all/list";
        Map<String, String> param = new HashMap<>();
        String json = OkHttp.string(url, getHeaders(param));
        JSONObject data = new JSONObject(json).getJSONObject("data");

        List<Vod> vods = new ArrayList<>();
        // 解析data中的list，类似getvod逻辑
        // 省略详细解析，实际需遍历values并添加Vod

        return Result.string(vods);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        Map<String, String> param = new HashMap<>();
        param.put("area", extend.getOrDefault("area", ""));
        param.put("lang", extend.getOrDefault("lang", ""));
        param.put("pageNum", pg);
        param.put("pageSize", "30");
        param.put("sort", extend.getOrDefault("sort", "1"));
        param.put("type", extend.getOrDefault("type", ""));
        param.put("type1", tid);
        param.put("v_class", extend.getOrDefault("v_class", ""));
        param.put("year", extend.getOrDefault("year", ""));

        String url = host + "/api/mw-movie/anonymous/video/list?" + paramsToStr(param);
        String json = OkHttp.string(url, getHeaders(param));
        JSONObject data = new JSONObject(json).getJSONObject("data");

        List<Vod> vods = new ArrayList<>();
        JSONArray list = data.getJSONArray("list");
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            Vod vod = new Vod();
            vod.setVodId(item.optString("id"));
            vod.setVodName(item.optString("name"));
            vod.setVodPic(item.optString("pic"));
            vod.setVodRemarks(item.optString("remark"));
            vods.add(vod);
        }

        return Result.string(vods);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Map<String, String> param = new HashMap<>();
        param.put("id", ids.get(0));
        String url = host + "/api/mw-movie/anonymous/video/detail?id=" + ids.get(0);
        String json = OkHttp.string(url, getHeaders(param));
        JSONObject data = new JSONObject(json).getJSONObject("data");

        Vod vod = new Vod();
        vod.setVodId(data.optString("id"));
        vod.setVodName(data.optString("name"));
        vod.setVodPic(data.optString("pic"));
        // 其他字段类似

        // 播放列表
        JSONArray episodes = data.getJSONArray("episodelist");
        StringBuilder playUrl = new StringBuilder();
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject ep = episodes.getJSONObject(i);
            if (i > 0) playUrl.append("#");
            playUrl.append(ep.optString("name")).append("$").append(ids.get(0)).append("@@").append(ep.optString("nid"));
        }
        vod.setVodPlayFrom("金牌");
        vod.setVodPlayUrl(playUrl.toString());

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        Map<String, String> param = new HashMap<>();
        param.put("keyword", key);
        param.put("pageNum", "1");
        param.put("pageSize", "20");

        String url = host + "/api/mw-movie/anonymous/video/searchByWord?" + paramsToStr(param);
        String json = OkHttp.string(url, getHeaders(param));
        JSONObject data = new JSONObject(json).getJSONObject("data").getJSONObject("result");

        List<Vod> vods = new ArrayList<>();
        JSONArray list = data.getJSONArray("list");
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            Vod vod = new Vod();
            vod.setVodId(item.optString("id"));
            vod.setVodName(item.optString("name"));
            vod.setVodPic(item.optString("pic"));
            vods.add(vod);
        }

        return Result.string(vods);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] parts = id.split("@@");
        Map<String, String> param = new HashMap<>();
        param.put("clientType", "1");
        param.put("id", parts[0]);
        param.put("nid", parts[1]);

        String url = host + "/api/mw-movie/anonymous/v2/video/episode/url?clientType=1&id=" + parts[0] + "&nid=" + parts[1];
        String json = OkHttp.string(url, getHeaders(param));
        JSONObject data = new JSONObject(json).getJSONObject("data");

        // 返回多分辨率或默认第一个
        JSONArray list = data.getJSONArray("list");
        if (list.length() > 0) {
            JSONObject first = list.getJSONObject(0);
            return Result.get().url(first.optString("url")).header(getHeaders(param)).string();
        }
        return Result.get().url("").string();
    }
}
