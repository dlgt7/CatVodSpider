package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.io.Serializable;
import java.util.Optional;

/**
 * 视频信息实体类
 * 优化点：引入 Lombok 简化代码，增强 Gson 解析性能，提供流式 API。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vod implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Gson GSON = new Gson();

    @SerializedName("type_name")
    private String typeName;
    
    @SerializedName("vod_id")
    private String vodId;
    
    @SerializedName("vod_name")
    private String vodName;
    
    @SerializedName("vod_pic")
    private String vodPic;
    
    @SerializedName("vod_remarks")
    private String vodRemarks;
    
    @SerializedName("vod_year")
    private String vodYear;
    
    @SerializedName("vod_area")
    private String vodArea;
    
    @SerializedName("vod_actor")
    private String vodActor;
    
    @SerializedName("vod_director")
    private String vodDirector;
    
    @SerializedName("vod_content")
    private String vodContent;
    
    @SerializedName("vod_play_from")
    private String vodPlayFrom;
    
    @SerializedName("vod_play_url")
    private String vodPlayUrl;
    
    @SerializedName("vod_tag")
    private String vodTag;
    
    @SerializedName("action")
    private String action;
    
    @SerializedName("style")
    private Style style;

    // --- 快速构建工具方法 ---

    /**
     * 从 JSON 字符串解析对象，自带空安全处理
     */
    public static Vod objectFrom(String str) {
        try {
            return Optional.ofNullable(GSON.fromJson(str, Vod.class)).orElseGet(Vod::new);
        } catch (Exception e) {
            return new Vod();
        }
    }

    /**
     * 快速创建带动作的对象
     */
    public static Vod action(String action) {
        return Vod.builder().action(action).build();
    }

    // --- 兼容性构造器 (保留原代码逻辑，通过 Builder 实现) ---

    public Vod(String vodId, String vodName, String vodPic) {
        this.vodId = vodId;
        this.vodName = vodName;
        this.vodPic = vodPic;
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks) {
        this(vodId, vodName, vodPic);
        this.vodRemarks = vodRemarks;
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, boolean folder) {
        this(vodId, vodName, vodPic, vodRemarks);
        this.vodTag = folder ? "folder" : "file";
    }

    // --- 内部样式类 ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Style implements Serializable {
        
        @SerializedName("type")
        private String type;
        
        @SerializedName("ratio")
        private Float ratio;

        public static Style rect() { return rect(0.75f); }
        
        public static Style rect(float ratio) { return new Style("rect", ratio); }
        
        public static Style oval() { return new Style("oval", 1.0f); }
        
        public static Style full() { return new Style("full", null); }
        
        public static Style list() { return new Style("list", null); }
    }
}
