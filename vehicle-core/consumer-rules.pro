# app 의 R8 에 자동 적용되는 규칙
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class kr.htaxlog.**$$serializer { *; }
-keepclassmembers class kr.htaxlog.** { *** Companion; }
# Pleos Vehicle SDK uses Gson TypeToken subclasses for capability data.
# Their generic superclass must survive R8 in the release APK.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
# Jakarta/Java Mail
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn java.awt.**
-dontwarn javax.activation.**
# Pleos SDK — 규칙은 SDK 문서 확인 후 추가 [검증 필요]
