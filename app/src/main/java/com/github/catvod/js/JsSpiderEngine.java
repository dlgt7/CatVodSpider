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

public class JsSpiderEngine {

    private final QuickJSContext ctx;
    private final Pattern URL_RE = Pattern.compile("url\\((.*?)\\)", Pattern.MULTILINE | Pattern.DOTALL);
    private final Pattern NO_ADD = Pattern.compile(":eq|:lt|:gt|:first|:last|:not|:even|:odd|:has|:contains|:matches|:empty|^body$|^#");
    private final Pattern JOIN_URL = Pattern.compile("(url|src|href|-original|-src|-play|-url|style)$|^(data-|url-|src-)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private final Pattern SPEC_URL = Pattern.compile("^(ftp|magnet|thunder|ws):", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private final Map<Integer, Document> docCache = new LinkedHashMap<Integer, Document>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry eldest) { return size() > 10; }
    };

    public JsSpiderEngine(QuickJSContext ctx) {
        this.ctx = ctx;
        registerFunctions();
    }

    private void registerFunctions() {
        ctx.getGlobalObject().setProperty("pdfh", args -> safeCall(() -> pdfh(args[0].toString(), args[1].toString())));
        ctx.getGlobalObject().setProperty("pdfa", args -> safeCall(() -> pdfa(args[0].toString(), args[1].toString())));
        ctx.getGlobalObject().setProperty("pd",   args -> safeCall(() -> pd(args[0].toString(), args[1].toString(), args.length > 2 ? args[2].toString() : "")));
        ctx.getGlobalObject().setProperty("pdfl", args -> safeCall(() -> pdfl(args[0].toString(), args[1].toString(), args[2].toString(), args[3].toString(), args.length > 4 ? args[4].toString() : "")));
        ctx.getGlobalObject().setProperty("request", args -> safeCall(() -> request(args)));
    }

    private String pdfh(String html, String rule) {
        return parseDomForUrl(html, rule, "");
    }

    private JSArray pdfa(String html, String rule) {
        List<String> items = parseDomForArray(html, rule);
        JSArray array = ctx.createNewJSArray();
        for (int i = 0; i < items.size(); i++) array.set(items.get(i), i);
        return array;
    }

    private String pd(String html, String rule, String baseUrl) {
        return parseDomForUrl(html, rule, baseUrl);
    }

    private JSArray pdfl(String html, String rule, String texts, String urls, String baseUrl) {
        List<String> results = parseDomForList(html, rule, texts, urls, baseUrl);
        JSArray array = ctx.createNewJSArray();
        for (int i = 0; i < results.size(); i++) array.set(results.get(i), i);
        return array;
    }

    private String parseDomForUrl(String html, String rule, String baseUrl) {
        Document doc = getCachedDoc(html);
        if (rule.equalsIgnoreCase("body&&Text") || rule.equalsIgnoreCase("Text")) return doc.text();
        if (rule.equalsIgnoreCase("body&&Html") || rule.equalsIgnoreCase("Html")) return doc.html();

        String option = "";
        if (rule.contains("&&")) {
            int lastIndex = rule.lastIndexOf("&&");
            option = rule.substring(lastIndex + 2);
            rule = rule.substring(0, lastIndex);
        }

        rule = parseHikerToJq(rule, true);
        Elements elements = selectElements(doc, rule);
        if (elements.isEmpty()) return "";
        if (option.isEmpty()) return elements.outerHtml();
        if (option.equalsIgnoreCase("Text")) return elements.text().trim();
        if (option.equalsIgnoreCase("Html")) return elements.html();

        String result = "";
        for (String opt : option.split("\\|\\|")) {
            result = elements.attr(opt);
            if (opt.toLowerCase().contains("style") && result.contains("url(")) {
                Matcher m = URL_RE.matcher(result);
                if (m.find()) result = m.group(1).replaceAll("^['\"](.*)['\"]$", "$1");
            }
            if (!result.isEmpty() && !baseUrl.isEmpty()) {
                if (JOIN_URL.matcher(opt).find() && !SPEC_URL.matcher(result).find()) {
                    result = result.contains("http") ? result.substring(result.indexOf("http")) : UriUtil.resolve(baseUrl, result);
                }
            }
            if (!result.isEmpty()) break;
        }
        return result.trim();
    }

