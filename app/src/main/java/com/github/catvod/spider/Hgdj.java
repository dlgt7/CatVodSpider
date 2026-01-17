package com.github.catvod.spider;

import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
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

public class Hgdj extends Spider {

    // 对应 py 里的 xurl 和 xurl1
    private String xurl = "https://mov.cenguigui.cn";
    private String xurl1 = "https://list.le.com/listn/c69_t-1_d1_y-1_s1_o4";

    // 对应 py 里的 headerx
    private Map<String, String> getHeaderx() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.87 Safari/537.36");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            List<Class> classes = new ArrayList<>();
            // 还原 py 逻辑：解析乐视网短剧分类
            String res = OkHttp.string(xurl1, getHeaderx());
            Document doc = Jsoup.parse(res);
            Elements valueLists = doc.select("ul.valueList");
            if (valueLists.size() > 2) {
                Elements vods = valueLists.get(2).select("li");
                for (Element vod : vods) {
                    String name = vod.text().trim();
                    if (name.equals("全部")) continue;
                    // 直接还原分类名
                    classes.add(new Class(name, name));
                }
            }
            // 修复编译错误：Result.string 静态方法需要确定的对象类型
            return Result.string(classes, new ArrayList<Vod>(), new JSONObject());
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeVideoContent() {
        // 还原 py 逻辑：默认加载“全部”第一页
        return categoryContent("全部", "1", false, null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            String pageStr = (pg == null || pg.isEmpty()) ? "1" : pg;
            int page = Integer.parseInt(pageStr);
            // 还原请求地址：api.php?name={key}&page={page}
            String url = xurl + "/duanju/api.php?name=" + tid + "&page=" + page;
            
            String json = OkHttp.string(url, getHeaderx());
            JSONObject data = new JSONObject(json);
            JSONArray vodList = data.getJSONArray("data");
            
            List<Vod> list = new ArrayList<>();
            for (int i = 0; i < vodList.length(); i++) {
                JSONObject vodObj = vodList.getJSONObject(i);
                Vod vod = new Vod();
                // 还原 py 参数映射
                vod.setVodId(vodObj.getString("book_id")); 
                vod.setVodName(vodObj.getString("title"));
                vod.setVodPic(vodObj.getString("cover"));
                vod.setVodRemarks(vodObj.optString("type")); 
                list.add(vod);
            }
            // 修复编译错误：使用 Result.java 的链式调用 page(page, count, limit, total)
            // 还原 py 的参数：pagecount=9999, limit=90, total=999999
            return Result.get().page(page, 9999, 90, 999999).list(list).string();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String did = ids.get(0);
            // 还原 py 接口：api.php?book_id={id}
            String url = xurl + "/duanju/api.php?book_id=" + did;
            String json = OkHttp.string(url, getHeaderx());
            JSONObject data = new JSONObject(json);
            
            Vod vod = new Vod();
            vod.setVodId(did);
            vod.setVodName(data.optString("title"));
            vod.setVodPic(data.optString("cover"));
            vod.setVodActor(data.optString("author", "未知"));
            vod.setVodRemarks(data.optString("category", "未知"));
            vod.setVodYear(data.optString("duration", "未知"));
            vod.setVodContent(data.optString("desc", "未知")); // 去除了“介绍剧情”前缀
            
            vod.setVodPlayFrom("短剧专线");
            
            // 剧集列表解析
            JSONArray episodes = data.getJSONArray("data");
            List<String> playList = new ArrayList<>();
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject ep = episodes.getJSONObject(i);
                // 格式：第1集$video_id
                playList.add(ep.getString("title") + "$" + ep.getString("video_id"));
            }
            vod.setVodPlayUrl(TextUtils.join("#", playList));
            
            return Result.string(vod);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        // 还原 py 逻辑：搜索即调用分类接口，参数为关键词
        return categoryContent(key, "1", false, null);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // 还原 py 接口：api.php?video_id={id}
            String url = xurl + "/duanju/api.php?video_id=" + id;
            String json = OkHttp.string(url, getHeaderx());
            JSONObject data = new JSONObject(json);
            String videoUrl = data.getJSONObject("data").getString("url");
            
            // 还原 py 配置：parse=0, header=headerx
            return Result.get().url(videoUrl).header(getHeaderx()).parse(0).string();
        } catch (Exception e) {
            return "";
        }
    }
}
