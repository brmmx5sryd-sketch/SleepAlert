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

# [追加] JavaMail (Jakarta Mail) の動作に必要なクラスを保護
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class jakarta.mail.** { *; }
-keep class com.sun.activation.** { *; }

# 警告が出てもビルドを止めない設定
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn jakarta.mail.**
-dontwarn com.sun.activation.**

# 実行時のリフレクション（内部的な呼び出し）を許可
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses