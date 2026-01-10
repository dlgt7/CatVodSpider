package com.github.catvod.spider;

import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 秀儿影视 2026年1月 最终稳定版
 */
public class Xiuer extends Spider {

    private static final String HOST = "https://www.xiuer.pro";

    @Override
    public String homeContent(boolean filter) {
        try {
            String html = AntiCrawlerEnhancer.get().enhancedGet(HOST, null);
            Document doc = Jsoup.parse(html);

            // 分类提取（去重并增加排序逻辑）
            List<Class> classes = new ArrayList<>();
            Elements cateSelectors = doc.select(".navbar-items a[href*=/show/], .nav-menu a[href*=/show/]");
            for (Element a : cateSelectors) {
                String typeId = a.attr("href").replaceAll(".*/show/|\\.html.*", "").trim();
                String typeName = a.ownText().trim();
                if (typeName.isEmpty() && a.selectFirst("span") != null) {
                    typeName = a.selectFirst("span").ownText().trim();
                }
                if (!typeId.isEmpty() && !typeName.isEmpty() && classes.stream().noneMatch(c -> c.getTypeId().equals(typeId))) {
                    classes.add(new Class(typeId, typeName));
                }
            }

            return Result.string(classes, parseVodList(doc));
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().msg("数据解析失败").string();
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            String url = HOST + "/show/" + tid + "/page/" + pg + ".html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            Document doc = Jsoup.parse(html);
            // 修正分页：秀儿这类站总数通常很大，limit 保持 24 即可
            return Result.get().page(Integer.parseInt(pg), 1000, 24, 24000)
                    .vod(parseVodList(doc)).string();
        } catch (Exception e) {
            return Result.get().msg("分类内容获取失败").string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String url = HOST + "/detail/" + ids.get(0) + ".html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            Document doc = Jsoup.parse(html);

            Vod vod = new Vod();
            vod.setVodId(ids.get(0));
            
            Element titleEl = doc.selectFirst(".module-info-item-title strong");
            vod.setVodName(titleEl != null ? titleEl.ownText().trim() : "");

            Element imgEl = doc.selectFirst(".module-info-poster img");
            if (imgEl != null) {
                vod.setVodPic(fixUrl(firstNonEmpty(imgEl.attr("data-original"), imgEl.attr("data-src"), imgEl.attr("src"))));
            }

            vod.setVodRemarks(doc.select(".module-info-item-title span").text());
            vod.setVodContent(doc.select(".module-info-item-content").text().trim());

            // 播放源：修正了 index 溢出风险
            Elements tabs = doc.select(".module-tab-item");
            Elements playLists = doc.select(".module-play-list");
            List<String> fromList = new ArrayList<>();
            List<String> urlList = new ArrayList<>();

            for (int i = 0; i < Math.min(tabs.size(), playLists.size()); i++) {
                Element tab = tabs.get(i);
                // 解决 tab 内可能嵌套多层 span 的情况
                String from = tab.text().trim();
                if (from.isEmpty()) from = "线路 " + (i + 1);

                List<String> eps = new ArrayList<>();
                for (Element a : playLists.get(i).select("a")) {
                    String epName = a.text().trim();
                    String epUrl = a.attr("href");
                    if (!epUrl.isEmpty()) eps.add(epName + "$" + fixUrl(epUrl));
                }
                if (!eps.isEmpty()) {
                    fromList.add(from);
                    urlList.add(TextUtils.join("#", eps));
                }
            }
            vod.setVodPlayFrom(TextUtils.join("$$$", fromList));
            vod.setVodPlayUrl(TextUtils.join("$$$", urlList));

            return Result.get().vod(vod).string();
        } catch (Exception e) {
            return Result.get().msg("详情页加载失败").string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            // 修复坑3：标准的 MacCMS 搜索路径
            String url = HOST + "/search/" + key + "----------.html";
            String html = AntiCrawlerEnhancer.get().enhancedGet(url, null);
            return Result.string(parseVodList(Jsoup.parse(html)));
        } catch (Exception e) {
            return Result.get().vod(new ArrayList<>()).string();
        }
    }

    // ─────────────── 核心解析邏輯 ───────────────
    private List<Vod> parseVodList(Document doc) {
        List<Vod> list = new ArrayList<>();
        // 坑1/坑6修复：严格按照容器层次定位，并加入“非广告”校验
        Elements modules = doc.select(".module-items");
        
        for (Element module : modules) {
            Elements items = module.select(".module-item");
            if (items.isEmpty()) continue;

            for (Element item : items) {
                Element a = item.selectFirst("a[href*=/detail/]");
                if (a == null) continue;

                String id = a.attr("href").replaceAll(".*/detail/|\\.html.*", "");
                String name = a.attr("title");
                if (TextUtils.isEmpty(name)) {
                    Element img = a.selectFirst("img");
                    name = (img != null) ? img.attr("alt") : "";
                }
                
                Element img = item.selectFirst("img");
                String pic = (img != null) ? firstNonEmpty(img.attr("data-original"), img.attr("data-src"), img.attr("src")) : "";
                String remark = item.select(".module-item-note").text();

                // 只有具备 ID 和 标题的才加入，过滤掉纯图片广告
                if (!id.isEmpty() && !name.isEmpty()) {
                    list.add(new Vod(id, name, fixUrl(pic), remark));
                }
            }
            // 如果这个 module-items 已经抓到了数据，且超过 4 个（排除只有1个推荐位的干扰），则视为有效主列表
            if (list.size() > 4) break; 
        }
        return list;
    }

    private String firstNonEmpty(String... args) {
        for (String s : args) if (!TextUtils.isEmpty(s)) return s;
        return "";
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return HOST + url;
        return url;
    }
}
