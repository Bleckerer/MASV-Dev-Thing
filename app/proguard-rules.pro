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

# Keep all model classes (used by Gson/Retrofit)
-keep class com.cambrian.masv_dev.models.** { *; }

# Keep API interfaces (Retrofit)
-keep interface com.cambrian.masv_dev.api.** { *; }

# Keep all WorkManager workers
-keep class com.cambrian.masv_dev.*Worker { *; }

# Keep all classes that might be accessed via reflection (e.g., PreferencesHelper, NotificationHelper)
-keep class com.cambrian.masv_dev.utils.** { *; }

# Keep Parcelable / Serializable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep annotations (for Gson/Retrofit)
-keepattributes *Annotation*
-keepattributes Signature

# Keep OkHttp and Logging interceptor
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Explicitly keep NotificationHelper and all its methods
-keep class com.cambrian.masv_dev.utils.NotificationHelper { *; }

# Keep all R resources (prevent resource shrinking from removing them)
-keep class com.cambrian.masv_dev.R$* { *; }
-keepattributes InnerClasses

# Keep NotificationManagerCompat and NotificationCompat
-keep class androidx.core.app.NotificationManagerCompat { *; }
-keep class androidx.core.app.NotificationCompat { *; }
-keep class androidx.core.app.NotificationCompat$Builder { *; }

# Keep PendingIntent
-keep class android.app.PendingIntent { *; }