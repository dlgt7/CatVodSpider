package com.github.catvod.crawler;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * 爬虫基类
 * 优化：防御性字段初始化、明确的方法契约、增强型单例管理
 */
public abstract class Spider {

    // 默认初始化防止日志输出 null
    public String siteKey = "";

    /**
     * 初始化环境
     * @param extend 若为 JSON，建议使用 JSONObject 解析
     * @throws Exception 建议捕获并抛出特定的 IOException 或 ParseException
     */
    public void init(@NonNull Context context, String extend) throws Exception {
    }

    public String homeContent(boolean filter) throws Exception {
        SpiderDebug.log("Default homeContent called: Overwrite this in subclasses");
        return "{}";
    }

    public String homeVideoContent() throws Exception {
        return "{}";
    }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        SpiderDebug.log("Default categoryContent called: Params [tid: %s, pg: %s]", tid, pg);
        return "{}";
    }

    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "{}";
        return "{}";
    }

    public String searchContent(String key, boolean quick) throws Exception {
        return "{}";
    }

    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick);
    }

    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "{}";
    }

    public String liveContent(String url) throws Exception {
        return "";
    }

    public boolean manualVideoCheck() throws Exception {
        return false;
    }

    public boolean isVideoFormat(String url) throws Exception {
        return false;
    }

    /**
     * 代理回调
     * @return Object[] 预期格式: [int code, String mimeType, InputStream content] 或 [int code, Map headers, String content]
     */
    @Nullable
    public Object[] proxy(Map<String, String> params) throws Exception {
        return null;
    }

    @Nullable
    public String action(String action) throws Exception {
        return null;
    }

    /**
     * 资源销毁
     * 注意：必须在子类中覆盖此方法以关闭自定义线程池或 IO 流
     */
    public void destroy() {
        SpiderDebug.info("Spider [" + siteKey + "] destroyed");
    }

    public static Dns safeDns() {
        return Dns.SYSTEM;
    }

    /**
     * 全局单例 OkHttpClient
     * 若子类需要特定拦截器，请使用 client().newBuilder().addInterceptor(...).build()
     */
    public static OkHttpClient client() {
        if (mClient == null) {
            synchronized (Spider.class) {
                if (mClient == null) {
                    mClient = new OkHttpClient.Builder()
                            .readTimeout(20, TimeUnit.SECONDS)
                            .writeTimeout(20, TimeUnit.SECONDS)
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .dns(safeDns())
                            .build();
                }
            }
        }
        return mClient;
    }

    private static volatile OkHttpClient mClient;
}
