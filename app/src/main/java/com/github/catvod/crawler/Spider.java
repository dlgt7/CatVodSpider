package com.github.catvod.crawler;

import android.content.Context;

import com.github.catvod.utils.UA;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

public abstract class Spider {

    public String siteKey;

    // 为爬虫任务创建专用的线程池
    private static final ExecutorService crawlerExecutor = Executors.newFixedThreadPool(
        Math.min(4, Runtime.getRuntime().availableProcessors()), // 最大4个线程，或CPU核心数（取较小值）
        new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Crawler-" + threadNumber.getAndIncrement());
                t.setDaemon(false); // 非守护线程
                t.setPriority(Thread.NORM_PRIORITY); // 普通优先级
                return t;
            }
        }
    );

    public void init(Context context) throws Exception {
    }

    public void init(Context context, String extend) throws Exception {
        init(context);
    }

    public String homeContent(boolean filter) throws Exception {
        return "";
    }

    public String homeVideoContent() throws Exception {
        return "";
    }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return "";
    }

    public String detailContent(List<String> ids) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return "";
    }

    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "";
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

    public Object[] proxy(Map<String, String> params) throws Exception {
        return null;
    }

    public String action(String action) throws Exception {
        return null;
    }

    public void destroy() {
        // 关闭线程池
        if (crawlerExecutor != null && !crawlerExecutor.isShutdown()) {
            crawlerExecutor.shutdown();
        }
    }

    /**
     * 获取自定义DNS实现
     * @return 自定义DNS实现，如果返回null则使用默认DNS
     */
    public static Dns safeDns() {
        return null;
    }

    /**
     * 获取自定义OkHttpClient实现
     * @return 自定义客户端实例，如果返回null则使用默认客户端
     */
    public static OkHttpClient client() {
        return null;
    }

    /**
     * 获取默认User-Agent
     * @return 默认User-Agent字符串
     */
    public static String userAgent() {
        return UA.CHROME;
    }

    /**
     * 获取随机User-Agent
     * @return 随机User-Agent字符串，用于反爬虫
     */
    public static String randomUserAgent() {
        return UA.getRandom();
    }

    /**
     * 获取爬虫专用线程池
     * @return 爬虫任务专用线程池
     */
    protected ExecutorService getCrawlerExecutor() {
        return crawlerExecutor;
    }

    /**
     * 异步执行初始化方法
     * @param context 上下文
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> initAsync(Context context) {
        return CompletableFuture.runAsync(() -> {
            try {
                init(context);
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }, crawlerExecutor);
    }

    /**
     * 异步执行初始化方法（带扩展参数）
     * @param context 上下文
     * @param extend 扩展参数
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> initAsync(Context context, String extend) {
        return CompletableFuture.runAsync(() -> {
            try {
                init(context, extend);
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }, crawlerExecutor);
    }

    /**
     * 异步执行首页内容获取
     * @param filter 是否过滤
     * @return CompletableFuture<String>
     */
    public CompletableFuture<String> homeContentAsync(boolean filter) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return homeContent(filter);
            } catch (Exception e) {
                SpiderDebug.log(e);
                return "";
            }
        }, crawlerExecutor);
    }

    /**
     * 异步执行首页视频内容获取
     * @return CompletableFuture<String>
     */
    public CompletableFuture<String> homeVideoContentAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return homeVideoContent();
            } catch (Exception e) {
                SpiderDebug.log(e);
                return "";
            }
        }, crawlerExecutor);
    }

    /**
     * 异步执行分类内容获取
     * @param tid 分类ID
     * @param pg 页码
     * @param filter 是否过滤
     * @param extend 扩展参数
     * @return CompletableFuture<String>
     */
    public CompletableFuture<String> categoryContentAsync(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return categoryContent(tid, pg, filter, extend);
            } catch (Exception e) {
                SpiderDebug.log(e);
                return "";
            }
        }, crawlerExecutor);
    }

    /**
     * 异步执行详情内容获取
     * @param ids ID列表
     * @return CompletableFuture<String>
     */
    public CompletableFuture<String> detailContentAsync(List<String> ids) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return detailContent(ids);
            } catch (Exception e) {
                SpiderDebug.log(e);
                return "";
            }
        }, crawlerExecutor);
    }

    /**
     * 异步执行搜索内容获取
     * @param key 搜索关键词
     * @param quick 是否快速搜索
     * @return CompletableFuture<String>
     */
    public CompletableFuture<String> searchContentAsync(String key, boolean quick) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return searchContent(key, quick);
            } catch (Exception e) {
                SpiderDebug.log(e);
                return "";
            }
        }, crawlerExecutor);
    }

    /**
     * 异步执行搜索内容获取（带页码）
     * @param key 搜索关键词
     * @param quick 是否快速搜索
     * @param pg 页码
     * @return CompletableFuture<String>
     */
    public CompletableFuture<String> searchContentAsync(String key, boolean quick, String pg) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return searchContent(key, quick, pg);
            } catch (Exception e) {
                SpiderDebug.log(e);
                return "";
            }
        }, crawlerExecutor);
    }

    /**
     * 异步执行播放内容获取
     * @param flag 标识
     * @param id ID
     * @param vipFlags VIP标识列表
     * @return CompletableFuture<String>
     */
    public CompletableFuture<String> playerContentAsync(String flag, String id, List<String> vipFlags) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return playerContent(flag, id, vipFlags);
            } catch (Exception e) {
                SpiderDebug.log(e);
                return "";
            }
        }, crawlerExecutor);
    }

    /**
     * 异步执行直播内容获取
     * @param url URL
     * @return CompletableFuture<String>
     */
    public CompletableFuture<String> liveContentAsync(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return liveContent(url);
            } catch (Exception e) {
                SpiderDebug.log(e);
                return "";
            }
        }, crawlerExecutor);
    }

    /**
     * 异步执行动作
     * @param action 动作
     * @return CompletableFuture<String>
     */
    public CompletableFuture<String> actionAsync(String action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action(action);
            } catch (Exception e) {
                SpiderDebug.log(e);
                return null;
            }
        }, crawlerExecutor);
    }
}