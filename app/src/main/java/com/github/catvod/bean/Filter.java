package com.github.catvod.bean;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 筛选条件实体类 - 针对 Java 17 与最新的 ProGuard 配置优化
 */
public class Filter {

    @SerializedName("key")
    private String key;

    @SerializedName("name")
    private String name;

    @SerializedName("init")
    private String init;

    @SerializedName("value")
    private List<Value> value;

    public Filter() {
    }

    public Filter(String key, String name, List<Value> value) {
        this(key, name, null, value);
    }

    public Filter(String key, String name, String init, List<Value> value) {
        this.key = key;
        this.name = name;
        this.init = init;
        this.value = value != null ? List.copyOf(value) : Collections.emptyList();
    }

    /**
     * 智能获取初始值：如果未定义 init，则尝试返回第一个选项的值
     */
    public String getInit() {
        if ((init == null || init.isEmpty()) && hasValues()) {
            return value.get(0).v();
        }
        return init;
    }

    public boolean hasValues() {
        return value != null && !value.isEmpty();
    }

    /**
     * 使用 Java 17 Record 替代内部类
     * 自动实现 getter (n(), v()), equals, hashCode, toString
     */
    public record Value(
        @SerializedName("n") String n,
        @SerializedName("v") String v
    ) {
        public Value(String value) {
            this(value, value);
        }
    }

    // --- Getters & Setters ---

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public void setInit(String init) { this.init = init; }

    public List<Value> getValue() {
        return value != null ? value : Collections.emptyList();
    }

    public void setValue(List<Value> value) {
        this.value = value != null ? List.copyOf(value) : Collections.emptyList();
    }

    @Override
    public String toString() {
        return String.format("Filter{key='%s', name='%s', count=%d}", 
                key, name, getValue().size());
    }
}
