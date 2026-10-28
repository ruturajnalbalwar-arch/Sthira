# MediaPipe calls back into Java from native code; R8 must not strip or rename these.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
# kotlinx.serialization generated serializers
-keepclassmembers class app.sthira.** { *** Companion; }
-keepclasseswithmembers class app.sthira.** { kotlinx.serialization.KSerializer serializer(...); }
