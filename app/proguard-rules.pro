# MobileDigger ProGuard Rules for Play Store Release
# Generated for v10.46 - Play Store Compliance

# ===================================
# ESSENTIAL - Keep Core App Classes
# ===================================

# Keep all app classes for debugging
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    *;
}

# ===================================
# COMPOSE - Required for Jetpack Compose
# ===================================

# Keep all Composable functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Compose runtime classes
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }

# Keep Compose compiler classes
-dontwarn androidx.compose.compiler.**

# ===================================
# MEDIA3 - AndroidX Media Playback
# ===================================

-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Media Session classes
-keep class androidx.media.** { *; }

# ===================================
# VLC (libVLC) - CRITICAL for AIFF playback
# ===================================

-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-keepclassmembers class org.videolan.libvlc.** {
    <init>(...);
    *;
}
-dontwarn org.videolan.**

# ===================================
# FFMPEG - Audio format support
# ===================================

-keep class wseemann.media.** { *; }
-keepclassmembers class wseemann.media.** {
    native <methods>;
    *;
}
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn wseemann.media.**

# ===================================
# COROUTINES - Kotlin Coroutines
# ===================================

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ===================================
# VIEWMODEL & LIFECYCLE
# ===================================

-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# Keep ViewModel factory
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ===================================
# SERIALIZATION (if using parcelize)
# ===================================

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ===================================
# REFLECTION - Keep reflected classes
# ===================================

-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ===================================
# ENUMS
# ===================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===================================
# SERVICE & RECEIVER
# ===================================

# Keep Services
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Keep our specific services
-keep class com.example.mobiledigger.audio.MusicService { *; }
-keep class com.example.mobiledigger.audio.MediaButtonReceiver { *; }

# ===================================
# DOCUMENTFILE (File operations)
# ===================================

-keep class androidx.documentfile.provider.** { *; }

# ===================================
# WAVEFORM LIBRARIES
# ===================================

# Amplituda
-keep class linc.com.amplituda.** { *; }

# WaveformSeekBar
-keep class com.masoudss.lib.** { *; }

# JTransforms (FFT)
-keep class org.jtransforms.** { *; }
-dontwarn org.jtransforms.**

# ===================================
# OPTIMIZATION SETTINGS
# ===================================

# Don't warn about missing classes
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Optimization options
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# ===================================
# DEBUGGING (Keep for crash reports)
# ===================================

# Keep source file names and line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# ===================================
# REMOVE LOGGING (Optional - uncomment for production)
# ===================================

# Uncomment to remove all Log calls in release builds
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
# }

# ===================================
# APP-SPECIFIC CLASSES (Adjust as needed)
# ===================================

# Keep all model classes
-keep class com.example.mobiledigger.model.** { *; }

# Keep all util classes (for crash logging)
-keep class com.example.mobiledigger.util.** { *; }

# Keep Application class
-keep class com.example.mobiledigger.MobileDiggerApplication { *; }