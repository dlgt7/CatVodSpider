package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 红果短剧 Java 实现
 */
public class Hgdj extends Spider {

    private String xurl = "https://mov.cenguigui.cn"; // [cite: 1]
    private String xurl1 = "https://list.le.com/listn/c69_t-1_d1_y-1_s1_o4"; // [cite: 1]

    private Map<String, String> getHeaderx() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.87 Safari/537.36"); // [cite: 1]
        return headers;
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            List<Class> classes = new ArrayList<>();
            // 解析分类逻辑：从乐视列表页提取
            String res = OkHttp.string(xurl1, getHeaderx()); // 
            Document doc = Jsoup.parse(res);
            // 对应 Python: doc.find_all('ul', class_="valueList")[2]
            Elements valueLists = doc.select("ul.valueList");
            if (valueLists.size() > 2) {
                Elements vods = valueLists.get(2).select("li"); // 
                for (Element vod : vods) {
                    String name = vod.text().trim();
                    if (name.equals("全部")) continue; // [cite: 6]
                
                    classes.add(new Class(name, name)); 
                }
            }
            return Result.string(classes, new ArrayList<>(), new HashMap<>());
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeVideoContent() {
        // 对应 Python: f'{xurl}/duanju/api.php?name=全部&page=1' [cite: 6]
        return categoryContent("全部", "1", false, null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            String page = (pg == null || pg.isEmpty()) ? "1" : pg; // [cite: 7]
            String url = xurl + "/duanju/api.php?name=" + tid + "&page=" + page; // 
            
            String json = OkHttp.string(url, getHeaderx());
            JSONObject data = new JSONObject(json);
            JSONArray vodList = data.getJSONArray("data"); // [cite: 3]
            
            List<Vod> list = new ArrayList<>();
            for (int i = 0; i < vodList.length(); i++) {
                JSONObject vodObj = vodList.getJSONObject(i);
                Vod vod = new Vod();
                vod.setVodId(vodObj.getString("book_id")); // [cite: 3]
                vod.setVodName(vodObj.getString("title")); // [cite: 3]
                vod.setVodPic(vodObj.getString("cover")); // [cite: 3]
               
                vod.setVodRemarks(vodObj.optString("type")); // [cite: 3]
                list.add(vod);
            }
            // 参数还原：limit 90, total 999999 
            return Result.string(page, "9999", 90, 999999, list);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String did = ids.get(0);
            String url = xurl + "/duanju/api.php?book_id=" + did; // [cite: 9]
            String json = OkHttp.string(url, getHeaderx());
            JSONObject data = new JSONObject(json);
            
            Vod vod = new Vod();
            vod.setVodId(did); // [cite: 11]
            vod.setVodName(data.optString("title")); // 标题还原
            vod.setVodPic(data.optString("cover")); // 封面还原
            vod.setVodActor(data.optString("author", "未知")); // [cite: 9]
            vod.setVodRemarks(data.optString("category", "未知")); // [cite: 10]
            vod.setVodYear(data.optString("duration", "未知")); // [cite: 10]
            
            vod.setVodContent(data.optString("desc", "未知")); // [cite: 9]
            
            vod.setVodPlayFrom("短剧专线"); // [cite: 11]
            
            JSONArray episodes = data.getJSONArray("data"); // [cite: 10]
            List<String> playList = new ArrayList<>();
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject ep = episodes.getJSONObject(i);
                String name = ep.getString("title"); // [cite: 10]
                String id = ep.getString("video_id"); // [cite: 10]
                playList.add(name + "$" + id); // [cite: 10]
            }
            vod.setVodPlayUrl(android.text.TextUtils.join("#", playList)); // [cite: 10]
            
            return Result.string(vod);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        // Python searchContent 调用 searchContentPage，本质也是调 api.php?name={key} [cite: 14, 15]
        return categoryContent(key, "1", false, null);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String url = xurl + "/duanju/api.php?video_id=" + id; // [cite: 12]
            String json = OkHttp.string(url, getHeaderx());
            JSONObject data = new JSONObject(json);
            String videoUrl = data.getJSONObject("data").getString("url"); // [cite: 12]
            
            return Result.get()
                .url(videoUrl) // [cite: 13]
                .header(getHeaderx()) // [cite: 13]
                .parse(0) // [cite: 13]
                .string();
        } catch (Exception e) {
            return "";
        }
    }
}
