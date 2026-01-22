# ---------------------------------------------------------
# 基础结构与混淆字典优化
# ---------------------------------------------------------
-flattenpackagehierarchy com.github.catvod.spider.merge

# ---------------------------------------------------------
# Bean / JSON 数据模型保持 (关键修改)
# ---------------------------------------------------------
# 保持所有使用了 SerializedName 注解的字段名不被混淆
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 特别保持 Record 类 (Java 17 特性)
# Record 的属性名、方法名和构造函数必须保留，否则 Gson 无法映射
-keepattributes Signature, MethodParameters, AnnotationDefault
-keep class com.github.catvod.bean.** { *; }

# ---------------------------------------------------------
# 框架与第三方依赖保持
# ---------------------------------------------------------

# [cite_start]dontwarn (忽略编译时警告) [cite: 1, 5]
-dontwarn org.slf4j.**
-dontwarn org.xmlpull.v1.**
-dontwarn android.content.res.**
-dontwarn okhttp3.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.**

# [cite_start]SLF4J [cite: 1]
-keep class org.slf4j.** { *; }

# [cite_start]AndroidX [cite: 1, 2]
-keep class androidx.core.** { *; }

# [cite_start]Spider (猫影视爬虫逻辑) [cite: 2, 3]
-keep class com.github.catvod.crawler.* { *; }
-keep class com.github.catvod.spider.* { public <methods>; }
-keep class com.github.catvod.js.Function { *; }

# [cite_start]OkHttp & Okio [cite: 3]
-keep class okio.** { *; }
-keep class okhttp3.** { *; }

# [cite_start]QuickJS (JS 引擎执行器) [cite: 3, 4]
-keep class com.whl.quickjs.** { *; }

# [cite_start]Sardine (WebDAV 支持) [cite: 4]
-keep class com.thegrizzlylabs.sardineandroid.** { *; }

# [cite_start]SMBJ (局域网共享支持) [cite: 4, 5]
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }

# [cite_start]Logger [cite: 5]
-keep class com.orhanobut.logger.** { *; }

# Gson (防止核心类被移除)
-keep class com.google.gson.** { *; }
