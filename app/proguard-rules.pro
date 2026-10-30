# --- Crashlytics: keep file names and line numbers so stack traces are readable; keep custom exception types ---
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
# Firebase ships its own consumer rules; nothing else is needed for Analytics / Crashlytics.

# --- OkHttp (platform-specific classes it probes reflectively) ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- kotlinx.serialization: keep generated serializers and the @Serializable classes' companions ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.darshan.splitsmart.**$$serializer { *; }
-keepclassmembers class com.darshan.splitsmart.** { *** Companion; }
-keepclasseswithmembers class com.darshan.splitsmart.** { kotlinx.serialization.KSerializer serializer(...); }

# --- zxing is plain Java; no rules needed. Room / Hilt / Compose ship consumer rules. ---
