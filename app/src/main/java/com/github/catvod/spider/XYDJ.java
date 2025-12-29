package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 星芽短剧 (XYDJ) - 完整移植原Python版XYDJ.py
 * 已修复编译错误：在dlgt6/CatVodSpider项目中，OkHttp.post返回OkResult，使用 .body() 获取响应字符串
 * 实现动态AES-ECB登录获取 token
 * 若登录失败返回空列表，避免崩溃
 */
public class XYDJ extends Spider {

    private static final String siteUrl = "https://app.whjzjx.cn";
    private static final String loginUrl = "https://u.shytkjgs.com/user/v3/account/login";

    private HashMap<String, String> headers;
    private HashMap<String, String> headerx;

    private void initHeaders() {
        headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 3 XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.101 Mobile Safari/537.36");

        headerx = new HashMap<>();
        headerx.put("platform", "1");
        headerx.put("version_name", "3.8.3.1");
    }

    private boolean getToken() {
        try {
            long times = System.currentTimeMillis();

            JSONObject data = new JSONObject();
            data.put("device", "2a50580e69d38388c94c93605241fb306");
            data.put("package_name", "com.jz.xydj");
            data.put("android_id", "ec1280db12795506");
            data.put("install_first_open", true);
            data.put("first_install_time", 1752505243345L);
            data.put("last_update_time", 1752505243345L);
            data.put("report_link_url", "");
            data.put("authorization", "");
            data.put("timestamp", times);

            String plainText = data.toString();

            String keyStr = "B@ecf920Od8A4df7";
            byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
            byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(plainBytes);
            String encrypted = Base64.getEncoder().encodeToString(encryptedBytes);

            HashMap<String, String> loginHeaders = new HashMap<>();
            loginHeaders.put("platform", "1");
            loginHeaders.put("user_agent", "Mozilla/5.0 (Linux; Android 9; V1938T Build/PQ3A.190705.08211809; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36");
            loginHeaders.put("content-type", "application/json; charset=utf-8");

            // 修复：使用 .body() 获取响应体字符串
            String resp = OkHttp.post(loginUrl, encrypted, loginHeaders).body();

            JSONObject json = new JSONObject(resp);
            if (json.has("data") && json.getJSONObject("data").has("token")) {
                String token = json.getJSONObject("data").optString("token");
                headerx.put("authorization", token);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void init(Context context, String extend) {
        initHeaders();
        getToken();
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();

            classes.put(new JSONObject().put("type_id", "1").put("type_name", "剧场"));
            classes.put(new JSONObject().put("type_id", "3").put("type_name", "新剧"));
            classes.put(new JSONObject().put("type_id", "2").put("type_name", "热播"));
            classes.put(new JSONObject().put("type_id", "7").put("type_name", "星选"));
            classes.put(new JSONObject().put("type_id", "5").put("type_name", "阳光"));

            result.put("class", classes);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            if (headerx.get("authorization") == null || headerx.get("authorization").isEmpty()) {
                getToken();
            }

            JSONObject result = new JSONObject();
            JSONArray list = new JSONArray();

            String url = siteUrl + "/v1/theater/home_page?theater_class_id=" + tid + "&page_num=" + pg + "&page_size=24";
            String content = OkHttp.string(url, headerx);
            JSONObject json = new JSONObject(content);

            if (json.optInt("code", -1) == 0 && json.has("data")) {
                JSONArray items = json.getJSONObject("data").optJSONArray("list");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject vodJson = items.getJSONObject(i).getJSONObject("theater");
                        JSONObject vod = new JSONObject();
                        vod.put("vod_id", vodJson.optString("id"));
                        vod.put("vod_name", vodJson.optString("title"));
                        vod.put("vod_pic", vodJson.optString("cover_url"));
                        vod.put("vod_remarks", vodJson.optString("theme", vodJson.optString("play_amount_str")));
                        list.put(vod);
                    }
                }
            }

            result.put("page", pg);
            result.put("pagecount", 9999);
            result.put("limit", 90);
            result.put("total", 999999);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "{\"list\":[]}";
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            if (headerx.get("authorization") == null || headerx.get("authorization").isEmpty()) {
                getToken();
            }

            String did = ids.get(0);
            String url = siteUrl + "/v2/theater_parent/detail?theater_parent_id=" + did;
            String content = OkHttp.string(url, headerx);
            JSONObject data = new JSONObject(content).optJSONObject("data");
            if (data == null) return "{\"list\":[]}";

            JSONObject vod = new JSONObject();
            vod.put("vod_id", did);
            vod.put("vod_name", data.optString("title", "未知"));
            vod.put("vod_content", "剧情：" + data.optString("introduction", ""));
            vod.put("vod_remarks", data.optString("filing", ""));
            vod.put("vod_area", data.optJSONArray("desc_tags") != null ? data.getJSONArray("desc_tags").optString(0, "") : "");

            String playFrom = "星芽";
            StringBuilder sb = new StringBuilder();

            JSONArray theaters = data.optJSONArray("theaters");
            if (theaters != null && theaters.length() > 0) {
                for (int i = 0; i < theaters.length(); i++) {
                    JSONObject ep = theaters.getJSONObject(i);
                    sb.append(ep.optString("num")).append("$").append(ep.optString("son_video_url")).append("#");
                }
            } else if (data.has("video_url") && !data.optString("video_url").isEmpty()) {
                sb.append("1$").append(data.optString("video_url"));
            } else {
                sb.append("暂无播放源$");
            }

            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '#') {
                sb.deleteCharAt(sb.length() - 1);
            }

            vod.put("vod_play_from", playFrom);
            vod.put("vod_play_url", sb.toString());

            JSONArray vodList = new JSONArray();
            vodList.put(vod);

            JSONObject result = new JSONObject();
            result.put("list", vodList);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "{\"list\":[]}";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", id);
            result.put("header", new JSONObject(headers).toString());
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            if (headerx.get("authorization") == null || headerx.get("authorization").isEmpty()) {
                getToken();
            }

            JSONObject payload = new JSONObject();
            payload.put("text", key);

            String url = siteUrl + "/v3/search";
            // 修复：使用 .body() 获取响应体
            String content = OkHttp.post(url, payload.toString(), headerx).body();
            JSONObject json = new JSONObject(content);

            JSONArray list = new JSONArray();
            if (json.optInt("code", -1) == 0 && json.has("data")) {
                JSONArray items = json.getJSONObject("data").optJSONObject("theater").optJSONArray("search_data");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        JSONObject vod = new JSONObject();
                        vod.put("vod_id", item.optString("id"));
                        vod.put("vod_name", item.optString("title"));
                        vod.put("vod_pic", item.optString("cover_url"));
                        vod.put("vod_remarks", item.optString("score_str"));
                        list.put(vod);
                    }
                }
            }

            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "{\"list\":[]}";
    }
}
