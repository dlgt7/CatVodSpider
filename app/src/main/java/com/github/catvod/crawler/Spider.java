package com.github.catvod.crawler;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * 爬虫基类
 * 改进：增加了注解支持、完善了 HttpClient 初始化、统一了返回值规范
 */
public abstract class Spider {

    public String siteKey;
    private static volatile OkHttpClient mClient;

    /**
     * 初始化方法
     */
    public void init(Context context) throws Exception {
    }

    public void init(Context context, String extend) throws Exception {
        init(context);
    }

    /**
     * 首页内容
     * @return 默认返回空 JSON 对象字符串，防止解析报错
     */
    public String homeContent(boolean filter) throws Exception {
        return "{}";
    }

    /**
     * 首页最近更新内容
     */
    public String homeVideoContent() throws Exception {
        return "{}";
    }

    /**
     * 分类内容
     */
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return "{}";
    }

    /**
     * 详情内容
     */
    public String detailContent(List<String> ids) throws Exception {
        return "{}";
    }

    /**
     * 搜索内容
     */
    public String searchContent(String key, boolean quick) throws Exception {
        return "{}";
    }

    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick);
    }

    /**
     * 播放配置
     */
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "{}";
    }

    /**
     * 直播内容
     */
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
     */
    @Nullable
    public Object[] proxy(Map<String, String> params) throws Exception {
        return null;
    }

    /**
     * 自定义交互动作
     */
    @Nullable
    public String action(String action) throws Exception {
        return null;
    }

    /**
     * 销毁资源，子类需在此关闭 IO 流或清理缓存
     */
    public void destroy() {
    }

    /**
     * 获取安全的 DNS 配置
     */
    public static Dns safeDns() {
        return Dns.SYSTEM;
    }

    /**
     * 获取全局统一的 OkHttpClient 实例 (双重检查锁定单例)
     */
    public static OkHttpClient client() {
        if (mClient == null) {
            synchronized (Spider.class) {
                if (mClient == null) {
                    mClient = new OkHttpClient.Builder()
                            .readTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .dns(safeDns())
                            .build();
                }
            }
        }
        return mClient;
    }
}
