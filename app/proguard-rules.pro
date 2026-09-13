# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class: com.example.MyWebViewJavaScriptInterface

# ---------------------------------------------------------------------------
# Release minification (R8) keep rules for FlareOfficial
# ---------------------------------------------------------------------------

# Moshi: generated adapters are resolved by name at runtime (Class.forName),
# so they must survive shrinking/obfuscation.
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass <fields>;
}
-keep,allowobfuscation,allowshrinking class *JsonAdapter_*

# Retrofit/OkHttp/Moshi reflect over generic signatures and annotations.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations

# Agora RTC: the native engine dispatches into the Java API by exact names.
-keep class io.agora.** { *; }
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
