package com.github.catvod;

import android.app.Activity;
import android.os.Bundle;

import com.github.catvod.crawler.Spider;
import com.github.catvod.databinding.ActivityMainBinding;
import com.github.catvod.spider.Init;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private ActivityMainBinding binding;
    private ExecutorService executor;
    private Spider spider;
    private Gson gson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gson = new GsonBuilder().setPrettyPrinting().create();
        Logger.addLogAdapter(new AndroidLogAdapter());
        executor = Executors.newCachedThreadPool();
        
        // 动态加载爬虫类，避免因删除 PTT.java 导致的编译错误
        initSpiderInstance();

        executor.execute(this::initSpider);
        initView();
        initEvent();
    }

    private void initSpiderInstance() {
        try {
            // 你可以将下面的字符串替换为你项目中现有的任何爬虫类名，例如 "com.github.catvod.spider.Douban"
            String className = "com.github.catvod.spider.Douban"; 
            this.spider = (Spider) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            Logger.e("未能加载指定的爬虫类，请检查类名是否正确");
        }
    }

    private void initView() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }

    private void initEvent() {
        binding.home.setOnClickListener(view -> executor.execute(this::homeContent));
        binding.homeVideo.setOnClickListener(view -> executor.execute(this::homeVideoContent));
        binding.category.setOnClickListener(view -> executor.execute(this::categoryContent));
        binding.detail.setOnClickListener(view -> executor.execute(this::detailContent));
        binding.player.setOnClickListener(view -> executor.execute(this::playerContent));
        binding.search.setOnClickListener(view -> executor.execute(this::searchContent));
        binding.live.setOnClickListener(view -> executor.execute(this::liveContent));
        binding.proxy.setOnClickListener(view -> executor.execute(this::proxy));
    }

    private void initSpider() {
        try {
            if (spider != null) {
                Init.init(getApplicationContext());
                spider.init(this, "");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void updateUI(String result) {
        Init.post(() -> binding.result.setText(result));
    }

    public void homeContent() {
        try {
            if (spider == null) return;
            String result = gson.toJson(JsonParser.parseString(spider.homeContent(true)));
            updateUI(result);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void homeVideoContent() {
        try {
            if (spider == null) return;
            String result = gson.toJson(JsonParser.parseString(spider.homeVideoContent()));
            updateUI(result);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void categoryContent() {
        try {
            if (spider == null) return;
            HashMap<String, String> extend = new HashMap<>();
            extend.put("c", "19");
            extend.put("year", "2024");
            String result = gson.toJson(JsonParser.parseString(spider.categoryContent("3", "2", true, extend)));
            updateUI(result);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void detailContent() {
        try {
            if (spider == null) return;
            String result = gson.toJson(JsonParser.parseString(spider.detailContent(List.of("78702"))));
            updateUI(result);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void playerContent() {
        try {
            if (spider == null) return;
            String result = gson.toJson(JsonParser.parseString(spider.playerContent("", "382044/1/78", new ArrayList<>())));
            updateUI(result);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void searchContent() {
        try {
            if (spider == null) return;
            String result = gson.toJson(JsonParser.parseString(spider.searchContent("我的人间烟火", false)));
            updateUI(result);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void liveContent() {
        try {
            if (spider == null) return;
            String result = gson.toJson(JsonParser.parseString(spider.liveContent("")));
            updateUI(result);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void proxy() {
        try {
            if (spider == null) return;
            Map<String, String> params = new HashMap<>();
            Logger.t("proxy").d(spider.proxy(params));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
