package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Gz360 Spider
 */
public class Gz360 extends Spider {

    private final HashMap<String, String> a = new HashMap<>();

    private static String aesDecrypt(String data, String key, String iv) {
        try {
            byte[] bytes = Crypto.hexToBytes(data);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return new String(cipher.doFinal(bytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String aesEncrypt(String data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec("U823n8pKnAAbWOST".getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec("wgr8N6BCs7426wf1".getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : encrypted) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static JsonObject apiRequest(JsonObject params, String path) {
        String baseUrl = "https://api.w32z7vtd.com";
        try {
            String time = String.valueOf(System.currentTimeMillis() / 1000);
            String requestKey = aesEncrypt(params.toString());
            String token = "97630f5f85d9f3c639fb7790ca881ef2.4cccf48dc340fe8bded39cfe4ef9ac2adb27425a9069e6cd121210fc7ba518ea8c1cc5629261e94bb6ccb66d8548449c72076c956a2fb46c253008909a6c66347eb458fe3c06d1fcc993ca03a298328f9229f1994a608250c7d1ae124c4520e6e14ce8bf9f4404119a6bbf53cf592a8df2e9145de92ec43ec87cf4bdc563f6e919fe32861b0e93b118ec37d8035fbb3c.473433979755ccd5ec1b4581ccef76e8209b9e0c6ff819917f12dffad47d0d5e";
            String keys = "bMTqITVqBsbq9UjLufsQuBvRiIyfqHLqAWUx0gj0ZUe9DMNDTmJDVZzAh45AZ5LtkC39Y0DU4Ufqm/9gliIJaj7cI/dhmoM5fib5HcslzyGONEwZY5fHBvokBreGaT8bPoaxmnWdTRjRfJzYZV6T06O7GsYVa6DuKTVArb0g48Q=";

            StringBuilder sb = new StringBuilder("token_id=,token=97630f5f85d9f3c639fb7790ca881ef2.4cccf48dc340fe8bded39cfe4ef9ac2adb27425a9069e6cd121210fc7ba518ea8c1cc5629261e94bb6ccb66d8548449c72076c956a2fb46c253008909a6c66347eb458fe3c06d1fcc993ca03a298328f9229f1994a608250c7d1ae124c4520e6e14ce8bf9f4404119a6bbf53cf592a8df2e9145de92ec43ec87cf4bdc563f6e919fe32861b0e93b118ec37d8035fbb3c.473433979755ccd5ec1b4581ccef76e8209b9e0c6ff819917f12dffad47d0d5e");
            sb.append(requestKey);
            sb.append(",app_id=1,time=").append(time);
            sb.append(",keys=bMTqITVqBsbq9UjLufsQuBvRiIyfqHLqAWUx0gj0ZUe9DMNDTmJDVZzAh45AZ5LtkC39Y0DU4Ufqm/9gliIJaj7cI/dhmoM5fib5HcslzyGONEwZY5fHBvokBreGaT8bPoaxmnWdTRjRfJzYZV6T06O7GsYVa6DuKTVArb0g48Q=*&zvdvdvddbfikkkumtmdwqppp?|4Y!s!2br");

            String signature = md5Encrypt(sb.toString());

            LinkedHashMap<String, String> formData = new LinkedHashMap<>();
            formData.put("token", token);
            formData.put("token_id", "");
            formData.put("phone_type", "1");
            formData.put("time", time);
            formData.put("phone_model", "xiaomi-2206123sc");
            formData.put("keys", keys);
            formData.put("request_key", requestKey);
            formData.put("signature", signature);
            formData.put("app_id", "1");
            formData.put("ad_version", "1");

            String url = baseUrl.concat(path);
            String response = OkHttp.post(url, formData, getHeaders());
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();

            if (!result.has("data")) return null;

            JsonObject data = result.getAsJsonObject("data");
            if (data.has("keys") && data.has("response_key")) {
                String keysStr = data.get("keys").getAsString();
                String decryptedKeys = rsaDecrypt(keysStr);
                JsonObject keysObj = JsonParser.parseString(decryptedKeys).getAsJsonObject();
                String responseKey = data.get("response_key").getAsString();
                String aesKey = keysObj.get("key").getAsString();
                String aesIv = keysObj.get("iv").getAsString();
                String decrypted = aesDecrypt(responseKey, aesKey, aesIv);
                if (!TextUtils.isEmpty(decrypted)) {
                    return JsonParser.parseString(decrypted).getAsJsonObject();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Version", "2406025");
        headers.put("PackageName", "com.j64f4b21072.ha69699879.dfea0a9826ba.ibf50c9b1d");
        headers.put("Ver", "1.9.2");
        headers.put("Referer", "https://api.w32z7vtd.com");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("User-Agent", "okhttp/3.12.0");
        return headers;
    }

    private static ArrayList<Filter> getCommonFilters() {
        ArrayList<Filter> filters = new ArrayList<>();
        // 地区
        filters.add(new Filter("area", "地区", Arrays.asList(
                new Filter.Value("地区", "0"),
                new Filter.Value("大陆", "大陆"),
                new Filter.Value("香港", "香港"),
                new Filter.Value("台湾", "台湾"),
                new Filter.Value("日本", "日本"),
                new Filter.Value("韩国", "韩国")
        )));
        // 年份
        filters.add(new Filter("year", "年份", Arrays.asList(
                new Filter.Value("年份", "0"),
                new Filter.Value("2026", "2026"),
                new Filter.Value("2025", "2025"),
                new Filter.Value("2024", "2024"),
                new Filter.Value("2023", "2023")
        )));
        // 排序
        filters.add(new Filter("sort", "排序", Arrays.asList(
                new Filter.Value("综合", "d_id"),
                new Filter.Value("最新", "d_addtime"),
                new Filter.Value("最热", "d_score"),
                new Filter.Value("高分", "d_score")
        )));
        return filters;
    }

    private static String md5Encrypt(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private static String rsaDecrypt(String data) {
        try {
            byte[] encrypted = Base64.decode(data, Base64.DEFAULT);
            String privateKeyStr = "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGAe6hKrWLi1zQmjTT1\nozbE4QdFeJGNxubxld6GrFGximxfMsMB6BpJhpcTouAqywAFppiKetUBBbXwYsYU\n1wNr648XVmPmCMCy4rY8vdliFnbMUj086DU6Z+/oXBdWU3/b1G0DN3E9wULRSwcK\nZT3wj/cCI1vsCm3gj2R5SqkA9Y0CAwEAAQKBgAJH+4CxV0/zBVcLiBCHvSANm0l7\nHetybTh/j2p0Y1sTXro4ALwAaCTUeqdBjWiLSo9lNwDHFyq8zX90+gNxa7c5EqcW\nV9FmlVXr8VhfBzcZo1nXeNdXFT7tQ2yah/odtdcx+vRMSGJd1t/5k5bDd9wAvYdI\nDblMAg+wiKKZ5KcdAkEA1cCakEN4NexkF5tHPRrR6XOY/XHfkqXxEhMqmNbB9U34\nsaTJnLWIHC8IXys6Qmzz30TtzCjuOqKRRy+FMM4TdwJBAJQZFPjsGC+RqcG5UvVM\niMPhnwe/bXEehShK86yJK/g/UiKrO87h3aEu5gcJqBygTq3BBBoH2md3pr/W+hUM\nWBsCQQChfhTIrdDinKi6lRxrdBnn0Ohjg2cwuqK5zzU9p/N+S9x7Ck8wUI53DKm8\njUJE8WAG7WLj/oCOWEh+ic6NIwTdAkEAj0X8nhx6AXsgCYRql1klbqtVmL8+95KZ\nK7PnLWG/IfjQUy3pPGoSaZ7fdquG8bq8oyf5+dzjE/oTXcByS+6XRQJAP/5ciy1b\nL3NhUhsaOVy55MHXnPjdcTX0FaLi+ybXZIfIQ2P4rb19mVq1feMbCXhz+L1rG8oa\nt5lYKfpe8k83ZA==";
            privateKeyStr = privateKeyStr.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            byte[] keyBytes = Base64.decode(privateKeyStr, Base64.DEFAULT);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            StringBuilder sb = new StringBuilder();
            int offset = 0;
            while (offset < encrypted.length) {
                int chunkLen = Math.min(128, encrypted.length - offset);
                byte[] chunk = Arrays.copyOfRange(encrypted, offset, offset + chunkLen);
                byte[] decryptedChunk = cipher.doFinal(chunk);
                sb.append(new String(decryptedChunk, StandardCharsets.UTF_8));
                offset += 128;
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String getString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    @Override
    public void init(Context context, String extend) {
        a.put("1", "5");
        a.put("2", "12");
        a.put("3", "30");
        a.put("4", "22");
        a.put("64", "");
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = Arrays.asList(
                new Class("1", "电影"),
                new Class("2", "国产剧"),
                new Class("3", "动漫"),
                new Class("4", "综艺"),
                new Class("64", "短剧")
        );

        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // 电影分类过滤器
        ArrayList<Filter> movieFilters = getCommonFilters();
        movieFilters.add(2, new Filter("sub", "类型", Arrays.asList(
                new Filter.Value("动作片", "5"),
                new Filter.Value("喜剧片", "6"),
                new Filter.Value("爱情片", "7"),
                new Filter.Value("科幻片", "8"),
                new Filter.Value("恐怖片", "9"),
                new Filter.Value("剧情片", "10")
        )));
        filters.put("1", movieFilters);

        // 国产剧分类过滤器
        ArrayList<Filter> dramaFilters = getCommonFilters();
        dramaFilters.add(2, new Filter("sub", "类型", Arrays.asList(
                new Filter.Value("国产剧", "12"),
                new Filter.Value("香港剧", "13"),
                new Filter.Value("台湾剧", "14"),
                new Filter.Value("欧美剧", "15"),
                new Filter.Value("日本剧", "16"),
                new Filter.Value("韩国剧", "17")
        )));
        filters.put("2", dramaFilters);

        // 动漫分类过滤器
        ArrayList<Filter> animeFilters = getCommonFilters();
        animeFilters.add(2, new Filter("sub", "类型", Arrays.asList(
                new Filter.Value("中国动漫", "30"),
                new Filter.Value("日本动漫", "31"),
                new Filter.Value("欧美动漫", "33")
        )));
        filters.put("3", animeFilters);

        // 综艺分类过滤器
        ArrayList<Filter> varietyFilters = getCommonFilters();
        varietyFilters.add(2, new Filter("sub", "类型", Arrays.asList(
                new Filter.Value("大陆综艺", "22"),
                new Filter.Value("港台综艺", "23"),
                new Filter.Value("日韩综艺", "24"),
                new Filter.Value("欧美综艺", "25")
        )));
        filters.put("4", varietyFilters);

        // 短剧分类过滤器
        filters.put("64", Arrays.asList(
                new Filter("sort", "排序", Arrays.asList(
                        new Filter.Value("综合", "d_id"),
                        new Filter.Value("最新", "d_addtime"),
                        new Filter.Value("最热", "d_score"),
                        new Filter.Value("高分", "d_score")
                ))
        ));

        return Result.string(classes, filters);
    }

    private Vod getVod(JsonObject item) {
        Vod vod = new Vod();
        vod.setVodId(getString(item, "vod_id"));
        vod.setVodName(getString(item, "vod_name"));
        vod.setVodPic(getString(item, "vod_pic"));
        vod.setVodYear(getString(item, "vod_year"));
        String continu = getString(item, "vod_continu");
        String score = getString(item, "vod_scroe");
        if (!TextUtils.isEmpty(continu) && !"0".equals(continu)) {
            vod.setVodRemarks("更新至" + continu + "集");
        } else if (!TextUtils.isEmpty(score)) {
            vod.setVodRemarks(score);
        } else {
            vod.setVodRemarks("暂无备注");
        }
        return vod;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        JsonObject params = new JsonObject();
        params.addProperty("tid", tid);
        params.addProperty("page", pg);

        String sort = extend != null && extend.containsKey("sort") ? extend.get("sort") : "d_id";
        params.addProperty("sort", sort);

        String area = extend != null && extend.containsKey("area") ? extend.get("area") : "0";
        params.addProperty("area", area);

        String sub = extend != null && extend.containsKey("sub") ? extend.get("sub") : a.get(tid);
        params.addProperty("sub", sub);

        String year = extend != null && extend.containsKey("year") ? extend.get("year") : "0";
        params.addProperty("year", year);

        params.addProperty("pageSize", "30");

        JsonObject result = apiRequest(params, "/App/IndexList/indexList");

        ArrayList<Vod> list = new ArrayList<>();
        if (result != null && result.has("list")) {
            JsonArray array = result.getAsJsonArray("list");
            for (JsonElement element : array) {
                list.add(getVod(element.getAsJsonObject()));
            }
        }

        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, 9999, 30, 999999).string();
    }

    @Override
    public String detailContent(List<String> ids) {
        String vodId = ids.get(0);

        JsonObject params = new JsonObject();
        params.addProperty("token_id", "1009464");
        params.addProperty("vod_id", vodId);
        params.addProperty("mobile_time", String.valueOf(System.currentTimeMillis() / 1000));
        params.addProperty("token", "97630f5f85d9f3c639fb7790ca881ef2.4cccf48dc340fe8bded39cfe4ef9ac2adb27425a9069e6cd121210fc7ba518ea8c1cc5629261e94bb6ccb66d8548449c72076c956a2fb46c253008909a6c66347eb458fe3c06d1fcc993ca03a298328f9229f1994a608250c7d1ae124c4520e6e14ce8bf9f4404119a6bbf53cf592a8df2e9145de92ec43ec87cf4bdc563f6e919fe32861b0e93b118ec37d8035fbb3c.473433979755ccd5ec1b4581ccef76e8209b9e0c6ff819917f12dffad47d0d5e");

        JsonObject vodInfo = apiRequest(params, "/App/IndexPlay/playInfo");
        if (vodInfo == null || !vodInfo.has("vodInfo")) {
            return Result.error("详情获取失败");
        }

        JsonObject vodData = vodInfo.getAsJsonObject("vodInfo");

        // 获取播放链接
        JsonObject playParams = new JsonObject();
        playParams.addProperty("vurl_cloud_id", "2");
        playParams.addProperty("vod_d_id", vodId);

        JsonObject playResult = apiRequest(playParams, "/App/Resource/Vurl/show");

        LinkedHashMap<String, List<String>> playMap = new LinkedHashMap<>();
        if (playResult != null && playResult.has("list")) {
            JsonArray playArray = playResult.getAsJsonArray("list");
            for (JsonElement element : playArray) {
                JsonObject playItem = element.getAsJsonObject();
                String title = getString(playItem, "title");
                if (!playItem.has("play")) continue;
                JsonObject play = playItem.getAsJsonObject("play");
                for (String playKey : play.keySet()) {
                    JsonObject playData = play.getAsJsonObject(playKey);
                    String showType = getString(playData, "show_type");
                    if ("2".equals(showType)) continue;
                    String param = getString(playData, "param");
                    if (TextUtils.isEmpty(param)) continue;
                    if (!playMap.containsKey(playKey)) {
                        playMap.put(playKey, new ArrayList<>());
                    }
                    playMap.get(playKey).add(title + "$" + param);
                }
            }
        }

        ArrayList<String> playFrom = new ArrayList<>();
        ArrayList<String> playUrl = new ArrayList<>();
        for (String key : playMap.keySet()) {
            playFrom.add(key);
            playUrl.add(TextUtils.join("#", playMap.get(key)));
        }

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(getString(vodData, "vod_name"));
        vod.setVodPic(getString(vodData, "vod_pic"));
        vod.setVodContent(getString(vodData, "vod_use_content"));
        vod.setVodActor(getString(vodData, "vod_actor"));
        vod.setVodDirector(getString(vodData, "vod_director"));
        vod.setVodArea(getString(vodData, "vod_area"));
        vod.setVodYear(getString(vodData, "vod_year"));
        vod.setVodRemarks(getString(vodData, "vod_scroe"));
        vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        JsonObject params = new JsonObject();
        String[] parts = id.split("&");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                String key = part.substring(0, idx);
                String value = part.substring(idx + 1);
                params.addProperty(key, value);
            }
        }

        JsonObject result = apiRequest(params, "/App/Resource/VurlDetail/showOne");
        String url = result != null ? getString(result, "url") : "";

        if (TextUtils.isEmpty(url)) {
            return Result.error("播放链接解析失败");
        }

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Lavf/57.83.100");

        return Result.get().url(url).parse(0).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        JsonObject params = new JsonObject();
        params.addProperty("keywords", key);
        params.addProperty("order_val", "1");

        JsonObject result = apiRequest(params, "/App/Index/findMoreVod");

        ArrayList<Vod> list = new ArrayList<>();
        if (result != null && result.has("list")) {
            JsonArray array = result.getAsJsonArray("list");
            for (JsonElement element : array) {
                list.add(getVod(element.getAsJsonObject()));
            }
        }

        return Result.string(list);
    }
}