package com.github.catvod.spider;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.github.catvod.crawler.SpiderDebug;
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
        this.executor = Executors.newFixedThreadPool(5);
    }

    public static Application context() {
        return get().app;
    }

    public static void init(Context context) {
        if (context == null) return;
        get().app = (Application) context.getApplicationContext();
        
        execute(() -> {
            Proxy.init();
            // 初始化完成后打印日志，方便线上调试
            String msg = "CatVodSpider 初始化完成 | 代理端口：" + Proxy.getPort();
            try {
                SpiderDebug.log(msg);
            } catch (Throwable ignored) {
                Log.i("Init", msg);
            }
        });
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
