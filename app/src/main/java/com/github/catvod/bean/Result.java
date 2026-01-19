package com.github.catvod.bean;

import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Result {

    @SerializedName("class")
    private List<Class> classes;
    @SerializedName("list")
    private List<Vod> list;
    @SerializedName("filters")
    private LinkedHashMap<String, List<Filter>> filters;
    @SerializedName("header")
    private String header;
    @SerializedName("format")
    private String format;
    @SerializedName("danmaku")
    private List<Danmaku> danmaku;
    @SerializedName("click")
    private String click;
    @SerializedName("msg")
    private String msg;
    @SerializedName("url")
    private Object url;
    @SerializedName("subs")
    private List<Sub> subs;
    @SerializedName("parse")
    private int parse;
    @SerializedName("jx")
    private int jx;
    @SerializedName("page")
    private Integer page;
    @SerializedName("pagecount")
    private Integer pagecount;
    @SerializedName("limit")
    private Integer limit;
    @SerializedName("total")
    private Integer total;

    public static Result objectFrom(String str) {
        try {
            return Json.getGson().fromJson(str, Result.class);
        } catch (Exception e) {
            e.printStackTrace();
            return new Result();
        }
    }

    public static String string(List<Class> classes, List<Vod> list, LinkedHashMap<String, List<Filter>> filters) {
        return Result.get().classes(classes).vod(list).filters(filters).string();
    }

    public static String string(List<Class> classes, List<Vod> list, JSONObject filters) {
        return Result.get().classes(classes).vod(list).filters(filters).string();
    }

    public static String string(List<Class> classes, List<Vod> list, JsonElement filters) {
        return Result.get().classes(classes).vod(list).filters(filters).string();
    }

    public static String string(List<Class> classes, LinkedHashMap<String, List<Filter>> filters) {
        return Result.get().classes(classes).filters(filters).string();
    }

    public static String string(List<Class> classes, JsonElement filters) {
        return Result.get().classes(classes).filters(filters).string();
    }

    public static String string(List<Class> classes, JSONObject filters) {
        return Result.get().classes(classes).filters(filters).string();
    }

    public static String string(List<Class> classes, List<Vod> list) {
        return Result.get().classes(classes).vod(list).string();
    }

    public static String string(List<?> list) {
        if (list == null || list.isEmpty()) return "";
        // 加强类型校验，避免ClassCastException
        if (!list.isEmpty() && list.get(0) != null) {
            Object firstElement = list.get(0);
            if (firstElement instanceof Vod) {
                return Result.get().vod((List<Vod>) list).string();
            } else if (firstElement instanceof Class) {
                return Result.get().classes((List<Class>) list).string();
            }
        }
        return "";
    }

    public static String string(Vod item) {
        return Result.get().vod(item).string();
    }

    public static String error(String msg) {
        return Result.get().vod(Collections.emptyList()).msg(msg).string();
    }

    public static String notify(String msg) {
        return Result.get().msg(msg).string();
    }

    public static Result get() {
        return new Result();
    }

    public Result classes(List<Class> classes) {
        this.classes = classes;
        return this;
    }

    public Result vod(List<Vod> list) {
        this.list = list;
        return this;
    }

    public Result vod(Vod item) {
        this.list = Arrays.asList(item);
        return this;
    }

    public Result filters(LinkedHashMap<String, List<Filter>> filters) {
        this.filters = filters;
        return this;
    }

    public Result filters(JSONObject object) {
        if (object == null) return this;
        try {
            Type listType = new TypeToken<LinkedHashMap<String, List<Filter>>>() {}.getType();
            this.filters = Json.getGson().fromJson(object.toString(), listType);
        } catch (Exception e) {
            e.printStackTrace();
            this.filters = new LinkedHashMap<>();
        }
        return this;
    }

    public Result filters(JsonElement element) {
        if (element == null) return this;
        try {
            Type listType = new TypeToken<LinkedHashMap<String, List<Filter>>>() {}.getType();
            this.filters = Json.getGson().fromJson(element.toString(), listType);
        } catch (Exception e) {
            e.printStackTrace();
            this.filters = new LinkedHashMap<>();
        }
        return this;
    }

    public Result header(Map<String, String> header) {
        if (header == null || header.isEmpty()) return this;
        this.header = Json.getGson().toJson(header);
        return this;
    }

    public Result chrome() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header(header);
        return this;
    }

    public Result parse() {
        this.parse = 1;
        return this;
    }

    public Result parse(int parse) {
        this.parse = parse;
        return this;
    }

    public Result jx() {
        this.jx = 1;
        return this;
    }

    public Result url(String url) {
        this.url = url;
        return this;
    }

    public Result url(List<String> url) {
        this.url = url;
        return this;
    }

    public Result danmaku(List<Danmaku> danmaku) {
        this.danmaku = danmaku;
        return this;
    }

    public Result click(String click) {
        this.click = click;
        return this;
    }

    public Result msg(String msg) {
        this.msg = msg;
        return this;
    }

    public Result format(String format) {
        this.format = format;
        return this;
    }

    public Result subs(List<Sub> subs) {
        this.subs = subs;
        return this;
    }

    public Result dash() {
        this.format = "application/dash+xml";
        return this;
    }

    public Result m3u8() {
        this.format = "application/x-mpegURL";
        return this;
    }

    public Result rtsp() {
        this.format = "application/x-rtsp";
        return this;
    }

    public Result octet() {
        this.format = "application/octet-stream";
        return this;
    }

    public Result page() {
        return page(1, 1, 0, 1);
    }

    public Result page(int page, int count, int limit, int total) {
        // 修复分页逻辑，当page无效时默认为1
        this.page = page > 0 ? page : 1;
        this.pagecount = count > 0 ? count : 1;
        this.limit = limit > 0 ? limit : 0;
        this.total = total > 0 ? total : 0;
        return this;
    }

    public List<Class> getClasses() {
        return classes == null ? Collections.emptyList() : classes;
    }

    public List<Vod> getList() {
        return list == null ? Collections.emptyList() : list;
    }

    public LinkedHashMap<String, List<Filter>> getFilters() {
        return filters == null ? new LinkedHashMap<>() : filters;
    }

    public String getHeader() {
        return header;
    }

    public String getMsg() {
        return msg;
    }

    /**
     * 获取URL字符串，安全处理Object类型
     * 支持 String, List<String>, Map<String, String> 类型
     * @return 如果url是字符串则直接返回，如果是列表则返回第一个元素，
     *         如果是Map则返回第一个值，否则返回空字符串
     */
    public String getUrlString() {
        if (url == null) {
            return "";
        } else if (url instanceof String) {
            return (String) url;
        } else if (url instanceof List) {
            List<?> urlList = (List<?>) url;
            if (!urlList.isEmpty() && urlList.get(0) != null) {
                return urlList.get(0).toString();
            }
        } else if (url instanceof Map) {
            // 增强对Map类型URL的支持，返回第一个值
            Map<?, ?> map = (Map<?, ?>) url;
            if (!map.isEmpty()) {
                // 返回Map的第一个值
                return String.valueOf(map.values().iterator().next());
            }
        } else if (url instanceof Map) {
            // 如果是Map类型，将其转换为JSON字符串
            return Json.getGson().toJson(url);
        }
        return url.toString();
    }

    /**
     * 获取指定线路的URL
     * @param key 线路标识
     * @return 对应线路的URL，如果不存在则返回空字符串
     */
    public String getUrlByLine(String key) {
        if (url instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) url;
            Object value = map.get(key);
            return value != null ? value.toString() : "";
        }
        return getUrlString();
    }

    /**
     * 获取所有线路名称
     * @return 线路名称列表
     */
    public List<String> getUrlLines() {
        if (url instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) url;
            return new java.util.ArrayList<>((java.util.Collection<String>) map.keySet());
        }
        return Collections.singletonList(getUrlString());
    }

    /**
     * 获取URL对象
     */
    public Object getUrl() {
        return url;
    }

    public List<String> getUrlList() {
        if (url == null) {
            return Collections.emptyList();
        } else if (url instanceof List) {
            return (List<String>) url;
        } else if (url instanceof String) {
            return Arrays.asList((String) url);
        } else if (url instanceof Map) {
            // 如果是Map，返回所有值的列表
            Map<?, ?> map = (Map<?, ?>) url;
            return new java.util.ArrayList<>((java.util.Collection<String>) map.values());
        }
        // 其他类型转换为单元素列表
        return Arrays.asList(url.toString());
    }

    public String string() {
        return toString();
    }

    @Override
    public String toString() {
        try {
            return Json.getGson().newBuilder().disableHtmlEscaping().create().toJson(this);
        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }
}