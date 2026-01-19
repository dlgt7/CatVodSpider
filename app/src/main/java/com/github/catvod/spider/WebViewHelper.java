package com.github.catvod.spider;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.UA;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * WebView 预热工具
 * 用于在后台静默通过 JavaScript 挑战，并将生成的 Cookie 存入系统的 CookieManager
 */
public class WebViewHelper {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // WebView 指纹抹除 JavaScript 脚本，增强版本
    private static final String FINGERPRINT_MASK_SCRIPT = 
        "(function() {" +
        "  Object.defineProperty(navigator, 'webdriver', { get: () => undefined });" +
        "  Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5]; });" +
        "  Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh']; });" +
        "  Object.defineProperty(navigator, 'userAgent', { get: () => '" + UA.getRandom() + "'; });" +
        "  if (window.chrome) {" +
        "    window.chrome.runtime = { sendMessage: () => {}, connect: () => {} };" +
        "  }" +
        "  Object.defineProperty(navigator, 'vendor', { get: () => 'Google Inc.' });" +
        "  const originalKeys = Object.keys(window);" +
        "  const keysToCheck = ['external', 'webkitStorageInfo', 'chrome', '_phantom', '__nightmare', 'callPhantom'];" +
        "  keysToCheck.forEach(key => {" +
        "    if (key in window && window[key] !== null) {" +
        "      try { delete window[key]; } catch(e) {}" +
        "    }" +
        "  });" +
        "  // 增强版Canvas指纹抹除" +
        "  const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;" +
        "  HTMLCanvasElement.prototype.toDataURL = function() {" +
        "    if (arguments.length === 0) {" +
        "      return 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==';" +
        "    }" +
        "    return originalToDataURL.apply(this, arguments);" +
        "  };" +
        "  // 抹除时间区域信息" +
        "  const originalDateTimeFormat = Intl.DateTimeFormat;" +
        "  Intl.DateTimeFormat = function(locales, options) {" +
        "    if (locales === undefined) locales = 'zh-CN';" +
        "    if (options === undefined) options = {};" +
        "    return new originalDateTimeFormat(locales, options);" +
        "  };" +
        "  // 拦截navigator.webdriver同步检测" +
        "  const getParameter = WebGLRenderingContext.prototype.getParameter;" +
        "  WebGLRenderingContext.prototype.getParameter = function(parameter) {" +
        "    if (parameter === 37445) return 'Intel Open Source Technology Center';" +
        "    if (parameter === 7936) return 'WebKit';" +
        "    return getParameter.call(this, parameter);" +
        "  };" +
        "})();";

    /**
     * 使用默认UA的基本预热方法
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static void warmup(String url, Runnable onFinish) {
        warmupWithSessionUA(url, UA.getRandom(), onFinish);
    }

    /**
     * 使用指定UA的预热方法
     * @param url 目标URL
     * @param sessionUA 会话级UA
     * @param onFinish 完成回调
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static void warmupWithSessionUA(String url, String sessionUA, Runnable onFinish) {
        // WebView 必须在主线程创建和调用
        mainHandler.post(() -> {
            WebView webView = null;
            try {
                webView = new WebView(Init.context());
                // 将WebView添加到临时容器以确保正确销毁
                ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                );
                webView.setLayoutParams(layoutParams);
                
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setDatabaseEnabled(true);
                settings.setUserAgentString(sessionUA); // 使用会话级UA确保一致性
                settings.setCacheMode(WebSettings.LOAD_DEFAULT);
                settings.setLoadWithOverviewMode(true);
                settings.setUseWideViewPort(true);
                settings.setDomStorageEnabled(true);
                settings.setAppCacheEnabled(true);
                settings.setGeolocationEnabled(false); // 禁用地理位置以避免权限请求
                settings.setSaveFormData(false);

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);
                        // 页面开始时注入指纹抹除脚本
                        view.evaluateJavascript(FINGERPRINT_MASK_SCRIPT, null);
                    }
                    
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        // 模拟人类交互 - 轻微滚动
                        view.evaluateJavascript("window.scrollTo(0, Math.floor(Math.random() * 100));", null);
                        
                        // 预留时间让 JS 挑战完成（如 Cloudflare）
                        mainHandler.postDelayed(() -> {
                            SpiderDebug.log("WebView 预热/挑战完成: " + url);
                            if (onFinish != null) onFinish.run();
                            cleanupWebView(view);
                        }, 5000);
                    }
                    
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        // 允许页面内跳转
                        return false;
                    }
                    
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        
                        // 检查是否为主HTML页面，如果是则进行脚本注入
                        if (isMainHtmlPage(url)) {
                            return interceptAndInjectScript(request, FINGERPRINT_MASK_SCRIPT);
                        }
                        
                        // 拦截广告和统计请求以加速加载
                        if (isAdOrTrackingUrl(url)) {
                            SpiderDebug.log("拦截广告/追踪请求: " + url);
                            return createEmptyResponse();
                        }
                        
                        // 拦截资源密集型请求
                        if (isResourceIntensiveUrl(url)) {
                            SpiderDebug.log("拦截资源密集型请求: " + url);
                            return createEmptyResponse();
                        }
                        
                        return super.shouldInterceptRequest(view, request);
                    }
                });
                
                // 添加JavaScript接口用于动态注入脚本
                webView.addJavascriptInterface(new JsBridge(), "Android");
                
                webView.loadUrl(url);
            } catch (Exception e) {
                SpiderDebug.log("WebViewHelper Error: " + e.getMessage());
                if (webView != null) {
                    cleanupWebView(webView);
                }
            }
        });
    }

    /**
     * 带自定义脚本注入的预热方法（使用默认UA）
     * @param url 目标URL
     * @param script 额外的JavaScript脚本
     * @param onFinish 完成回调
     */
    public static void warmupWithScript(String url, String script, Runnable onFinish) {
        warmupWithScriptAndSessionUA(url, script, UA.getRandom(), onFinish);
    }

