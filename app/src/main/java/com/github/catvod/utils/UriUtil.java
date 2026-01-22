package com.github.catvod.utils;

import android.text.TextUtils;
import androidx.annotation.Nullable;

/**
 * 修改后的完整版：保留了原有的高效索引算法，并增强了 resolve 方法
 */
public final class UriUtil {

    private static final int INDEX_COUNT = 4;
    private static final int SCHEME_COLON = 0;
    private static final int PATH = 1;
    private static final int QUERY = 2;
    private static final int FRAGMENT = 3;

    private UriUtil() {}

    /**
     * 核心方法：将相对 URL 转换为绝对 URL
     * 适配 JsSpiderEngine 的调用
     */
    public static String resolve(String baseUri, @Nullable String referenceUri) {
        StringBuilder uriBuilder = new StringBuilder();
        if (baseUri == null) baseUri = "";
        if (referenceUri == null) referenceUri = "";

        int[] refIndices = getUriIndices(referenceUri);
        if (refIndices[SCHEME_COLON] != -1) {
            return referenceUri; // 已经是绝对地址
        }

        int[] baseIndices = getUriIndices(baseUri);
        if (refIndices[QUERY] == 0) {
            return uriBuilder.append(baseUri, 0, baseIndices[QUERY]).append(referenceUri).toString();
        }
        if (refIndices[PATH] == 0) {
            return uriBuilder.append(baseUri, 0, baseIndices[PATH]).append(referenceUri).toString();
        }
        if (refIndices[PATH] != refIndices[QUERY]) {
            int lastSlashIndex = baseUri.lastIndexOf('/', baseIndices[QUERY] - 1);
            int basePathEnd = lastSlashIndex == -1 ? baseIndices[PATH] : lastSlashIndex + 1;
            return uriBuilder.append(baseUri, 0, basePathEnd).append(referenceUri).toString();
        }
        
        // 处理特殊的 "//" 开头
        if (referenceUri.startsWith("//")) {
            return baseUri.substring(0, baseIndices[SCHEME_COLON] + 1) + referenceUri;
        }

        return uriBuilder.append(baseUri, 0, baseIndices[PATH]).append(referenceUri).toString();
    }

    /**
     * 原有逻辑：高效计算 URI 各部分索引
     */
    private static int[] getUriIndices(String uriString) {
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
