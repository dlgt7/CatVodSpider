package com.github.catvod.utils;

/**
 * User-Agent工具类
 * 为CatVodSpider项目提供常用的User-Agent字符串
 */
public class UA {
    
    /**
     * Chrome浏览器User-Agent
     */
    public static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    
    /**
     * Firefox浏览器User-Agent
     */
    public static final String FIREFOX = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0";
    
    /**
     * Safari浏览器User-Agent
     */
    public static final String SAFARI = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15";
    
    /**
     * iPhone移动设备User-Agent
     */
    public static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1";
    
    /**
     * iPad平板设备User-Agent
     */
    public static final String IPAD = "Mozilla/5.0 (iPad; CPU OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1";
    
    /**
     * Android移动设备User-Agent
     */
    public static final String ANDROID = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    
    /**
     * 默认User-Agent
     */
    public static final String DEFAULT = CHROME;
    
    /**
     * 获取随机User-Agent
     * @return 随机选择的User-Agent字符串
     */
    public static String getRandom() {
        String[] agents = {CHROME, FIREFOX, SAFARI, IPHONE, IPAD, ANDROID};
        return agents[(int) (Math.random() * agents.length)];
    }
}