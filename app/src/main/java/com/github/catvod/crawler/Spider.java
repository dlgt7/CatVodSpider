package com.github.catvod.crawler;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * 爬虫抽象基类
 * 优化：DCL单例、高性能集合处理、默认行为日志追踪
 */
public abstract class Spider {

    public String siteKey;
    private static volatile OkHttpClient mClient;

    /**
     * 初始化爬虫环境
     * @param context 非空 Android 上下文
     * @param extend  可选的扩展参数（JSON 字符串或 Key-Value）
     */
    public void init(@NonNull Context context, String extend) throws Exception {
        // 子类可根据需要解析 extend 参数
    }

    public String homeContent(boolean filter) throws Exception {
        SpiderDebug.log("Default homeContent: returning empty JSON");
        return "{}";
    }

    public String homeVideoContent() throws Exception {
        return "{}";
    }

    /**
     * 获取分类内容
     * @param extend 已在外部处理，子类直接读取即可
     */
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        SpiderDebug.log("Default categoryContent: returning empty JSON");
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

    @Nullable
    public Object[] proxy(Map<String, String> params) throws Exception {
        return null;
    }

    @Nullable
    public String action(String action) throws Exception {
        return null;
    }

    /**
     * 释放资源。子类必须在此关闭 IO 流、定时器或取消网络请求。
     */
    public void destroy() {
        SpiderDebug.info("Spider [" + siteKey + "] destroy called");
    }

    /**
     * 安全 DNS 接口，预留 DoH (DNS over HTTPS) 扩展位
     */
    public static Dns safeDns() {
        return Dns.SYSTEM;
    }

    /**
     * 获取单例 OkHttpClient
     * 优化：统一 20s 超时时间，支持并发环境下的单例访问
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
}
