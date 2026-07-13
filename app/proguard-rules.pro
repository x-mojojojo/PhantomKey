# PhantomKey ProGuard rules
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class com.phantomkey.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
