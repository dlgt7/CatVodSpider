package com.github.catvod.spider;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 初始化管理器
 * 1. 管理全局Application上下文
 * 2. 提供线程池执行异步任务
 * 3. 提供主线程Handler发送UI更新
 * 4. 集成反爬虫增强器初始化
 */
public class Init {

    private final ExecutorService executor;
    private final Handler handler;
    private Application app;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private static class Loader {
        // 由于使用了Holder模式，INSTANCE不需要volatile
        static final Init INSTANCE = new Init();
    }

    public static Init get() {
        return Loader.INSTANCE;
    }

    public Init() {
        this.handler = new Handler(Looper.getMainLooper());
        // 使用自定义线程工厂，为线程命名便于调试
        // 设置为守护线程，避免后台任务阻止应用退出
        // 减少线程池大小以节省系统资源，适用于I/O密集型初始化任务
        this.executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Init-thread");
                t.setDaemon(true); // 设置为守护线程，随主线程结束
                return t;
            }
        });
    }

    public static Application context() {
        return get().app;
    }

    /**
     * 初始化应用上下文
     * 注意：此方法可能耗时较长（如Proxy.findPort），应在线程池中执行
     * @param context 应用上下文
     */
    public static void init(Context context) {
        // 防止重复初始化
        if (get().initialized.get()) {
            return;
        }
        
        if (get().initialized.compareAndSet(false, true)) {
            get().app = ((Application) context.getApplicationContext()); // 使用getApplicationContext()避免内存泄漏
            
            // 将初始化任务放到线程池中执行，避免在主线程中耗时
            execute(() -> {
                Proxy.init();
                AntiCrawlerEnhancer.get().init(context);
            });
        }
    }

    /**
     * 在工作线程执行任务
     * @param runnable 要执行的任务
     */
    public static void execute(Runnable runnable) {
        get().executor.execute(runnable);
    }

    /**
     * 在主线程执行任务
     * @param runnable 要执行的任务
     */
    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    /**
     * 延迟在主线程执行任务
     * @param runnable 要执行的任务
     * @param delay 延迟时间（毫秒）
     */
    public static void post(Runnable runnable, int delay) {
        get().handler.postDelayed(runnable, delay);
    }

    /**
     * 关闭线程池，释放资源
     * 注意：通常在应用退出时调用
     */
    public static void shutdown() {
        get().executor.shutdown();
        try {
            // 等待最多5秒让现有任务完成
            if (!get().executor.awaitTermination(5, TimeUnit.SECONDS)) {
                // 如果超时，则强制关闭
                get().executor.shutdownNow();
                // 再等待一段时间确保线程真正终止
                if (!get().executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("线程池未能正常关闭");
                }
            }
        } catch (InterruptedException e) {
            // 恢复中断状态
            Thread.currentThread().interrupt();
            get().executor.shutdownNow();
        }
    }

    /**
     * 检查线程池是否已关闭
     * @return 如果已关闭返回true，否则返回false
     */
    public static boolean isShutdown() {
        return get().executor.isShutdown();
    }
    
    /**
     * 重置初始化状态，用于测试或特殊情况
     */
    public static void resetInitialization() {
        get().initialized.set(false);
    }
    
    /**
     * 检查是否已初始化
     * @return 如果已初始化返回true，否则返回false
     */
    public static boolean isInitialized() {
        return get().initialized.get();
    }
}