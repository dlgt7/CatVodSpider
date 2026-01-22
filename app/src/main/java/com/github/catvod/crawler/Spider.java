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
 * 爬虫核心抽象类
 * 修复：修复变量未使用逻辑、调整超时时间、增加默认调用日志
 */
public abstract class Spider {

    public String siteKey;
    private static volatile OkHttpClient mClient;

    public void init(@NonNull Context context) throws Exception {
        // 子类需实现初始化逻辑
    }

    public void init(@NonNull Context context, String extend) throws Exception {
        init(context);
    }

    public String homeContent(boolean filter) throws Exception {
        SpiderDebug.log("Default homeContent called (not overridden)");
        return "{}";
    }

    public String homeVideoContent() throws Exception {
        return "{}";
    }

    /**
     * @param extend 扩展参数，若子类需要使用，建议直接操作该 Map 或根据需求拷贝
     */
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        SpiderDebug.log("Default categoryContent called (not overridden)");
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
     * 资源销毁：强烈建议子类在此关闭自定义的网络连接、线程池或缓存
     */
    public void destroy() {
        SpiderDebug.log("Spider destroy called");
    }

    public static Dns safeDns() {
        // 预留 DoH 扩展点，目前使用系统默认 DNS
        return Dns.SYSTEM;
    }

    /**
     * 单例 OkHttpClient：将超时时间调整为 20s 以提高稳定性
     */
    public static OkHttpClient client() {
        if (mClient == null) {
            synchronized (Spider.class) {
                if (mClient == null) {
                    mClient = new OkHttpClient.Builder()
                            .readTimeout(20, TimeUnit.SECONDS)
                            .writeTimeout(20, TimeUnit.SECONDS)
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .dns(safeDns())
                            .build();
                }
            }
        }
        return mClient;
    }
}
