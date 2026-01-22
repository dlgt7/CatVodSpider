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
 * 爬虫核心抽象类
 * 优化：引入单例 OkHttpClient、参数预校验、高性能集合初始化
 */
public abstract class Spider {

    public String siteKey;
    private static volatile OkHttpClient mClient;

    /**
     * 初始化环境
     */
    public void init(@NonNull Context context) throws Exception {
    }

    public void init(@NonNull Context context, String extend) throws Exception {
        init(context);
    }

    /**
     * 首页内容：默认返回空 JSON 字符串，确保解析器不崩溃
     */
    public String homeContent(boolean filter) throws Exception {
        return "{}";
    }

    public String homeVideoContent() throws Exception {
        return "{}";
    }

    /**
     * 分类内容：优化 HashMap 初始化容量，减少 rehash 开销
     */
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (extend != null) {
            // 根据负载因子 0.75 计算初始容量
            int capacity = (int) (extend.size() / 0.75F + 1.0F);
            Map<String, String> safeExtend = new HashMap<>(capacity);
            safeExtend.putAll(extend);
        }
        return "{}";
    }

    /**
     * 详情内容：增加列表非空校验
     */
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
     * 显式资源释放：子类应在此关闭线程池或 IO 流
     */
    public void destroy() {
    }

    /**
     * 预留安全 DNS (DoH) 接口
     */
    public static Dns safeDns() {
        return Dns.SYSTEM;
    }

    /**
     * 线程安全的单例 OkHttpClient
     */
    public static OkHttpClient client() {
        if (mClient == null) {
            synchronized (Spider.class) {
                if (mClient == null) {
                    mClient = new OkHttpClient.Builder()
                            .readTimeout(10, TimeUnit.SECONDS)
                            .writeTimeout(10, TimeUnit.SECONDS)
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .dns(safeDns())
                            .build();
                }
            }
        }
        return mClient;
    }
}
