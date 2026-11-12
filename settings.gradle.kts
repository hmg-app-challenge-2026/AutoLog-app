pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")   // Pleos SDK
    }
    // libs = gradle/libs.versions.toml(Gradle 기본, 모든 모듈 공통) · fe = 차량 앱 화면 전용(Roborazzi·Coil 등)
    versionCatalogs {
        create("fe") { from(files("gradle/fe.versions.toml")) }
    }
}
rootProject.name = "AutoLog"

// 저장소에 있는 Gradle 모듈 목록일 뿐, APK 포함 여부와는 무관하다.
// APK에 무엇이 들어가는지는 vehicle-app/build.gradle.kts 의 dependencies 가 결정한다.
// 2026-11-11 AutoLog-Frontend·AutoLog-Backend를 이 리포 하나로 합쳤다 — 화면 앱은 :vehicle-app(예전 :app은 지움, R-6).
include(":shared", ":vehicle-core", ":relay", ":vehicle-app")
