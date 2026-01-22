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

    private static class Loader {
        static final Init INSTANCE = new Init();
    }

    public static Init get() {
        return Loader.INSTANCE;
    }

    private Init() {
        this.handler = new Handler(Looper.getMainLooper());
        // 核心线程数 5，适合 Spider 的 IO 密集型任务
        this.executor = Executors.newFixedThreadPool(5);
    }

    public static Application context() {
        return get().app;
    }

    public static void init(Context context) {
        if (context == null) return;
        // 确保持有的是全局 Application 上下文
        get().app = (Application) context.getApplicationContext();
        // 异步执行代理初始化，防止启动时卡顿
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
     * 建议在 Spider 彻底销毁或壳子退出时调用
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
