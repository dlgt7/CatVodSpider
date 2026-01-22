package com.github.catvod.crawler;

import android.util.Log;

/**
 * 调试日志工具类
 * 修复：支持自动定位调用位置、严格过滤空白字符、提供多级别日志
 */
public class SpiderDebug {

    private static final String TAG = "SpiderDebug";

    /**
     * 自动获取调用者的堆栈信息 (类名:行号)
     */
    private static String getCallInfo() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length > 4) {
            StackTraceElement element = stackTrace[4];
            return "[" + element.getFileName() + ":" + element.getLineNumber() + "] ";
        }
        return "";
    }

    public static void log(Throwable e) {
        if (e == null) return;
        Log.e(TAG, "[Thread-" + Thread.currentThread().getId() + "] " + getCallInfo(), e);
    }

    public static void log(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        Log.d(TAG, "[Thread-" + Thread.currentThread().getId() + "] " + getCallInfo() + msg);
    }

    public static void info(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        Log.i(TAG, "[Thread-" + Thread.currentThread().getId() + "] " + getCallInfo() + msg);
    }

    public static void error(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        Log.e(TAG, "[Thread-" + Thread.currentThread().getId() + "] " + getCallInfo() + msg);
    }
}
