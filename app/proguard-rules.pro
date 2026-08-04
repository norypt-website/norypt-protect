# Strip all android.util.Log calls from release builds
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# Compose needs no blanket keep; the previous `-keep class androidx.compose.** { *; }`
# exempted the whole Compose runtime from shrinking and obfuscation.
-keepclassmembers class * extends androidx.lifecycle.ViewModel { *; }

# Keep Device Admin receiver
-keep class com.norypt.protect.admin.ProtectAdminReceiver { *; }
-keepclassmembers class com.norypt.protect.admin.ProtectAdminReceiver {
    public <methods>;
}

# Tink (used transitively by androidx.security:security-crypto for
# EncryptedSharedPreferences) references optional Errorprone + javax.annotation
# classes that aren't on the Android runtime classpath. R8 only needs to
# silence these warnings; the annotations are stripped at runtime anyway.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
