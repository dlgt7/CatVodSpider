package com.github.catvod.crawler;

import android.util.Log;
import android.text.TextUtils;

/**
 * 调试日志工具类
 * 优化：增加了线程ID追踪与完整的堆栈记录
 */
public class SpiderDebug {

    private static final String TAG = "SpiderDebug";

    /**
     * 记录异常：包含完整的堆栈信息与当前线程 ID
     */
    public static void log(Throwable e) {
        if (e == null) return;
        Log.e(TAG, "[Thread-" + Thread.currentThread().getId() + "] Error: " + e.getMessage(), e);
    }

    /**
     * 记录普通消息：增加空校验，防止无效日志输出
     */
    public static void log(String msg) {
        if (TextUtils.isEmpty(msg)) return;
        Log.d(TAG, "[Thread-" + Thread.currentThread().getId() + "] " + msg);
    }
}
