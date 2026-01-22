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
public class VodClass {

    private static final Gson GSON = new Gson();

    @SerializedName("type_id")
    private String typeId;

    @SerializedName("type_name")
    private String typeName;

    @SerializedName("type_flag")
    private String typeFlag;

    @SerializedName("type_extend")
    private String typeExtend;

    @SerializedName("sort")
    private String sort;

    public VodClass() {
    }

    public VodClass(String typeId, String typeName) {
        this(typeId, typeName, "0");
    }

    public VodClass(String typeId, String typeName, String typeFlag) {
        this.typeId = Objects.toString(typeId, "");
        this.typeName = Objects.toString(typeName, "");
        this.typeFlag = Objects.toString(typeFlag, "0");
    }

    public static List<VodClass> arrayFrom(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Type listType = new TypeToken<List<VodClass>>() {}.getType();
            List<VodClass> list = GSON.fromJson(json, listType);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

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
        this.typeExtend = (typeExtend == null) ? "" : typeExtend.trim();
    }

    public Integer getSort() {
        if (sort == null || sort.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(sort.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setSort(String sort) {
        this.sort = Objects.toString(sort, null);
    }

    public boolean isFilterable() {
        if ("1".equals(getTypeFlag())) return true;
        String extend = getTypeExtend();
        return extend.length() > 2 && !extend.equals("{}") && !extend.equals("[]");
    }

    public boolean isAll() {
        String id = getTypeId();
        return "0".equals(id) || "全部".equals(getTypeName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VodClass)) return false;
        VodClass other = (VodClass) o;
        return Objects.equals(getTypeId(), other.getTypeId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTypeId());
    }

    @Override
    public String toString() {
        return String.format(
            "VodClass{id='%s', name='%s', filterFlag='%s', filterable=%b, sort=%s}",
            getTypeId(), 
            getTypeName(), 
            getTypeFlag(), 
            isFilterable(), 
            getSort()
        );
    }
}
