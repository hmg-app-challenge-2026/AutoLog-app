# app 의 R8 에 자동 적용되는 규칙
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class kr.htaxlog.**$$serializer { *; }
-keepclassmembers class kr.htaxlog.** { *** Companion; }
# Jakarta/Java Mail
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn java.awt.**
-dontwarn javax.activation.**
# Pleos SDK — 규칙은 SDK 문서 확인 후 추가 [검증 필요]
