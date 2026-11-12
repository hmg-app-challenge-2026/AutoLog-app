plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency) apply false
    alias(libs.plugins.ktlint) apply false
    alias(fe.plugins.roborazzi) apply false
}

// 모든 Kotlin 모듈에 ktlint 적용 (CONVENTIONS.md 6.1)
//   ./gradlew ktlintFormat  — 자동 정리
//   ./gradlew ktlintCheck   — 검사 (CI)
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        filter { exclude { it.file.path.contains("/build/") } }
    }
}
