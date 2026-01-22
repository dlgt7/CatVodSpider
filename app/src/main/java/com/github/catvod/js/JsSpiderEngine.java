package com.github.catvod.js;

import com.github.catvod.utils.OkHttpUtil;
import com.github.catvod.utils.UriUtil;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 终极 JS 爬虫运行时：整合了解析引擎、网络请求与内存缓存
 */
public class JsSpiderRuntime {

    private final QuickJSContext ctx;
    private final Pattern URL_IN_STYLE = Pattern.compile("url\\(['\"]?(.*?)['\"]?\\)");
    
    // LRU 缓存：保存最近解析过的 10 个 HTML 文档，避免重复解析
    private final Map<Integer, Document> docCache = new LinkedHashMap<Integer, Document>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry eldest) { return size() > 10; }
    };

    public JsSpiderRuntime(QuickJSContext ctx) {
        this.ctx = ctx;
        registerGlobalFunctions();
    }

    private void registerGlobalFunctions() {
        // 注册核心解析函数
        ctx.getGlobalObject().setProperty("pdfh", args -> safeCall(() -> pdfh(args[0].toString(), args[1].toString())));
        ctx.getGlobalObject().setProperty("pdfa", args -> safeCall(() -> pdfa(args[0].toString(), args[1].toString())));
        ctx.getGlobalObject().setProperty("pd",   args -> safeCall(() -> pd(args[0].toString(), args[1].toString(), args.length > 2 ? args[2].toString() : "")));
        ctx.getGlobalObject().setProperty("pdfl", args -> safeCall(() -> pdfl(args[0].toString(), args[1].toString(), args[2].toString(), args[3].toString(), args.length > 4 ? args[4].toString() : "")));
        
        // 注册增强网络请求函数 (方案 B)
        ctx.getGlobalObject().setProperty("request", args -> safeCall(() -> request(args)));
    }

    // --- 核心解析逻辑 (重写自原 Parser.java) ---

    private String pdfh(String html, String rule) {
        Elements els = query(html, rule);
        return extract(els, getOption(rule));
    }

    private JSArray pdfa(String html, String rule) {
        Elements els = query(html, rule);
        List<String> list = new ArrayList<>();
        for (Element e : els) list.add(e.outerHtml());
        return toJSArray(list);
    }

    private String pd(String html, String rule, String baseUrl) {
        String option = getOption(rule);
        if (option.isEmpty()) option = "href";
        String val = extract(query(html, rule), option);
        return UriUtil.resolve(baseUrl, val);
    }

    private JSArray pdfl(String html, String rule, String textRule, String urlRule, String baseUrl) {
        Elements listEls = query(html, rule);
        List<String> results = new ArrayList<>();
        for (Element el : listEls) {
            String itemHtml = el.outerHtml();
            String text = pdfh(itemHtml, textRule);
            String url = pd(itemHtml, urlRule, baseUrl);
            results.add(text + "$" + url);
        }
        return toJSArray(results);
    }

    // --- 内部处理工具 ---

    private Elements query(String html, String rule) {
        Document doc = getCachedDoc(html);
        String selector = rule.split("&&")[0];
        Elements current = new Elements(doc);

        // 简化的 Hiker 规则处理：支持空格分割的多级选择与 :eq(n)
        for (String part : selector.split(" ")) {
            if (part.contains(":eq(")) {
                String css = part.substring(0, part.indexOf(":eq"));
                int idx = Integer.parseInt(part.substring(part.indexOf("(") + 1, part.indexOf(")")));
                Elements selected = current.select(css);
                int realIdx = idx < 0 ? selected.size() + idx : idx;
                current = (realIdx >= 0 && realIdx < selected.size()) ? new Elements(selected.get(realIdx)) : new Elements();
            } else {
                current = current.select(part);
            }
            if (current.isEmpty()) break;
        }
        return current;
    }

    private String extract(Elements els, String option) {
        if (els.isEmpty()) return "";
        if (option.equalsIgnoreCase("text")) return els.text().trim();
        if (option.equalsIgnoreCase("html")) return els.html();
        if (option.isEmpty()) return els.outerHtml();
        
        String val = els.attr(option);
        if (option.toLowerCase().contains("style") && val.contains("url(")) {
            Matcher m = URL_IN_STYLE.matcher(val);
            if (m.find()) val = m.group(1).replaceAll("^['\"](.*)['\"]$", "$1");
        }
        return val;
    }

    private String getOption(String rule) {
        String[] parts = rule.split("&&");
        return parts.length > 1 ? parts[parts.length - 1] : "";
    }

    private Object request(Object[] args) throws Exception {
        String url = args[0].toString();
        Map<String, String> headers = new HashMap<>();
        String method = "GET";
        String body = null;

        if (args.length > 1 && args[1] instanceof JSObject) {
            JSObject opt = (JSObject) args[1];
            method = opt.getProperty("method").toString().toUpperCase();
            // 注意：此处需要根据具体的 QuickJS 桥接实现来转换 JSObject 内部属性
        }
        return OkHttpUtil.execute(url, method, headers, body);
    }

    private Document getCachedDoc(String html) {
        return docCache.computeIfAbsent(html.hashCode(), k -> Jsoup.parse(html));
    }

    private JSArray toJSArray(List<String> list) {
        JSArray array = ctx.createNewJSArray();
        for (int i = 0; i < list.size(); i++) array.set(list.get(i), i);
        return array;
    }

    private Object safeCall(CallableAction action) {
        try { return action.call(); } 
        catch (Exception e) { return null; }
    }

    interface CallableAction { Object call() throws Exception; }
}
