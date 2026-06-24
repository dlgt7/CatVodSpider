package com.github.catvod.spider.merge.a;

import com.github.catvod.bean.Result;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 字符串工具类
 */
public final class s {

    /**
     * 返回空结果字符串
     */
    public static String g() {
        return Result.string(new ArrayList<>());
    }

    /**
     * 拼接两个字符串
     */
    public static String j(String a, String b) {
        StringBuilder sb = new StringBuilder();
        sb.append(a);
        sb.append(b);
        return sb.toString();
    }

    /**
     * 拼接三个字符串
     */
    public static String k(String a, String b, String c) {
        StringBuilder sb = new StringBuilder();
        sb.append(a);
        sb.append(b);
        sb.append(c);
        return sb.toString();
    }

    /**
     * 拼接五个字符串
     */
    public static String l(String a, String b, String c, String d, String e) {
        StringBuilder sb = new StringBuilder(a);
        sb.append(b);
        sb.append(c);
        sb.append(d);
        sb.append(e);
        return sb.toString();
    }

    /**
     * 创建 StringBuilder 并添加字符串
     */
    public static StringBuilder n(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append(str);
        return sb;
    }

    /**
     * 创建 HashMap 并添加一个键值对
     */
    public static HashMap<String, String> p(String key, String value) {
        HashMap<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    /**
     * 创建 HashMap 并添加两个键值对
     */
    public static HashMap<String, String> q(String k1, String v1, String k2, String v2) {
        HashMap<String, String> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}