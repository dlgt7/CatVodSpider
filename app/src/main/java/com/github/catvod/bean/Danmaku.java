package com.github.catvod.bean;

import com.google.gson.annotations.SerializedName;

/**
 * 弹幕实体类
 * 使用 Record 自动实现 Getter (n(), v()), equals, hashCode, toString
 */
public record Danmaku(
    @SerializedName("name") String name,
    @SerializedName("url") String url
) {
    public static Builder create() {
        return new Builder();
    }

    /**
     * 保持链式调用习惯的 Builder
     */
    public static class Builder {
        private String name;
        private String url;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Danmaku build() {
            return new Danmaku(name, url);
        }
    }
}
