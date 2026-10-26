# ==== Reflection / native (усе, що може впасти під R8) ====
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, SourceFile, LineNumberTable
-keepclassmembers,allowshrinking,allowobfuscation class * {
    @androidx.annotation.Keep *;
}
-keep @androidx.annotation.Keep class * {*;}

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ==== Shizuku ====
-keep class moe.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-keep interface moe.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-dontwarn moe.shizuku.**
-dontwarn rikka.shizuku.**
-dontwarn dev.rikka.shizuku.**

# ==== LiteRT-LM ====
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn com.google.mediapipe.**

# ==== Media3 (ExoPlayer) ====
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ==== JSON ====
-keep class org.json.** { *; }

# ==== Kotlin ====
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ==== Compose ====
-dontwarn androidx.compose.**
-keepclassmembers class androidx.compose.runtime.** { *; }

# ==== Reflection API для нашого коду ====
-keep class com.dynamicai.launcher.** { *; }
-keepclassmembers class com.dynamicai.launcher.** { *; }

# ==== Coroutines ====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}


# Godot Engine Rules
-keep class org.godotengine.godot.** { *; }
-keepclassmembers class org.godotengine.godot.** { *; }
-dontwarn org.godotengine.godot.**

# NewPipe Extractor ProGuard rules
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-keep class org.schabi.newpipe.extractor.** { *; }
