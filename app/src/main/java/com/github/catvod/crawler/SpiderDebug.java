package com.github.catvod.crawler;

import android.util.Log;
import android.text.TextUtils;

public class SpiderDebug {

    private static final String TAG = "SpiderDebug";

    /**
     * 记录异常信息，包含完整的堆栈跟踪
     */
    public static void log(Throwable e) {
        if (e == null) return;
        // 使用 Log.e 记录异常，打印完整的堆栈信息以便追踪定位
        Log.e(TAG, "Exception caught: " + e.getMessage(), e);
    }

    /**
     * 记录普通调试消息
     */
    public static void log(String msg) {
        if (TextUtils.isEmpty(msg)) return;
        Log.d(TAG, msg);
    }

    /**
     * 记录带 Tag 的错误消息
     */
    public static void error(String tag, String msg) {
        Log.e(TAG, "[" + tag + "] " + msg);
    }
}
