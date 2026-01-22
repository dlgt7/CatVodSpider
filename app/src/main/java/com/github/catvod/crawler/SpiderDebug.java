package com.github.catvod.crawler;

import android.util.Log;

/**
 * 增强型调试日志工具
 * 优化：动态栈帧定位、异常完整回退、格式化支持
 */
public class SpiderDebug {

    private static final String TAG = "SpiderDebug";

    /**
     * 动态获取调用者的位置信息，不再依赖硬编码索引
     */
    private static String getCallLocation() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean foundSelf = false;
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.equals(SpiderDebug.class.getName())) {
                foundSelf = true;
                continue;
            }
            if (foundSelf) {
                return String.format("[%s:%d] ", element.getFileName(), element.getLineNumber());
            }
        }
        return "";
    }

    private static String format(String msg) {
        return "[Thread-" + Thread.currentThread().getId() + "] " + getCallLocation() + msg;
    }

    public static void log(Throwable e) {
        if (e == null) return;
        // 确保消息不为空
        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
        Log.e(TAG, format("Exception: " + msg), e);
    }

    public static void log(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        Log.d(TAG, format(msg));
    }

    // 支持格式化输出
    public static void log(String format, Object... args) {
        log(String.format(format, args));
    }

    public static void info(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        Log.i(TAG, format(msg));
    }

    public static void error(String msg, Throwable e) {
        Log.e(TAG, format(msg), e);
    }
}
