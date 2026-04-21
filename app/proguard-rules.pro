# Strip all android.util.Log calls from release builds
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# Keep Compose + ViewModel reflection
-keep class androidx.compose.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { *; }

# Keep Device Admin receiver
-keep class com.norypt.protect.admin.ProtectAdminReceiver { *; }
-keepclassmembers class com.norypt.protect.admin.ProtectAdminReceiver {
    public <methods>;
}
