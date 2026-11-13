// 차량 로직 (화면 없음). Android library — app 을 빌드할 때 APK 안에 합쳐진다.
// 의존 방향: app → vehicle-core → shared. 이 모듈은 Compose/UI 를 모른다.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "kr.autolog.vehicle.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // AAOS CarPropertyManager(시동 상태). 런타임에는 차량 OS가 제공한다.
    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }

    // 계측 테스트 APK 패키징: android-mail·activation의 중복 라이선스 파일
    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":shared")) // inbox DTO. :relay 는 절대 넣지 않는다.

    // Pleos Connect SDK
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navi)
    implementation(libs.pleos.fused)
    implementation(libs.pleos.stt) // Gleo AI SpeechToText 3.3.1 (Pleos Nexus에서 확인)

    // 상태 노출 (app 이 collect)
    api(libs.coroutines.android)
    implementation(libs.lifecycle.service) // Foreground Service

    // 로컬 원본 저장 · 백그라운드
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.ktx)
    implementation(libs.security.crypto) // SMTP 앱 비밀번호 암호화 저장

    // 중계 서버 inbox 수신
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx)
    implementation(libs.okhttp)

    // 메일 발송 (SMTP)
    implementation(libs.android.mail)
    implementation(libs.android.activation)

    // XLSX: POI 대신 assets 템플릿 XML 치환 + ZipOutputStream 으로 시작 (APK 크기)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    // SDK 탐침(에뮬레이터 + Pleos Virtual Console). ./gradlew :vehicle-core:connectedDebugAndroidTest
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
