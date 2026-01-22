package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 影视分类实体类 (TVBox/FongMi 协议 - 工业级稳健版)
 * 封装了针对各类爬虫源的容错逻辑与业务语义。
 */
public class Class {

    private static final Gson GSON = new Gson();

    @SerializedName("type_id")
    private String typeId;

    @SerializedName("type_name")
    private String typeName;

    /**
     * 对应 JSON 的 type_flag
     * 业务语义：筛选标志位 ("1" 代表显式开启筛选)
     */
    @SerializedName("type_flag")
    private String typeFlag;

    /**
     * 分类扩展数据：存放筛选条件的 JSON 字符串 (如 {"key": "...", "name": "..."})
     */
    @SerializedName("type_extend")
    private String typeExtend;

    /**
     * 排序字段：内部以 String 存储，兼顾 JSON 中的多种数据类型
     */
    @SerializedName("sort")
    private String sort;

    // --- 构造函数 ---

    public Class() {
    }

    public Class(String typeId, String typeName) {
        this(typeId, typeName, "0");
    }

    public Class(String typeId, String typeName, String typeFlag) {
        this.typeId = Objects.toString(typeId, "");
        this.typeName = Objects.toString(typeName, "");
        this.typeFlag = Objects.toString(typeFlag, "0");
    }

    // --- 静态解析方法 ---

    public static List<Class> arrayFrom(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Type listType = new TypeToken<List<Class>>() {}.getType();
            List<Class> list = GSON.fromJson(json, listType);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // --- Getter & Setter ---

    public String getTypeId() {
        return Objects.toString(typeId, "");
    }

    public void setTypeId(String typeId) {
        this.typeId = Objects.toString(typeId, "");
    }

    public String getTypeName() {
        return Objects.toString(typeName, "");
    }

    public void setTypeName(String typeName) {
        this.typeName = Objects.toString(typeName, "");
    }

    public String getTypeFlag() {
        return Objects.toString(typeFlag, "0");
    }

    public void setTypeFlag(String typeFlag) {
        this.typeFlag = Objects.toString(typeFlag, "0");
    }

    public String getTypeExtend() {
        return Objects.toString(typeExtend, "");
    }

    public void setTypeExtend(String typeExtend) {
        // setter 时进行首尾去空格处理，确保下游逻辑判断准确
        this.typeExtend = (typeExtend == null) ? "" : typeExtend.trim();
    }

    /**
     * 获取排序：区分“未设置”与“数值0”
     * @return 如果 sort 缺失或非数字返回 null，否则返回对应数值
     */
    public Integer getSort() {
        if (sort == null || sort.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(sort.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 统一 Setter：只接受 String，内部自动处理空值
     */
    public void setSort(String sort) {
        this.sort = Objects.toString(sort, null);
    }

    // --- 业务辅助方法 ---

    /**
     * 语义化判断：分类是否支持筛选
     * 保护逻辑：
     * 1. 显式 flag 为 "1"
     * 2. 扩展字符串长度 > 2（有效排除空 JSON "{}" 和 "[]" 及干扰字符）
     */
    public boolean isFilterable() {
        if ("1".equals(getTypeFlag())) return true;
        String extend = getTypeExtend();
        // 阈值保护：长度必须大于 2 且不等于常见的空 JSON 结构
        return extend.length() > 2 && 
               !extend.equals("{}") && 
               !extend.equals("[]");
    }

    public boolean isAll() {
        String id = getTypeId();
        return "0".equals(id) || "全部".equals(getTypeName());
    }

    // --- 标准方法重载 (@Override) ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Class)) return false;
        Class other = (Class) o;
        return Objects.equals(getTypeId(), other.getTypeId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTypeId());
    }

    @Override
    public String toString() {
        // 使用动态类名，增强调试可读性
        return String.format(
            "%s{id='%s', name='%s', filterFlag='%s', filterable=%b, sort=%s}",
            getClass().getSimpleName(),
            getTypeId(), 
            getTypeName(), 
            getTypeFlag(), 
            isFilterable(), 
            getSort() // 这里会打印数值或 null
        );
    }
}
