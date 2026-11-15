# Keep Xray-core JNI bindings and GoMobile callback interfaces
-keep class libv2ray.** { *; }
-dontwarn libv2ray.**

-keep class com.qantara.vpn.xray.** { *; }
-keep class com.qantara.vpn.model.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
