package com.github.catvod.spider;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Init {

    private Application app;
    private final ExecutorService executor;
    private final Handler handler;

    // 使用静态内部类实现单例 (线程安全且延迟加载)
    private static class Loader {
        static final Init INSTANCE = new Init();
    }

    public static Init get() {
        return Loader.INSTANCE;
    }

    private Init() {
        this.handler = new Handler(Looper.getMainLooper());
        // 核心线程数设为 5，最大程度兼顾性能与功耗
        this.executor = Executors.newFixedThreadPool(5);
    }

    public static Application context() {
        return get().app;
    }

    /**
     * 初始化入口
     */
    public static void init(Context context) {
        if (context == null) return;
        // 确保获取的是 ApplicationContext，防止 Activity 泄露
        get().app = (Application) context.getApplicationContext();
        // 异步执行代理初始化，避免阻塞主线程
        execute(Proxy::init);
    }

    public static void execute(Runnable runnable) {
        get().executor.execute(runnable);
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, int delay) {
        get().handler.postDelayed(runnable, delay);
    }

    /**
     * 释放资源 (可选：供壳子销毁时调用)
     */
    public void shutdown() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
