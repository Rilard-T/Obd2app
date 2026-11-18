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

# Keep @Keep annotated Firestore models and serialization classes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class androidx.annotation.Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# kotlinx.serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.example.obd.model.**$$serializer { *; }
-keepclassmembers class com.example.obd.model.** {
    *** Companion;
}

# Android Auto CarAppService
-keep class com.example.car.** { *; }

# Keep Room entities & DAOs
-keep class com.example.obd.data.*Entity { *; }
-keep class com.example.obd.cloud.* { *; }
