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

# Preserve useful release crash traces while R8 still obfuscates implementation names.
-keepattributes SourceFile,LineNumberTable

# ML Kit registers its features through ComponentRegistrar classes listed in the manifest and
# created by reflection with their no-argument constructor. The rule bundled with the
# firebase-components 16.1.0 that ML Kit uses keeps only the class names, and R8 full mode
# (default in AGP 8+) then strips the constructors. Every registrar then fails to load, ML Kit
# has no components, and the first getClient() call crashes the release app at startup
# (NullPointerException in LanguageIdentifierImpl$Factory.create from BaseApp.onCreate).
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
