# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Jetpack Glance resolves which placed widget instances to update by the
# GlanceAppWidget subclass (GlanceAppWidgetManager.getGlanceIds(javaClass),
# used by updateAll()/updateIf()). R8 horizontal class merging collapses our
# structurally-identical HrtWidgetMedium/HrtWidgetLarge into a single class
# (distinguished only by a synthetic $r8$classId field), so getGlanceIds() can
# no longer tell them apart: HrtWidgetMedium().updateAll() and
# HrtWidgetLarge().updateAll() then both resolve to the same placed widget and
# race, intermittently rendering the medium layout into the large widget.
# -keepnames is insufficient (it blocks renaming, not merging); pin every
# GlanceAppWidget subclass so R8 keeps them as distinct classes.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget