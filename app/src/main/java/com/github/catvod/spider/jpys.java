package com.github.catvod.spider;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 金牌影视爬虫实现
 * 对应原Python版本: 金牌.py
 */
public class jpys extends Spider {

    private String host = "";
    private List<String> hostList = new ArrayList<>();

    public void init(String extend) {
        init(null, extend); // 调用父类方法
    }
    
    @Override
    public void init(android.content.Context context, String extend) {
        if (!TextUtils.isEmpty(extend)) {
            try {
                JsonObject extObj = Json.parse(extend).getAsJsonObject();
                String sites = extObj.has("site") ? extObj.get("site").getAsString() : "";
                if (!TextUtils.isEmpty(sites)) {
                    String[] hosts = sites.split(",");
                    for (String h : hosts) {
                        hostList.add(h.trim());
                    }
                    host = selectBestHost(hostList);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            // 获取分类数据
            String categoryUrl = host + "/api/mw-movie/anonymous/get/filer/type";
            String categoryJson = OkHttp.string(categoryUrl, getHeaders(null));
            JsonObject categoryData = Json.parse(categoryJson).getAsJsonObject();

            // 获取筛选数据
            String filterUrl = host + "/api/mw-movie/anonymous/v1/get/filer/list";
            String filterJson = OkHttp.string(filterUrl, getHeaders(null));
            JsonObject filterData = Json.parse(filterJson).getAsJsonObject();

            List<Class> classes = new ArrayList<>();
            java.util.LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

            // 解析分类
            if (categoryData.has("data") && categoryData.get("data").isJsonArray()) {
                JsonArray dataArray = categoryData.getAsJsonArray("data");
                for (int i = 0; i < dataArray.size(); i++) {
                    JsonObject item = dataArray.get(i).getAsJsonObject();
                    String typeName = item.has("typeName") ? item.get("typeName").getAsString() : "";
                    String typeId = item.has("typeId") ? String.valueOf(item.get("typeId").getAsInt()) : "";
                    classes.add(new Class(typeId, typeName));
                }
            }

            // 解析筛选条件
            if (filterData.has("data") && filterData.get("data").isJsonObject()) {
                JsonObject dataObj = filterData.getAsJsonObject("data");
                for (String tid : dataObj.keySet()) {
                    List<Filter> filterItems = new ArrayList<>();
                    
                    // 排序选项
                    List<Filter.Value> sortValues = new ArrayList<>();
                    sortValues.add(new Filter.Value("最近更新", "2"));
                    sortValues.add(new Filter.Value("人气高低", "3"));
                    sortValues.add(new Filter.Value("评分高低", "4"));

                    JsonObject d = dataObj.getAsJsonObject(tid);
                    
                    // 类型筛选
                    if (d.has("typeList") && d.getAsJsonArray("typeList").size() > 0) {
                        List<Filter.Value> typeValues = new ArrayList<>();
                        JsonArray typeList = d.getAsJsonArray("typeList");
                        for (int i = 0; i < typeList.size(); i++) {
                            JsonObject typeItem = typeList.get(i).getAsJsonObject();
                            String itemText = typeItem.has("itemText") ? typeItem.get("itemText").getAsString() : "";
                            String itemValue = typeItem.has("itemValue") ? typeItem.get("itemValue").getAsString() : "";
                            typeValues.add(new Filter.Value(itemText, itemValue));
                        }
                        filterItems.add(new Filter("type", "类型", typeValues));
                    }

                    // 剧情筛选
                    if (d.has("plotList") && d.getAsJsonArray("plotList").size() > 0) {
                        List<Filter.Value> plotValues = new ArrayList<>();
                        JsonArray plotList = d.getAsJsonArray("plotList");
                        for (int i = 0; i < plotList.size(); i++) {
                            JsonObject plotItem = plotList.get(i).getAsJsonObject();
                            String itemText = plotItem.has("itemText") ? plotItem.get("itemText").getAsString() : "";
                            plotValues.add(new Filter.Value(itemText, itemText));
                        }
                        filterItems.add(new Filter("v_class", "剧情", plotValues));
                    }

                    // 地区筛选
                    if (d.has("districtList") && d.getAsJsonArray("districtList").size() > 0) {
                        List<Filter.Value> areaValues = new ArrayList<>();
                        JsonArray areaList = d.getAsJsonArray("districtList");
                        for (int i = 0; i < areaList.size(); i++) {
                            JsonObject areaItem = areaList.get(i).getAsJsonObject();
                            String itemText = areaItem.has("itemText") ? areaItem.get("itemText").getAsString() : "";
                            areaValues.add(new Filter.Value(itemText, itemText));
                        }
                        filterItems.add(new Filter("area", "地区", areaValues));
                    }

                    // 年份筛选
                    if (d.has("yearList") && d.getAsJsonArray("yearList").size() > 0) {
                        List<Filter.Value> yearValues = new ArrayList<>();
                        JsonArray yearList = d.getAsJsonArray("yearList");
                        for (int i = 0; i < yearList.size(); i++) {
                            JsonObject yearItem = yearList.get(i).getAsJsonObject();
                            String itemText = yearItem.has("itemText") ? yearItem.get("itemText").getAsString() : "";
                            yearValues.add(new Filter.Value(itemText, itemText));
                        }
                        filterItems.add(new Filter("year", "年份", yearValues));
                    }

                    // 语言筛选
                    if (d.has("languageList") && d.getAsJsonArray("languageList").size() > 0) {
                        List<Filter.Value> langValues = new ArrayList<>();
                        JsonArray langList = d.getAsJsonArray("languageList");
                        for (int i = 0; i < langList.size(); i++) {
                            JsonObject langItem = langList.get(i).getAsJsonObject();
                            String itemText = langItem.has("itemText") ? langItem.get("itemText").getAsString() : "";
                            langValues.add(new Filter.Value(itemText, itemText));
                        }
                        filterItems.add(new Filter("lang", "语言", langValues));
                    }

                    // 排序筛选
                    filterItems.add(new Filter("sort", "排序", sortValues));

                    filters.put(tid, filterItems);
                }
            }

            return Result.string(classes, filters);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String homeVideoContent() {
        try {
            // 获取首页推荐数据
            String homeUrl = host + "/api/mw-movie/anonymous/v1/home/all/list";
            String homeJson = OkHttp.string(homeUrl, getHeaders(null));
            JsonObject homeData = Json.parse(homeJson).getAsJsonObject();

            // 获取热门搜索数据
            String hotSearchUrl = host + "/api/mw-movie/anonymous/home/hotSearch";
            String hotSearchJson = OkHttp.string(hotSearchUrl, getHeaders(null));
            JsonObject hotSearchData = Json.parse(hotSearchJson).getAsJsonObject();

            List<Vod> vods = new ArrayList<Vod>();

            // 处理首页数据
            if (homeData.has("data") && homeData.get("data").isJsonObject()) {
                JsonObject dataObj = homeData.getAsJsonObject("data");
                for (String key : dataObj.keySet()) {
                    JsonObject listObj = dataObj.getAsJsonObject(key);
                    if (listObj.has("list") && listObj.getAsJsonArray("list").size() > 0) {
                        JsonArray listArray = listObj.getAsJsonArray("list");
                        vods.addAll(parseVodList(listArray));
                    }
                }
            }

            // 处理热门搜索数据
            if (hotSearchData.has("data") && hotSearchData.get("data").isJsonArray()) {
                JsonArray hotList = hotSearchData.getAsJsonArray("data");
                vods.addAll(parseVodList(hotList));
            }

            return Result.get().vod(vods).string();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("area", extend.getOrDefault("area", ""));
            params.put("filterStatus", "1");
            params.put("lang", extend.getOrDefault("lang", ""));
            params.put("pageNum", pg);
            params.put("pageSize", "30");
            params.put("sort", extend.getOrDefault("sort", "1"));
            params.put("sortBy", "1");
            params.put("type", extend.getOrDefault("type", ""));
            params.put("type1", tid);
            params.put("v_class", extend.getOrDefault("v_class", ""));
            params.put("year", extend.getOrDefault("year", ""));
            params.put("key", "cb808529bae6b6be45ecfab29a4889bc");

            String url = host + "/api/mw-movie/anonymous/video/list?" + buildParams(params);
            String json = OkHttp.string(url, getHeaders(params));
            JsonObject data = Json.parse(json).getAsJsonObject();

            List<Vod> vods = new ArrayList<Vod>();
            if (data.has("data") && data.get("data").isJsonObject()) {
                JsonObject dataObj = data.getAsJsonObject("data");
                if (dataObj.has("list") && dataObj.getAsJsonArray("list").size() > 0) {
                    JsonArray listArray = dataObj.getAsJsonArray("list");
                    vods.addAll(parseVodList(listArray));
                }
            }

            return Result.get().vod(vods).page(Integer.parseInt(pg), 9999, 90, 999999).string();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String url = host + "/api/mw-movie/anonymous/video/detail?id=" + ids.get(0);
            Map<String, String> params = new HashMap<>();
            params.put("id", ids.get(0));
            String json = OkHttp.string(url, getHeaders(params));
            JsonObject data = Json.parse(json).getAsJsonObject();

            List<Vod> vods = new ArrayList<Vod>();
            if (data.has("data") && data.get("data").isJsonObject()) {
                JsonObject dataObj = data.getAsJsonObject("data");
                Vod vod = parseVod(dataObj);
                vod.setVodPlayFrom("金牌");
                
                if (dataObj.has("episodelist")) {
                    JsonArray episodes = dataObj.getAsJsonArray("episodelist");
                    List<String> playList = new ArrayList<>();
                    for (int i = 0; i < episodes.size(); i++) {
                        JsonObject ep = episodes.get(i).getAsJsonObject();
                        String name = ep.has("name") ? ep.get("name").getAsString() : "第" + (i+1) + "集";
                        String nid = ep.has("nid") ? ep.get("nid").getAsString() : String.valueOf(i+1);
                        // 格式：第1集$ID@@NID
                        playList.add(name + "$" + ids.get(0) + "@@" + nid);
                    }
                    vod.setVodPlayUrl(TextUtils.join("#", playList));
                }
                
                vods.add(vod);
            }

            return Result.string(vods);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("keyword", key);
            params.put("pageNum", pg);
            params.put("pageSize", "8");
            params.put("sourceCode", "1");
            params.put("key", "cb808529bae6b6be45ecfab29a4889bc");

            String url = host + "/api/mw-movie/anonymous/video/searchByWord?" + buildParams(params);
            String json = OkHttp.string(url, getHeaders(params));
            JsonObject data = Json.parse(json).getAsJsonObject();

            List<Vod> vods = new ArrayList<Vod>();
            if (data.has("data") && data.get("data").isJsonObject()) {
                JsonObject resultObj = data.getAsJsonObject("data").getAsJsonObject("result");
                if (resultObj.has("list") && resultObj.getAsJsonArray("list").size() > 0) {
                    JsonArray listArray = resultObj.getAsJsonArray("list");
                    vods.addAll(parseVodList(listArray));
                }
            }

            return Result.get().vod(vods).page(Integer.parseInt(pg), 9999, 90, 999999).string();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String[] ids = id.split("@@");
            String url = host + "/api/mw-movie/anonymous/v2/video/episode/url?clientType=1&id=" + ids[0] + "&nid=" + ids[1];
            
            Map<String, String> params = new HashMap<>();
            params.put("clientType", "1");
            params.put("id", ids[0]);
            params.put("nid", ids[1]);
            
            String json = OkHttp.string(url, getHeaders(params));
            JsonObject data = Json.parse(json).getAsJsonObject();

            if (data.has("data") && data.get("data").isJsonObject()) {
                JsonObject dataObj = data.getAsJsonObject("data");
                if (dataObj.has("list") && dataObj.getAsJsonArray("list").size() > 0) {
                    JsonArray listArray = dataObj.getAsJsonArray("list");
                    if (listArray.size() > 0) {
                        // Java 版本取第一个视频链接
                        JsonObject firstItem = listArray.get(0).getAsJsonObject();
                        String videoUrl = firstItem.has("url") ? firstItem.get("url").getAsString() : "";
                        
                        // 设置请求头，包含必要的 Referer 和 Origin
                        Map<String, String> headers = new HashMap<>();
                        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; ) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.61 Chrome/126.0.6478.61 Not/A)Brand/8  Safari/537.36");
                        headers.put("sec-ch-ua-platform", "\"Windows\"");
                        headers.put("DNT", "1");
                        headers.put("sec-ch-ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"");
                        headers.put("sec-ch-ua-mobile", "?0");
                        headers.put("Origin", host);
                        headers.put("Referer", host + "/");
                        
                        return Result.get().url(videoUrl).parse(0).header(headers).string();
                    }
                }
            }
            
            return Result.get().url("").string();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.get().url("").string();
        }
    }

    private String buildParams(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) sb.append("&");
            sb.append(Uri.encode(entry.getKey())).append("=").append(Uri.encode(entry.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private Map<String, String> getHeaders(Map<String, String> params) {
        if (params == null) params = new HashMap<>();
        // 使用 TreeMap 自动排序，确保 MD5 前的字符串顺序与 Python 一致
        java.util.TreeMap<String, String> sortedParams = new java.util.TreeMap<>(params);
        sortedParams.put("key", "cb808529bae6b6be45ecfab29a4889bc");
        sortedParams.put("t", String.valueOf(System.currentTimeMillis()));
        
        String paramString = buildParams(sortedParams); // 确保 buildParams 遍历顺序
        String sign = Crypto.sha1(Crypto.md5(paramString)); // 默认使用UTF-8编码
        
        String deviceId = UUID.randomUUID().toString();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; ) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.61 Chrome/126.0.6478.61 Not/A)Brand/8  Safari/537.36");
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("sign", sign);
        headers.put("t", sortedParams.get("t"));
        headers.put("deviceid", deviceId);
        
        return headers;
    }

    private List<Vod> parseVodList(JsonArray jsonArray) {
        List<Vod> vods = new ArrayList<Vod>();
        for (int i = 0; i < jsonArray.size(); i++) {
            try {
                JsonElement item = jsonArray.get(i);
                Vod vod = parseVod(item.getAsJsonObject());
                vods.add(vod);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return vods;
    }
    
    private Vod parseVod(JsonObject item) {
        Vod vod = new Vod();
        
        try {
            // 映射字段
            if (item.has("vod_id") || item.has("id")) {
                vod.setVodId(item.has("vod_id") ? item.get("vod_id").getAsString() : item.get("id").getAsString());
            }
            if (item.has("vod_name") || item.has("name")) {
                vod.setVodName(item.has("vod_name") ? item.get("vod_name").getAsString() : item.get("name").getAsString());
            }
            if (item.has("vod_pic") || item.has("pic") || item.has("cover")) {
                String pic = "";
                if (item.has("vod_pic")) pic = item.get("vod_pic").getAsString();
                else if (item.has("pic")) pic = item.get("pic").getAsString();
                else if (item.has("cover")) pic = item.get("cover").getAsString();
                if (!pic.startsWith("http")) {
                    pic = host + pic;
                }
                vod.setVodPic(pic);
            }
            if (item.has("vod_remarks") || item.has("remarks") || item.has("score")) {
                vod.setVodRemarks(item.has("vod_remarks") ? item.get("vod_remarks").getAsString() : 
                                item.has("remarks") ? item.get("remarks").getAsString() : 
                                item.get("score").getAsString());
            }
            if (item.has("vod_year") || item.has("year")) {
                vod.setVodYear(item.has("vod_year") ? item.get("vod_year").getAsString() : item.get("year").getAsString());
            }
            if (item.has("vod_area") || item.has("area")) {
                vod.setVodArea(item.has("vod_area") ? item.get("vod_area").getAsString() : item.get("area").getAsString());
            }
            if (item.has("vod_actor") || item.has("actor")) {
                vod.setVodActor(item.has("vod_actor") ? item.get("vod_actor").getAsString() : item.get("actor").getAsString());
            }
            if (item.has("vod_director") || item.has("director")) {
                vod.setVodDirector(item.has("vod_director") ? item.get("vod_director").getAsString() : item.get("director").getAsString());
            }
            if (item.has("vod_content") || item.has("content") || item.has("descript")) {
                vod.setVodContent(item.has("vod_content") ? item.get("vod_content").getAsString() : 
                                item.has("content") ? item.get("content").getAsString() : 
                                item.get("descript").getAsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return vod;
    }

    private String selectBestHost(List<String> hosts) {
        // 如果只有一个主机，直接返回
        if (hosts == null || hosts.isEmpty()) {
            return "";
        }
        if (hosts.size() == 1) {
            return hosts.get(0);
        }
        
        // 多个主机时，进行测速选择最佳主机
        Map<String, Long> results = new HashMap<>();
        List<Thread> threads = new ArrayList<>();
        
        // 创建测速线程
        for (String url : hosts) {
            Thread thread = new Thread(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    // 发送HEAD请求测试延迟
                    java.net.URL urlObj = new java.net.URL(url);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
                    conn.setRequestMethod("HEAD");
                    conn.setConnectTimeout(1000); // 1秒超时
                    conn.setReadTimeout(1000);
                    conn.setInstanceFollowRedirects(false); // 不跟随重定向
                    conn.connect();
                    long delay = System.currentTimeMillis() - startTime;
                    results.put(url, delay);
                } catch (Exception e) {
                    // 连接失败，标记为无穷大
                    results.put(url, Long.MAX_VALUE);
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 选择延迟最小的主机
        String bestHost = hosts.get(0);
        long minDelay = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : results.entrySet()) {
            if (entry.getValue() < minDelay) {
                minDelay = entry.getValue();
                bestHost = entry.getKey();
            }
        }
        
        return bestHost;
    }
}