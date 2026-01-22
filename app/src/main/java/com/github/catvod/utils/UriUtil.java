package com.github.catvod.utils;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import java.net.URI;

/**
 * 整合版 URI 工具类
 * 包含高效的索引计算逻辑与可靠的相对路径解析
 */
public final class UriUtil {

    private static final int INDEX_COUNT = 4;
    private static final int SCHEME_COLON = 0;
    private static final int PATH = 1;
    private static final int QUERY = 2;
    private static final int FRAGMENT = 3;

    private UriUtil() {
    }

    /**
     * 将相对路径解析为绝对路径
     * @param base 基准地址 (例如 https://abc.com/folder/)
     * @param relative 相对地址 (例如 ../test.mp4)
     * @return 完整地址
     */
    public static String resolve(String base, String relative) {
        if (TextUtils.isEmpty(base)) return relative;
        if (TextUtils.isEmpty(relative)) return base;
        try {
            // 处理特殊的 // 开头路径 (根据基准地址补全协议)
            if (relative.startsWith("//")) {
                int[] baseIndices = getUriIndices(base);
                if (baseIndices[SCHEME_COLON] != -1) {
                    return base.substring(0, baseIndices[SCHEME_COLON] + 1) + relative;
                }
            }
            // 使用标准库进行路径合并
            URI baseUri = new URI(base);
            return baseUri.resolve(relative).toString();
        } catch (Exception e) {
            return relative;
        }
    }

    /**
     * 高效获取 URI 各部分的索引位置
     * @param uriString 待分析的 URI 字符串
     * @return 包含四个关键索引位置的数组
     */
    public static int[] getUriIndices(String uriString) {
        int[] indices = new int[INDEX_COUNT];
        if (TextUtils.isEmpty(uriString)) {
            indices[SCHEME_COLON] = -1;
            return indices;
        }

        int fragmentIndex = uriString.indexOf('#');
        if (fragmentIndex == -1) {
            fragmentIndex = uriString.length();
        }
        int queryIndex = uriString.indexOf('?');
        if (queryIndex == -1 || queryIndex > fragmentIndex) {
            queryIndex = fragmentIndex;
        }
        
        int schemeIndexLimit = uriString.indexOf('/');
        if (schemeIndexLimit == -1 || schemeIndexLimit > queryIndex) {
            schemeIndexLimit = queryIndex;
        }
        int schemeIndex = uriString.indexOf(':');
        if (schemeIndex > schemeIndexLimit) {
            schemeIndex = -1;
        }

        boolean hasAuthority = schemeIndex + 2 < queryIndex 
                && uriString.charAt(schemeIndex + 1) == '/' 
                && uriString.charAt(schemeIndex + 2) == '/';
        
        int pathIndex;
        if (hasAuthority) {
            pathIndex = uriString.indexOf('/', schemeIndex + 3);
            if (pathIndex == -1 || pathIndex > queryIndex) {
                pathIndex = queryIndex;
            }
        } else {
            pathIndex = schemeIndex + 1;
        }

        indices[SCHEME_COLON] = schemeIndex;
        indices[PATH] = pathIndex;
        indices[QUERY] = queryIndex;
        indices[FRAGMENT] = fragmentIndex;
        return indices;
    }
}