    /**
     * 带自定义脚本注入的预热方法（使用指定UA）
     * @param url 目标URL
     * @param script 额外的JavaScript脚本
     * @param sessionUA 会话级UA
     * @param onFinish 完成回调
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static void warmupWithScriptAndSessionUA(String url, String script, String sessionUA, Runnable onFinish) {
        mainHandler.post(() -> {
            WebView webView = null;
            try {
                webView = new WebView(Init.context());
                // 将WebView添加到临时容器以确保正确销毁
                ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                );
                webView.setLayoutParams(layoutParams);
                
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setDatabaseEnabled(true);
                settings.setUserAgentString(sessionUA); // 使用会话级UA确保一致性
                settings.setCacheMode(WebSettings.LOAD_DEFAULT);
                settings.setLoadWithOverviewMode(true);
                settings.setUseWideViewPort(true);
                settings.setDomStorageEnabled(true);

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);
                        SpiderDebug.log("WebView 开始加载: " + url);
                        // 注入指纹抹除脚本
                        view.evaluateJavascript(FINGERPRINT_MASK_SCRIPT, null);
                        // 页面开始加载时注入用户脚本
                        if (script != null && !script.trim().isEmpty()) {
                            view.evaluateJavascript(script, null);
                        }
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        SpiderDebug.log("WebView 页面加载完成: " + url);
                        // 页面加载完成后再次尝试注入脚本
                        if (script != null && !script.trim().isEmpty()) {
                            mainHandler.postDelayed(() -> {
                                view.evaluateJavascript(script, null);
                                // 再次注入指纹抹除脚本以确保生效
                                view.evaluateJavascript(FINGERPRINT_MASK_SCRIPT, null);
                            }, 1000);
                        }
                        
                        // 模拟人类交互 - 轻微滚动
                        view.evaluateJavascript("window.scrollTo(0, Math.floor(Math.random() * 100));", null);
                        
                        // 预留时间让 JS 挑战完成
                        mainHandler.postDelayed(() -> {
                            SpiderDebug.log("WebView 预热/挑战完成: " + url);
                            if (onFinish != null) onFinish.run();
                            cleanupWebView(view);
                        }, 5000);
                    }
                    
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        // 允许页面内跳转
                        return false;
                    }
                    
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        
                        // 检查是否为主HTML页面，如果是则进行脚本注入
                        if (isMainHtmlPage(url)) {
                            String combinedScript = FINGERPRINT_MASK_SCRIPT + 
                                (script != null && !script.trim().isEmpty() ? script : "");
                            return interceptAndInjectScript(request, combinedScript);
                        }
                        
                        // 拦截广告和统计请求以加速加载
                        if (isAdOrTrackingUrl(url)) {
                            SpiderDebug.log("拦截广告/追踪请求: " + url);
                            return createEmptyResponse();
                        }
                        
                        // 拦截资源密集型请求
                        if (isResourceIntensiveUrl(url)) {
                            SpiderDebug.log("拦截资源密集型请求: " + url);
                            return createEmptyResponse();
                        }
                        
                        return super.shouldInterceptRequest(view, request);
                    }
                });
                
                webView.addJavascriptInterface(new JsBridge(), "Android");
                
                webView.loadUrl(url);
            } catch (Exception e) {
                SpiderDebug.log("WebViewHelper with Script Error: " + e.getMessage());
                if (webView != null) {
                    cleanupWebView(webView);
                }
            }
        });
    }

    /**
     * 带高级选项的预热方法（使用默认UA）
     * @param url 目标URL
     * @param script 额外的JavaScript脚本
     * @param delay 延迟时间（毫秒）
     * @param onFinish 完成回调
     */
    public static void warmupAdvanced(String url, String script, long delay, Runnable onFinish) {
        warmupAdvancedWithSessionUA(url, script, delay, UA.getRandom(), onFinish);
    }

