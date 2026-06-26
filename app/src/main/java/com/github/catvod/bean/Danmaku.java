package com.github.catvod.bean;

import android.text.TextUtils;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

public class Danmaku {

    @SerializedName("name")
    private String name;

    @SerializedName("url")
    private String url;

    private transient boolean selected;

    public static List<Danmaku> arrayFrom(String str) {
        Type listType = new TypeToken<List<Danmaku>>() {}.getType();
        List<Danmaku> items = Json.fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Danmaku from(String path) {
        Danmaku danmaku = new Danmaku();
        danmaku.setName(path);
        danmaku.setUrl(path);
        return danmaku;
    }

    public static Danmaku empty() {
        return new Danmaku();
    }

    /**
     * 创建B站弹幕对象
     * @param cid B站视频cid
     * @return 已配置好URL的Danmaku对象
     */
    public static Danmaku bili(String cid) {
        Danmaku danmaku = new Danmaku();
        danmaku.setName("B站");
        danmaku.setUrl("https://api.bilibili.com/x/v1/dm/list.so?oid=" + cid);
        return danmaku;
    }

    /**
     * 拉取弹幕原始字节数据（最底层，调用方自行处理解压/解码）
     * 适用于任何弹幕接口
     * @param url 弹幕接口URL
     * @param headers 请求头
     * @return 原始字节数组，失败返回空数组
     */
    public static byte[] fetchBytes(String url, Map<String, String> headers) {
        try {
            byte[] bytes = OkHttp.bytes(url, headers);
            return bytes == null ? new byte[0] : bytes;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    /**
     * 拉取弹幕文本内容（适用于未压缩的JSON/XML/ASS接口，如爱奇艺/优酷/AcFun等）
     * @param url 弹幕接口URL
     * @param headers 请求头
     * @return UTF-8字符串，失败返回空字符串
     */
    public static String fetchText(String url, Map<String, String> headers) {
        byte[] bytes = fetchBytes(url, headers);
        if (bytes.length == 0) return "";
        try {
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 拉取弹幕并解压deflate数据（适用于B站等使用raw deflate压缩的接口）
     * @param url 弹幕接口URL
     * @param headers 请求头
     * @return 解压后的UTF-8字符串，失败返回空字符串
     */
    public static String fetchDeflate(String url, Map<String, String> headers) {
        byte[] bytes = fetchBytes(url, headers);
        return deflateToString(bytes);
    }

    /**
     * 解压raw deflate数据（无zlib头）
     * @param compressed 压缩字节数组
     * @return 解压后的UTF-8字符串，失败返回空字符串
     */
    public static String deflateToString(byte[] compressed) {
        if (compressed == null || compressed.length == 0) return "";
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.length);
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break;
                }
                out.write(buffer, 0, count);
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            inflater.end();
        }
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? getUrl() : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isEmpty() {
        return getUrl().isEmpty();
    }

    public String getRealUrl() {
        String u = getUrl();
        if (u.startsWith("/")) u = "file:/" + u;
        return u;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Danmaku)) return false;
        return getUrl().equals(((Danmaku) obj).getUrl());
    }

    @Override
    public String toString() {
        return Json.toJson(this);
    }
}
