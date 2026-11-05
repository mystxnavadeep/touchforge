# AIDL stubs and Shizuku user service must survive any future R8 pass.
-keep class com.touchforge.toolkit.helper.** { *; }
-keep class com.touchforge.toolkit.aidl.** { *; }
-keep class moe.shizuku.** { *; }
-keep class rikka.** { *; }
# kotlinx.serialization
-keepclassmembers class com.touchforge.toolkit.core.schema.** { *; }
