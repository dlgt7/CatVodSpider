package com.github.catvod.spider;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.UA;

/**
 * WebView 预热工具
 * 用于在后台静默通过 JavaScript 挑战，并将生成的 Cookie 存入系统的 CookieManager
 */
public class WebViewHelper {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    public static void warmup(String url, Runnable onFinish) {
        // WebView 必须在主线程创建和调用
        mainHandler.post(() -> {
            try {
                WebView webView = new WebView(Init.context());
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setDatabaseEnabled(true);
                settings.setUserAgentString(UA.DEFAULT); // 保持与 OkHttp 请求一致的 UA

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        // 预留 5 秒时间让 JS 挑战完成（如 Cloudflare）
                        mainHandler.postDelayed(() -> {
                            SpiderDebug.log("WebView 预热/挑战完成: " + url);
                            if (onFinish != null) onFinish.run();
                            webView.destroy(); 
                        }, 5000);
                    }
                });
                webView.loadUrl(url);
            } catch (Exception e) {
                SpiderDebug.log("WebViewHelper Error: " + e.getMessage());
            }
        });
    }
}