    /**
     * 带高级选项的预热方法（使用指定UA）
     * @param url 目标URL
     * @param script 额外的JavaScript脚本
     * @param delay 延迟时间（毫秒）
     * @param sessionUA 会话级UA
     * @param onFinish 完成回调
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static void warmupAdvancedWithSessionUA(String url, String script, long delay, String sessionUA, Runnable onFinish) {
        mainHandler.post(() -> {
            WebView webView = null;
            try {
                webView = new WebView(Init.context());
                // 将WebView添加到临时容器以确保正确销毁
                ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                );
                webView.setLayoutParams(layoutParams);
                
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setDatabaseEnabled(true);
                settings.setUserAgentString(sessionUA); // 使用会话级UA确保一致性
                settings.setCacheMode(WebSettings.LOAD_DEFAULT);
                settings.setLoadWithOverviewMode(true);
                settings.setUseWideViewPort(true);
                settings.setDomStorageEnabled(true);
                settings.setAppCacheEnabled(true);
                settings.setGeolocationEnabled(false); // 禁用地理位置以避免权限请求
                settings.setSaveFormData(false);

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);
                        SpiderDebug.log("WebView 开始加载: " + url);
                        // 注入指纹抹除脚本
                        view.evaluateJavascript(FINGERPRINT_MASK_SCRIPT, null);
                        // 页面开始加载时注入用户脚本
                        if (script != null && !script.trim().isEmpty()) {
                            view.evaluateJavascript(script, null);
                        }
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        SpiderDebug.log("WebView 页面加载完成: " + url);
                        // 页面加载完成后再次尝试注入脚本
                        if (script != null && !script.trim().isEmpty()) {
                            mainHandler.postDelayed(() -> {
                                view.evaluateJavascript(script, null);
                                // 再次注入指纹抹除脚本以确保生效
                                view.evaluateJavascript(FINGERPRINT_MASK_SCRIPT, null);
                            }, 1000);
                        }
                        
                        // 模拟人类交互 - 轻微滚动
                        view.evaluateJavascript("window.scrollTo(0, Math.floor(Math.random() * 100));", null);
                        
                        // 根据指定延迟时间等待
                        long finalDelay = Math.max(delay, 3000); // 至少等待3秒
                        mainHandler.postDelayed(() -> {
                            SpiderDebug.log("WebView 预热/挑战完成: " + url);
                            if (onFinish != null) onFinish.run();
                            cleanupWebView(view);
                        }, finalDelay);
                    }
                    
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        // 允许页面内跳转
                        SpiderDebug.log("WebView URL跳转: " + request.getUrl().toString());
                        return false;
                    }
                    
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        
                        // 检查是否为主HTML页面，如果是则进行脚本注入
                        if (isMainHtmlPage(url)) {
                            String combinedScript = FINGERPRINT_MASK_SCRIPT + 
                                (script != null && !script.trim().isEmpty() ? script : "");
                            return interceptAndInjectScript(request, combinedScript);
                        }
                        
                        // 拦截广告和统计请求以加速加载
                        if (isAdOrTrackingUrl(url)) {
                            SpiderDebug.log("拦截广告/追踪请求: " + url);
                            return createEmptyResponse();
                        }
                        
                        // 拦截资源密集型请求
                        if (isResourceIntensiveUrl(url)) {
                            SpiderDebug.log("拦截资源密集型请求: " + url);
                            return createEmptyResponse();
                        }
                        
                        return super.shouldInterceptRequest(view, request);
                    }
                });
                
                webView.addJavascriptInterface(new JsBridge(), "Android");
                
                webView.loadUrl(url);
            } catch (Exception e) {
                SpiderDebug.log("WebViewHelper Advanced Error: " + e.getMessage());
                if (webView != null) {
                    cleanupWebView(webView);
                }
            }
        });
    }

    /**
     * 判断是否为主HTML页面
     * @param url URL地址
     * @return 是否为主HTML页面
     */
    private static boolean isMainHtmlPage(String url) {
        return url != null && !url.toLowerCase().contains(".js") && 
               !url.toLowerCase().contains(".css") && 
               !url.toLowerCase().contains(".png") && 
               !url.toLowerCase().contains(".jpg") && 
               !url.toLowerCase().contains(".gif") &&
               (url.toLowerCase().contains(".html") || 
                url.toLowerCase().contains(".htm") || 
                !url.contains(".") || 
                url.endsWith("/"));
    }

