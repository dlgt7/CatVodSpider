package com.github.catvod.js.bean;

import android.content.Context;
import android.net.Uri;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.js.utils.JSUtil;

import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Info {

    private Context context;
    private Spider spider;
    private String key;
    private String name;
    private String api;
    private String ext;
    private String group;
    private String search;
    private String categories;
    private String timeout;
    private String encoding;
    private String proxy;
    private boolean play;
    private boolean danmaku;
    private boolean lazy;
    private String type;
    private String user;
    private String password;
    private String cookie;
    private String token;
    private String referer;
    private String userAgent;
    private Map<String, String> headers;
    private Cache cache;

    public Info(Context context, Spider spider) {
        this.context = context;
        this.spider = spider;
        this.cache = new Cache();
        this.headers = new HashMap<>();
    }

    public Info(Context context, Spider spider, JSONObject object) {
        this(context, spider);
        init(object);
    }

    public Context getContext() {
        return context;
    }

    public Spider getSpider() {
        return spider;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public boolean isPlay() {
        return play;
    }

    public void setPlay(boolean play) {
        this.play = play;
    }

    public boolean isDanmaku() {
        return danmaku;
    }

    public void setDanmaku(boolean danmaku) {
        this.danmaku = danmaku;
    }

    public boolean isLazy() {
        return lazy;
    }

    public void setLazy(boolean lazy) {
        this.lazy = lazy;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }

    public void removeHeader(String key) {
        this.headers.remove(key);
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public boolean hasKey() {
        return key != null && !key.isEmpty();
    }

    public boolean hasName() {
        return name != null && !name.isEmpty();
    }

    public boolean hasApi() {
        return api != null && !api.isEmpty();
    }

    public boolean hasExt() {
        return ext != null && !ext.isEmpty();
    }

    public boolean hasGroup() {
        return group != null && !group.isEmpty();
    }

    public boolean hasSearch() {
        return search != null && !search.isEmpty();
    }

    public boolean hasCategories() {
        return categories != null && !categories.isEmpty();
    }

    public boolean hasTimeout() {
        return timeout != null && !timeout.isEmpty();
    }

    public boolean hasEncoding() {
        return encoding != null && !encoding.isEmpty();
    }

    public boolean hasProxy() {
        return proxy != null && !proxy.isEmpty();
    }

    public boolean hasType() {
        return type != null && !type.isEmpty();
    }

    public boolean hasUser() {
        return user != null && !user.isEmpty();
    }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    public boolean hasCookie() {
        return cookie != null && !cookie.isEmpty();
    }

    public boolean hasToken() {
        return token != null && !token.isEmpty();
    }

    public boolean hasReferer() {
        return referer != null && !referer.isEmpty();
    }

    public boolean hasUserAgent() {
        return userAgent != null && !userAgent.isEmpty();
    }

    public boolean hasHeaders() {
        return headers != null && !headers.isEmpty();
    }

    public boolean isConfigured() {
        return hasKey() && hasApi();
    }

    public boolean isValid() {
        return hasApi() && new File(api).exists();
    }

    public boolean isRemote() {
        return hasApi() && api.startsWith("http");
    }

    public boolean isLocal() {
        return hasApi() && !isRemote();
    }

    public boolean requiresAuth() {
        return hasUser() || hasPassword() || hasToken();
    }

    public void init(JSONObject object) {
        try {
            key = object.optString("key");
            name = object.optString("name");
            api = object.optString("api");
            ext = object.optString("ext");
            group = object.optString("group");
            search = object.optString("search");
            categories = object.optString("categories");
            timeout = object.optString("timeout");
            encoding = object.optString("encoding");
            proxy = object.optString("proxy");
            play = object.optBoolean("play", false);
            danmaku = object.optBoolean("danmaku", false);
            lazy = object.optBoolean("lazy", false);
            type = object.optString("type");
            user = object.optString("user");
            password = object.optString("password");
            cookie = object.optString("cookie");
            token = object.optString("token");
            referer = object.optString("referer");
            userAgent = object.optString("userAgent");
            
            if (hasProxy()) {
                Uri uri = Uri.parse(proxy);
                if (uri.getHost() != null) {
                    JSUtil.setProxy(uri.getHost(), uri.getPort());
                }
            }
            
            SpiderDebug.log("Info initialized: " + toString());
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    @Override
    public String toString() {
        return "Info{" +
                "key='" + key + '\'' +
                ", name='" + name + '\'' +
                ", api='" + api + '\'' +
                ", type='" + type + '\'' +
                ", group='" + group + '\'' +
                ", play=" + play +
                ", danmaku=" + danmaku +
                ", lazy=" + lazy +
                '}';
    }
}