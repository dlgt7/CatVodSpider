package com.github.catvod.js;

import com.github.catvod.js.utils.JSUtil;
import com.github.catvod.utils.UriUtil;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSMethod;
import com.whl.quickjs.wrapper.QuickJSContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 核心解析引擎：整合了缓存、DOM解析、规则转换和JS绑定
 */
public class JsSpiderEngine {

    private final QuickJSContext ctx;
    private final Map<Integer, Document> docCache = new LinkedHashMap<Integer, Document>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry eldest) { return size() > 10; }
    };

    private static final Pattern URL_EXTRACT = Pattern.compile("url\\(['\"]?(.*?)['\"]?\\)");
    private static final Pattern JQ_PSEUDO = Pattern.compile(":eq|:lt|:gt|:first|:last");

    public JsSpiderEngine(QuickJSContext ctx) {
        this.ctx = ctx;
        registerFunctions();
    }

    private void registerFunctions() {
        // 自动扫描带 @JSMethod 的方法并注册到 JS 全局
        ctx.getGlobalObject().setProperty("pdfh", args -> pdfh(args[0].toString(), args[1].toString()));
        ctx.getGlobalObject().setProperty("pdfa", args -> pdfa(args[0].toString(), args[1].toString()));
        ctx.getGlobalObject().setProperty("pd",   args -> pd(args[0].toString(), args[1].toString(), args.length > 2 ? args[2].toString() : ""));
        ctx.getGlobalObject().setProperty("pdfl", args -> pdfl(args[0].toString(), args[1].toString(), args[2].toString(), args[3].toString(), args.length > 4 ? args[4].toString() : ""));
    }

    // --- 暴露给 JS 的 API ---

    @JSMethod
    public String pdfh(String html, String rule) {
        Elements els = query(html, rule);
        return extractValue(els, getOption(rule));
    }

    @JSMethod
    public JSArray pdfa(String html, String rule) {
        Elements els = query(html, rule);
        List<String> results = new ArrayList<>();
        for (Element e : els) results.add(e.outerHtml());
        return JSUtil.toArray(ctx, results);
    }

    @JSMethod
    public String pd(String html, String rule, String baseUrl) {
        String option = getOption(rule);
        if (option.isEmpty()) option = "href"; // 默认取链接
        String val = extractValue(query(html, rule), option);
        return UriUtil.resolve(baseUrl, val);
    }

    @JSMethod
    public JSArray pdfl(String html, String rule, String textRule, String urlRule, String baseUrl) {
        Elements listEls = query(html, rule);
        List<String> items = new ArrayList<>();
        for (Element el : listEls) {
            String itemHtml = el.outerHtml();
            String text = pdfh(itemHtml, textRule);
            String url = pd(itemHtml, urlRule, baseUrl);
            items.add(text + "$" + url);
        }
        return JSUtil.toArray(ctx, items);
    }

    // --- 内部核心解析逻辑 ---

    private Elements query(String html, String rule) {
        Document doc = getDoc(html);
        String cleanRule = rule.split("&&")[0]; 
        // 支持 Hiker 风格的多级选择器转换
        String[] steps = cleanRule.split(" ");
        Elements current = new Elements(doc);

        for (String step : steps) {
            if (step.contains(":eq(")) {
                int idx = Integer.parseInt(step.substring(step.indexOf("(") + 1, step.indexOf(")")));
                String css = step.substring(0, step.indexOf(":eq"));
                current = selectWithIndex(current, css, idx);
            } else {
                current = current.select(step);
            }
            if (current.isEmpty()) break;
        }
        return current;
    }

    private Elements selectWithIndex(Elements base, String css, int index) {
        Elements selected = base.select(css);
        if (selected.isEmpty()) return new Elements();
        int realIdx = index < 0 ? selected.size() + index : index;
        return (realIdx >= 0 && realIdx < selected.size()) ? 
                new Elements(selected.get(realIdx)) : new Elements();
    }

    private String extractValue(Elements els, String option) {
        if (els.isEmpty()) return "";
        if (option.equalsIgnoreCase("text")) return els.text();
        if (option.equalsIgnoreCase("html")) return els.html();
        if (option.equalsIgnoreCase("outer")) return els.outerHtml();
        
        String attrVal = els.attr(option);
        // 处理 Style 中的 url()
        if (option.toLowerCase().contains("style") && attrVal.contains("url(")) {
            Matcher m = URL_EXTRACT.matcher(attrVal);
            if (m.find()) return m.group(1);
        }
        return attrVal;
    }

    private String getOption(String rule) {
        String[] parts = rule.split("&&");
        return parts.length > 1 ? parts[parts.length - 1] : "";
    }

    private Document getDoc(String html) {
        int hash = html.hashCode();
        if (docCache.containsKey(hash)) return docCache.get(hash);
        Document doc = Jsoup.parse(html);
        docCache.put(hash, doc);
        return doc;
    }
}
