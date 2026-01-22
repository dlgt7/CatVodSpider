package com.github.catvod.crawler;

import android.util.Log;

/**
 * 调试日志工具类
 * - 优化：支持调用位置追踪、多级别记录及严格字符串过滤
 */
public class SpiderDebug {

    private static final String TAG = "SpiderDebug";

    /**
     * 获取调用者的类名、方法名及行号
     */
    private static String getCallLocation() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length > 4) {
            StackTraceElement e = stackTrace[4];
            return String.format("[%s:%d] ", e.getFileName(), e.getLineNumber());
        }
        return "";
    }

    private static String format(String msg) {
        return "[Thread-" + Thread.currentThread().getId() + "] " + getCallLocation() + msg;
    }

    public static void log(Throwable e) {
        if (e == null) return;
        Log.e(TAG, format("Exception: " + e.getMessage()), e);
    }

    public static void log(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        Log.d(TAG, format(msg));
    }

    public static void info(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        Log.i(TAG, format(msg));
    }

    public static void error(String msg, Throwable e) {
        Log.e(TAG, format(msg), e);
    }
}
