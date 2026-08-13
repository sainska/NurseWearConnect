# NurseWearConnect ProGuard Rules

# Retrofit & OkHttp
-keepattributes Signature, InnerClasses, AnnotationDefault
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Gson
-keep class com.google.gson.** { *; }
-keep class com.example.nursewearconnect.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Supabase & Ktor
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Material Icons Extended - Help R8 tree-shake the massive icon library
# Since icons are accessed via static objects, we ensure R8 can see their usage
-keepclassmembers class androidx.compose.material.icons.Icons$** {
    public static <fields>;
}

# Remove Log messages in release builds for extra size reduction and security
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}
