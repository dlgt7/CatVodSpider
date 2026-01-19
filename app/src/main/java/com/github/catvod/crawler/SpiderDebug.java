package com.github.catvod.crawler;

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class SpiderDebug {

    private static final String TAG = SpiderDebug.class.getSimpleName();

    /**
     * 记录异常信息
     * @param e 异常对象
     */
    public static void log(Throwable e) {
        if (e != null) {
            // 获取完整的堆栈跟踪信息
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stackTrace = sw.toString();
            Log.e(TAG, e.getMessage() + "\n" + stackTrace);
        }
    }

    /**
     * 记录普通日志信息
     * @param msg 日志消息
     */
    public static void log(String msg) {
        if (msg != null) {
            Log.d(TAG, msg);
        }
    }

    /**
     * 记录错误级别的日志
     * @param msg 错误消息
     */
    public static void error(String msg) {
        if (msg != null) {
            Log.e(TAG, msg);
        }
    }

    /**
     * 记录错误级别的日志（带异常）
     * @param msg 错误消息
     * @param e 异常对象
     */
    public static void error(String msg, Throwable e) {
        if (msg != null) {
            Log.e(TAG, msg, e);
        } else if (e != null) {
            log(e);
        }
    }

    /**
     * 记录警告级别的日志
     * @param msg 警告消息
     */
    public static void warn(String msg) {
        if (msg != null) {
            Log.w(TAG, msg);
        }
    }

    /**
     * 记录信息级别的日志
     * @param msg 信息消息
     */
    public static void info(String msg) {
        if (msg != null) {
            Log.i(TAG, msg);
        }
    }

    /**
     * 记录调试级别的日志
     * @param msg 调试消息
     */
    public static void debug(String msg) {
        if (msg != null) {
            Log.d(TAG, msg);
        }
    }
}