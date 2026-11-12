// 차량 앱 화면 (Compose). 로직은 :vehicle-core.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(fe.plugins.roborazzi)
}

android {
    namespace = "kr.autolog.vehicle"
    compileSdk = 35
    useLibrary("android.car") // CarUxRestrictions (스펙 D-07)

    defaultConfig {
        applicationId = "kr.autolog.vehicle"
        minSdk = 28 // Pleos: 28 이상 필수
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "backend"
    productFlavors {
        create("demo") {
            dimension = "backend"
            applicationIdSuffix = ".demo"
        }
        create("vehicle") { dimension = "backend" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
}

dependencies {
    implementation(project(":vehicle-core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.m3)
    implementation(fe.compose.foundation)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.vm.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(fe.compose.icons.extended)
    implementation(fe.coil.compose) // 영수증 썸네일
    implementation(fe.zxing.core) // 영수증 QR

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(fe.turbine)
    testImplementation(fe.robolectric)
    testImplementation(fe.roborazzi)
    testImplementation(fe.roborazzi.compose)
    testImplementation(fe.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(fe.compose.ui.test.junit4)
    debugImplementation(fe.compose.ui.test.manifest)
}