    /**
     * 创建空响应以拦截资源
     * @return 空的WebResourceResponse
     */
    private static WebResourceResponse createEmptyResponse() {
        InputStream emptyStream = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        return new WebResourceResponse("text/plain", "utf-8", emptyStream);
    }

    /**
     * 拦截并注入脚本到HTML响应
     * @param request 原始请求
     * @param script 要注入的脚本
     * @return 注入脚本后的响应
     */
    private static WebResourceResponse interceptAndInjectScript(WebResourceRequest request, String script) {
        try {
            // 创建包含注入脚本的HTML
            String injectedHtml = "<!DOCTYPE html>" +
                "<html><head>" +
                "<script>" + script + "</script>" +
                "</head><body></body></html>";
            
            InputStream inputStream = new ByteArrayInputStream(injectedHtml.getBytes(StandardCharsets.UTF_8));
            return new WebResourceResponse("text/html", "utf-8", inputStream);
        } catch (Exception e) {
            SpiderDebug.log("Script injection failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * WebView 清理方法，防止内存泄露
     * @param webView 要清理的WebView
     */
    private static void cleanupWebView(WebView webView) {
        if (webView != null) {
            try {
                // 停止加载
                webView.stopLoading();
                // 清除历史记录
                webView.clearHistory();
                // 清除缓存
                webView.clearCache(true);
                // 加载空白页以停止所有JS执行
                webView.loadUrl("about:blank");
                // 移除所有回调和引用
                webView.onPause();
                
                // 从父容器移除（如果存在）
                ViewGroup parent = (ViewGroup) webView.getParent();
                if (parent != null) {
                    parent.removeView(webView);
                }
                
                // 移除所有视图
                webView.removeAllViews();
                // 销毁
                webView.destroy();
            } catch (Exception e) {
                SpiderDebug.log("WebView cleanup error: " + e.getMessage());
            }
        }
    }

    /**
     * 判断是否为广告或追踪URL
     * @param url 待检查的URL
     * @return 如果是广告或追踪URL返回true
     */
    private static boolean isAdOrTrackingUrl(String url) {
        String lowerUrl = url.toLowerCase();
        String[] adTrackPatterns = {
            "googleads", "googlesyndication", "doubleclick", "facebook.com/tr", 
            "analytics", "stat", "track", "pixel", "beacon", "monitor", 
            "adserver", "adsystem", "advertisement", "affiliate", "tagmanager",
            "google-analytics.com", "doubleclick.net", "googletagmanager.com",
            "facebook.net", "fbcdn.net", "googlesyndication.com", "amazon-adsystem.com",
            "mathtag.com", "rubiconproject.com", "pubmatic.com", "openx.net",
            "indexexchange.com", "casalemedia.com", "lijit.com", "taboola.com",
            "outbrain.com", "revcontent.com", "adroll.com", "bidswitch.net"
        };
        
        for (String pattern : adTrackPatterns) {
            if (lowerUrl.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为资源密集型URL（如图片、CSS、字体等）
     * @param url 待检查的URL
     * @return 如果是资源密集型URL返回true
     */
    private static boolean isResourceIntensiveUrl(String url) {
        String lowerUrl = url.toLowerCase();
        String[] resourcePatterns = {
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".ico", // 图片
            ".css", ".woff", ".woff2", ".ttf", ".eot", ".otf", // 样式和字体
            ".mp4", ".avi", ".mov", ".flv", ".webm", ".mkv", // 视频
            ".mp3", ".wav", ".ogg", ".aac", ".flac", // 音频
            ".pdf", ".zip", ".rar", ".tar", ".gz", ".7z", // 文档和压缩包
            ".m3u8", ".ts", ".m3u", ".mpd", ".f4m", // 流媒体格式
            ".swf", ".fla", ".flv", // Flash文件
            ".xml", ".rss", ".atom", // XML相关
            ".exe", ".dmg", ".pkg", ".deb", ".rpm", // 可执行文件
            ".iso", ".img", ".bin", // 镜像文件
            ".jar", ".war", ".ear", // Java归档文件
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".odt", ".ods", ".odp" // 办公文档
        };
        
        for (String pattern : resourcePatterns) {
            if (lowerUrl.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * JavaScript桥接类，用于动态交互
     */
    public static class JsBridge {
        @JavascriptInterface
        public void log(String message) {
            SpiderDebug.log("WebView JS: " + message);
        }

        @JavascriptInterface
        public void callback(String data) {
            SpiderDebug.log("WebView Callback: " + data);
        }
        
        @JavascriptInterface
        public String getUserAgent() {
            return UA.getRandom();
        }
        
        @JavascriptInterface
        public void injectCustomScript(String script) {
            SpiderDebug.log("WebView 注入自定义脚本: " + script);
        }
    }
}