    private List<String> parseDomForArray(String html, String rule) {
        rule = parseHikerToJq(rule, false);
        Elements elements = selectElements(getCachedDoc(html), rule);
        List<String> list = new ArrayList<>();
        for (Element e : elements) list.add(e.outerHtml());
        return list;
    }

    private List<String> parseDomForList(String html, String rule, String texts, String urls, String baseUrl) {
        rule = parseHikerToJq(rule, false);
        Elements elements = selectElements(getCachedDoc(html), rule);
        List<String> items = new ArrayList<>();
        for (Element e : elements) {
            String itemHtml = e.outerHtml();
            items.add(parseDomForUrl(itemHtml, texts, "").trim() + "$" + parseDomForUrl(itemHtml, urls, baseUrl));
        }
        return items;
    }

    private String parseHikerToJq(String parse, boolean first) {
        String[] parses = parse.split("&&");
        List<String> items = new ArrayList<>();
        for (int i = 0; i < parses.length; i++) {
            String str = parses[i].trim();
            if (NO_ADD.matcher(str).find()) {
                items.add(str);
            } else {
                if (!first && i >= parses.length - 1) items.add(str);
                else items.add(str + ":eq(0)");
            }
        }
        return String.join(" ", items);
    }

    private Elements selectElements(Document doc, String rule) {
        Elements elements = new Elements(doc);
        for (String part : rule.split(" ")) {
            String cleanRule = part;
            int index = 0;
            if (part.contains(":eq(")) {
                cleanRule = part.substring(0, part.indexOf(":eq("));
                index = Integer.parseInt(part.substring(part.indexOf("(") + 1, part.indexOf(")")));
            }
            String selectRule = cleanRule;
            List<String> excludes = new ArrayList<>();
            if (cleanRule.contains("--")) {
                String[] split = cleanRule.split("--");
                selectRule = split[0];
                excludes.addAll(Arrays.asList(split).subList(1, split.length));
            }
            elements = (elements.isEmpty()) ? doc.select(selectRule) : elements.select(selectRule);
            if (part.contains(":eq(")) {
                int realIdx = index < 0 ? elements.size() + index : index;
                elements = (realIdx >= 0 && realIdx < elements.size()) ? new Elements(elements.get(realIdx)) : new Elements();
            }
            for (String ex : excludes) elements.select(ex).remove();
        }
        return elements;
    }

    private Object request(Object[] args) throws Exception {
        String url = args[0].toString();
        Map<String, String> headers = new HashMap<>();
        String method = "GET";
        String body = null;

        if (args.length > 1 && args[1] instanceof JSObject) {
            JSObject opt = (JSObject) args[1];
            Object m = opt.getProperty("method");
            if (m != null) method = m.toString().toUpperCase();
            Object b = opt.getProperty("body");
            if (b != null) body = b.toString();
            
            JSObject jsHeaders = (JSObject) opt.getProperty("headers");
            if (jsHeaders != null) {
                JSArray keys = jsHeaders.getNames();
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.get(i).toString();
                    headers.put(key, jsHeaders.getProperty(key).toString());
                }
            }
        }
        return OkHttpUtil.execute(url, method, headers, body);
    }

    private Document getCachedDoc(String html) {
        return docCache.computeIfAbsent(html.hashCode(), k -> Jsoup.parse(html));
    }

    private Object safeCall(CallableAction action) {
        try { return action.call(); } catch (Exception e) { return ""; }
    }

    interface CallableAction { Object call() throws Exception; }
}
