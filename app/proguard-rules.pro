# ---------------------------------------------------------
# 基础结构与混淆字典优化
# ---------------------------------------------------------
-flattenpackagehierarchy com.github.catvod.spider.merge

# ---------------------------------------------------------
# Bean / JSON 数据模型保持 (关键修改)
# ---------------------------------------------------------
# 保持所有使用了 SerializedName 注解的字段名不被混淆 [cite: 1]
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 特别保持 Record 类 (Java 17 特性) [cite: 2]
# Record 的属性名、方法名和构造函数必须保留，否则 Gson 无法映射 [cite: 2]
-keepattributes Signature, MethodParameters, AnnotationDefault
-keep class com.github.catvod.bean.** { *; }

# ---------------------------------------------------------
# 框架与第三方依赖保持
# ---------------------------------------------------------

# dontwarn (忽略编译时警告) 
-dontwarn org.slf4j.**
-dontwarn org.xmlpull.v1.**
-dontwarn android.content.res.**
-dontwarn okhttp3.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.**

# SLF4J 保持 [cite: 4]
-keep class org.slf4j.** { *; }

# AndroidX 核心保持 [cite: 4]
-keep class androidx.core.** { *; }

# Spider (猫影视爬虫逻辑) 保持 [cite: 5, 6]
-keep class com.github.catvod.crawler.* { *; }
-keep class com.github.catvod.spider.* { public <methods>; }
-keep class com.github.catvod.js.Function { *; }

# OkHttp & Okio 保持 [cite: 7]
-keep class okio.** { *; }
-keep class okhttp3.** { *; }

# QuickJS (JS 引擎执行器) 保持 [cite: 8]
-keep class com.whl.quickjs.** { *; }

# Sardine (WebDAV 支持) 保持 [cite: 8]
-keep class com.thegrizzlylabs.sardineandroid.** { *; }

# SMBJ (局域网共享支持) 保持 [cite: 9]
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }

# Logger 保持 [cite: 9]
-keep class com.orhanobut.logger.** { *; }

# Gson (防止核心类被移除) [cite: 10]
-keep class com.google.gson.** { *; }
