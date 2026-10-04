# R8 / ProGuard rules for the ESP32 Pedometer Sync app.
#
# This file is referenced by the release build type in app/build.gradle.kts and
# is only applied when isMinifyEnabled is switched on.

# WebView JavaScript bridge ------------------------------------------------
# MainActivity injects an anonymous object as "AndroidBridge" and map.html calls
# onRouteChanged() / onApplyClicked() by name, so those method names and the
# @JavascriptInterface annotation must survive shrinking and obfuscation.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Crash reports ------------------------------------------------------------
# Keep enough metadata to symbolicate release stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
