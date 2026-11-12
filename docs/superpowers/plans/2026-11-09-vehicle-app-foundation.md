# 차량 앱 기반 + 디자인 게이트 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AutoLog-Frontend에 Pleos 차량 앱의 뼈대(빌드·디자인 시스템·사용 3단계·Navigator)와 첫 화면 3종(ProfilePicker·Home+자동 종료 안내·Peek)을 만들고, 스크린샷으로 디자인 게이트를 통과할 수 있는 상태로 만든다.

**Architecture:** 단일 Activity + Compose. Compose → ViewModel(`StateFlow<UiState>`) → Repository → `backend/vehicle-core`의 Contracts(앱 B 것은 실제 Fake, 앱 A 것은 이 리포의 `data/interim`). 루트가 `UsageTier`(주행 BACKGROUND · 정차 PEEK · 주차 FULL)를 보고 화면 층을 고른다. 빌드는 Backend를 git submodule로 두고 composite build로 `vehicle-core`를 가져온다.

**Tech Stack:** Kotlin 2.0.21 · AGP 8.8.2 · Gradle 8.12 · JDK 17 · Compose BOM 2024.12.01 · Material 3 · lifecycle 2.8.7 · coroutines 1.9.0 · Coil 2.7.0(이 계획에선 미사용) · JUnit4 · Turbine 1.2.0 · Robolectric 4.14.1 · Roborazzi 1.32.2 · ktlint 1.5.0

**Spec:** [`docs/superpowers/specs/2026-11-09-autolog-frontend-design.md`](../specs/2026-11-09-autolog-frontend-design.md) + [`docs/design-language.md`](../../design-language.md). 실행 전에 두 문서를 읽는다. 이 계획은 스펙 11장 일정의 **11.09–10 골격 + 11.10–11 디자인 게이트** 범위다.

**이 계획에 없는 것(다음 계획):** ReviewTrip·영수증·운행기록부·이력·설정 화면, 딥링크, 앱 A의 LocalStore·ReportApi·DeviceApi·UploadApi interim, 운영 웹, 카메라 페이지, 사내 서버 취합. Home의 보조 버튼(영수증 저장·운행기록부·이력·설정)도 해당 화면과 함께 다음 계획에서 붙인다.

## Global Constraints

- minSdk 28 · compileSdk 35 · targetSdk 35 · Java 17 · `namespace`/`applicationId` = `kr.autolog.vehicle` (demo flavor는 `.demo` 접미사)
- 버전은 `backend/gradle/libs.versions.toml`(카탈로그 이름 `libs`)을 그대로 쓰고, 프론트 전용은 `gradle/fe.versions.toml`(카탈로그 이름 `fe`)에만 넣는다
- `backend/` 안의 파일은 고치지 않는다. submodule 포인터는 Backend `origin/main` 커밋으로만 올린다
- `ai.pleos.*` import 금지. `kr.autolog.vehicle.core`의 서비스 인터페이스는 `data/` 패키지에서만 쓴다(`ui/`는 Repository 인터페이스만)
- 화면 코드에 색·dp·sp 리터럴 금지 — `AutoLogTheme.colors/type`, `Space`, `Size`, `Radius`, `AutoLogTheme.motion`만. 화면 문구는 `strings.xml`
- 디자인 토큰 이름 대응: 문서 `space.1~9` = 코드 `Space.s1~s9`, `size.*` = `Size.*`, `radius.*` = `Radius.*`, `type.*` = `AutoLogTheme.type.*`, `motion.*` = `AutoLogTheme.motion.*`
- 사용 단계: `tier = minOf(차량 상태, 플랫폼 CarUxRestrictions)`, 판정 불가 = BACKGROUND, CarUx 연결 실패 = PEEK. FULL이 아니면 입력 잠금, 단계가 FULL에서 내려가면 백스택 `[Home]`
- 기어 P 자동 종료 대기 **5초**(`GEAR_P_WAIT_MS = 5_000`). 남은 시간은 `deadlineAt − now`로만 계산하고 화면이 타이머를 갖지 않는다
- 터치 대상 최소 76 dp, 본문 최소 28 sp, 보조 최소 22 sp, 텍스트 대비 4.5:1 이상(비텍스트 3:1)
- 시각은 epoch millis(UTC). 현재 시각은 `AppClock`을 주입받는다(`System.currentTimeMillis()` 직접 호출 금지, `AppClock.System` 정의부 제외)
- 커밋 형식 `<type>(<scope>): <한국어 요약> (<기능ID>)`, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. push는 하지 않는다(사용자가 직접)
- WSL에서 Gradle은 메모리 상한을 걸고 실행한다: `systemd-run --user --scope -p MemoryMax=10G ./gradlew …` (아래 단계의 `G` = 이 접두사를 붙인 `./gradlew`)

```bash
# 모든 작업 전에 한 번: 단축 함수
G() { systemd-run --user --scope -q -p MemoryMax=10G ./gradlew "$@"; }
```

---

## 파일 구조 (이 계획이 만드는 것)

```
AutoLog-Frontend/
├── backend/                                   (submodule)
├── settings.gradle.kts · build.gradle.kts · gradle.properties · gradlew · gradlew.bat
├── gradle/wrapper/* · gradle/fe.versions.toml
├── .editorconfig · .gitignore · local.properties(커밋 안 함)
├── .github/workflows/ci.yml
├── docs/dev-setup.md · docs/verification.md
└── vehicle-app/
    ├── build.gradle.kts · proguard-rules.pro
    └── src/
        ├── main/AndroidManifest.xml
        ├── main/res/values/{strings,colors,themes}.xml · values-night/colors.xml
        ├── main/res/font/asta_sans.ttf · main/assets/licenses/asta_sans_OFL.txt
        ├── main/kotlin/kr/autolog/vehicle/
        │   ├── AutoLogApp.kt · MainActivity.kt
        │   ├── common/AppClock.kt · common/FlowExt.kt
        │   ├── data/interim/InterimContracts.kt · data/interim/FakeTripSession.kt
        │   ├── data/usage/UsageTier.kt · CarUxSource.kt · AndroidCarUxSource.kt · VehicleUsageRepository.kt
        │   ├── data/repository/SessionRepository.kt · ProfileRepository.kt · ContextRepository.kt
        │   ├── di/AppGraph.kt
        │   ├── nav/Route.kt · nav/Navigator.kt
        │   └── ui/
        │       ├── design/Colors.kt · Contrast.kt · Type.kt · Dimens.kt · Motion.kt · Theme.kt
        │       │         WindowLayout.kt · Format.kt · Texts.kt · Haptics.kt · SharedAvatar.kt
        │       ├── design/components/Press.kt · Buttons.kt · StatusChip.kt · HeroMetric.kt
        │       │                    Avatar.kt · ProfileTile.kt · Banner.kt · Overlay.kt
        │       ├── root/AutoLogRoot.kt · root/FullApp.kt
        │       ├── peek/PeekUiState.kt · PeekViewModel.kt · PeekScreen.kt
        │       ├── profile/ProfilePickerUiState.kt · ProfilePickerViewModel.kt · ProfilePickerScreen.kt
        │       └── home/HomeUiState.kt · HomeViewModel.kt · HomeScreen.kt · AutoEndPrompt.kt · RecoveryDialog.kt
        ├── demo/kotlin/kr/autolog/vehicle/di/FlavorGraph.kt
        ├── demo/kotlin/kr/autolog/vehicle/demo/DemoScenario.kt · DemoPanel.kt
        ├── demo/res/values/strings.xml
        ├── vehicle/kotlin/kr/autolog/vehicle/di/FlavorGraph.kt
        └── test/kotlin/kr/autolog/vehicle/ … (작업마다 명시) · test/screenshots/*.png
```

---

### Task 1: WSL 도구 설치 + 리포 골격 + composite build

**Files:**
- Create: `.gitmodules`(git이 생성), `backend/`(submodule), `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/fe.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `.editorconfig`, `.gitignore`, `vehicle-app/build.gradle.kts`, `vehicle-app/proguard-rules.pro`, `vehicle-app/src/main/AndroidManifest.xml`, `vehicle-app/src/main/kotlin/kr/autolog/vehicle/MainActivity.kt`, `vehicle-app/src/main/res/values/strings.xml`, `.github/workflows/ci.yml`, `docs/dev-setup.md`, `docs/verification.md`
- Test: `vehicle-app/src/test/kotlin/kr/autolog/vehicle/ContractsVisibleTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces: 빌드되는 `:vehicle-app` 모듈(flavor `demo`/`vehicle`), 카탈로그 `libs`·`fe`, `kr.autolog.vehicle.core.*`·`kr.autolog.vehicle.fake.*` 사용 가능

- [ ] **Step 1: JDK 17 설치 (sudo 불필요)**

```bash
mkdir -p ~/opt && cd ~/opt
curl -L -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf jdk17.tar.gz && rm jdk17.tar.gz
ln -sfn "$(ls -d ~/opt/jdk-17* | head -1)" ~/opt/jdk17
~/opt/jdk17/bin/java -version
```
Expected: `openjdk version "17.…"`

- [ ] **Step 2: Android SDK 설치**

```bash
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
curl -L -o tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
python3 -c "import zipfile; zipfile.ZipFile('tools.zip').extractall('.')" && mv cmdline-tools latest && rm tools.zip
chmod +x latest/bin/*
cat >> ~/.bashrc <<'EOF'
export JAVA_HOME=$HOME/opt/jdk17
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
EOF
source ~/.bashrc
yes | sdkmanager --licenses > /dev/null
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
ls $ANDROID_HOME/platforms/android-35/optional/
```
Expected: 마지막 줄 출력에 `android.car.jar`가 있다(없으면 `docs/verification.md`에 기록하고 Task 7의 `useLibrary("android.car")`를 확인한다).

- [ ] **Step 3: 브랜치와 submodule**

```bash
cd ~/code/AutoLog-Frontend
git checkout -b feat/foundation   # 옛 이름 fe/vehicle-app-foundation (2026-11-09 변경)
git submodule add https://github.com/hmg-app-challenge-2026/AutoLog-Backend backend
git -C backend log --oneline -1
```
Expected: Backend `main` 최신 커밋 한 줄(예: `49eddf2 Merge pull request #13 …`).

- [ ] **Step 4: Gradle wrapper·공통 설정 복사 (Backend와 같은 값)**

```bash
cp -r backend/gradle/wrapper gradle/ 2>/dev/null || (mkdir -p gradle && cp -r backend/gradle/wrapper gradle/)
cp backend/gradlew backend/gradlew.bat . && chmod +x gradlew
cp backend/.editorconfig .editorconfig
cp backend/gradle.properties gradle.properties
cp backend/.gitignore .gitignore
printf '\n# Roborazzi 비교 산출물(기준 이미지는 src/test/screenshots에 커밋)\nvehicle-app/build/outputs/roborazzi/\n' >> .gitignore
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

- [ ] **Step 5: `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/") // Pleos SDK (vehicle-core 전이 의존)
    }
    versionCatalogs {
        create("libs") { from(files("backend/gradle/libs.versions.toml")) } // Backend와 같은 버전 (스펙 D-04)
        create("fe") { from(files("gradle/fe.versions.toml")) } // 프론트 전용
    }
}
rootProject.name = "AutoLog-Frontend"

includeBuild("backend") {
    dependencySubstitution {
        substitute(module("kr.autolog:vehicle-core")).using(project(":vehicle-core"))
    }
}
include(":vehicle-app")
```

- [ ] **Step 6: `build.gradle.kts`(루트)와 `gradle/fe.versions.toml`**

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(fe.plugins.roborazzi) apply false
}

// 모든 모듈에 ktlint (Backend와 같은 설정)
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        filter { exclude { it.file.path.contains("/build/") } }
    }
}
```

```toml
# gradle/fe.versions.toml — Backend 카탈로그와 같은 시기의 안정판만 (스펙 5.3 버전 선정 기준)
[versions]
coil = "2.7.0"
zxing = "3.5.3"
turbine = "1.2.0"
robolectric = "4.14.1"
roborazzi = "1.32.2"
androidx-test-ext = "1.2.1"

[libraries]
compose-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
zxing-core = { module = "com.google.zxing:core", version.ref = "zxing" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-ext" }

[plugins]
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

- [ ] **Step 7: `vehicle-app/build.gradle.kts`, `proguard-rules.pro`**

```kotlin
// 차량 앱 화면 (Compose). 로직은 backend/vehicle-core (composite build).
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
    implementation("kr.autolog:vehicle-core") // composite build가 backend/:vehicle-core로 치환

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
```

```
# vehicle-app/proguard-rules.pro
# vehicle-core의 consumer-rules가 병합된다. 앱 전용 규칙은 필요할 때 추가한다.
```

- [ ] **Step 8: 최소 Manifest·Activity·문자열**

```xml
<!-- vehicle-app/src/main/AndroidManifest.xml -->
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.hardware.type.automotive" android:required="false" />
    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar"
        android:usesCleartextTraffic="true">
        <uses-library android:name="android.car" android:required="false" />
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:resizeableActivity="true"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|density|uiMode"
            android:windowSoftInputMode="adjustResize">
            <!-- screenOrientation은 지정하지 않는다 (스펙 D-08). DO 선언 이유·결정 규칙은 스펙 5.4, V-03·V-11 -->
            <meta-data android:name="distractionOptimized" android:value="true" />
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

```kotlin
// vehicle-app/src/main/kotlin/kr/autolog/vehicle/MainActivity.kt — Task 12에서 교체
package kr.autolog.vehicle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("AutoLog") }
    }
}
```

```xml
<!-- vehicle-app/src/main/res/values/strings.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">AutoLog</string>
</resources>
```

- [ ] **Step 9: 실패하는 연결 테스트 작성**

```kotlin
// vehicle-app/src/test/kotlin/kr/autolog/vehicle/ContractsVisibleTest.kt
package kr.autolog.vehicle

import kr.autolog.vehicle.fake.FakeContext
import org.junit.Assert.assertEquals
import org.junit.Test

class ContractsVisibleTest {
    @Test
    fun `backend vehicle-core의 계약과 Fake를 앱에서 쓸 수 있다`() {
        val context = FakeContext()

        val label = context.destination.value?.label

        assertEquals("판교 테크노밸리 거래처", label)
    }
}
```

- [ ] **Step 10: 빌드·테스트 실행 (V-02 스파이크)**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*ContractsVisibleTest*' && G :vehicle-app:assembleDemoDebug :vehicle-app:assembleVehicleDebug`
Expected: `BUILD SUCCESSFUL`, `vehicle-app/build/outputs/apk/demo/debug/vehicle-app-demo-debug.apk` 존재.
실패하고 원인이 `vehicle-core` 해석이면 스펙 5.3의 대안(`include(":vehicle-core")` + `projectDir = file("backend/vehicle-core")`, `:shared` 동일)으로 `settings.gradle.kts`를 바꾸고 다시 실행한다. 어느 방식이 됐는지 Step 12에서 기록한다.

- [ ] **Step 11: CI**

```yaml
# .github/workflows/ci.yml
name: CI
on:
  pull_request:
  push:
    branches: [main]
jobs:
  vehicle-app:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive # backend 리포가 비공개면 secrets.BACKEND_TOKEN 으로 token: 을 지정한다
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
      - name: ktlint
        run: ./gradlew ktlintCheck
      - name: Unit tests + screenshot verify
        run: ./gradlew :vehicle-app:testDemoDebugUnitTest :vehicle-app:verifyRoborazziDemoDebug
      - name: Build
        run: ./gradlew :vehicle-app:assembleDemoDebug :vehicle-app:assembleVehicleDebug
```

- [ ] **Step 12: `docs/dev-setup.md`, `docs/verification.md`**

````markdown
<!-- docs/dev-setup.md -->
# 개발 환경

## WSL (빌드·테스트·스크린샷)
1. JDK 17·Android SDK: `docs/superpowers/plans/2026-11-09-vehicle-app-foundation.md` Task 1 Step 1~2
2. `git submodule update --init --recursive`
3. `echo "sdk.dir=$ANDROID_HOME" > local.properties`
4. Gradle은 메모리 상한을 걸고 실행한다(WSL 메모리 부족 시 세션 전체가 죽는다):
   ```bash
   G() { systemd-run --user --scope -q -p MemoryMax=10G ./gradlew "$@"; }
   G :vehicle-app:testDemoDebugUnitTest
   G :vehicle-app:recordRoborazziDemoDebug   # 스크린샷 기록 → vehicle-app/src/test/screenshots
   ```
5. WSL에서는 Pleos 에뮬레이터를 돌리지 않는다(외장 GPU 필요).

## 맥 (Apple Silicon, 에뮬레이터·시연)
1. Android Studio 최신, JDK 17
2. Pleos 에뮬레이터: 스펙 5.5 "맥 에뮬레이터 준비" 1~5 (SDK Update Site `crp-arm64-sys-img.xml`, API 34, **Cold boot**, RAM 4 GB)
3. `git clone --recurse-submodules …` 후 Android Studio로 루트 열기 → Build Variant `demoDebug`
4. 설치: `./gradlew :vehicle-app:installDemoDebug`
5. 스크린샷은 맥에서 기록해도 커밋하지 않는다(기준 플랫폼은 Linux)
````

```markdown
<!-- docs/verification.md -->
# 검증 기록

스펙 13장 V-xx 결과. 날짜·환경(WSL/맥 에뮬레이터/실차)을 꼭 적는다.

| ID | 결과 | 환경 | 날짜 | 메모 |
|---|---|---|---|---|
| V-01 | | 맥 에뮬레이터 | | FormFactorConsole 프리셋별 dp·DPI·px |
| V-02 | (Task 1 Step 10 결과: dependencySubstitution 성공 / include 대안 사용) | WSL | 2026-11-.. | |
| V-03 | | 맥 에뮬레이터 | | |
| V-04 | | 맥 에뮬레이터 | | |
| V-05 | | WSL | | |
| V-06 | | 맥 에뮬레이터 | | |
| V-07 | | 실차 | | |
| V-08 | | 맥 에뮬레이터 | | |
| V-09 | | WSL | | |
| V-10 | | WSL | | |
| V-11 | | 맥 에뮬레이터 | | |
| V-12 | | 맥 에뮬레이터 | | |
| V-13 | | 맥 에뮬레이터 | | |
| V-14 | | — | | Backend sdk-findings #5 |
| V-15 | | 맥 에뮬레이터 | | |
| V-16 | | 맥 에뮬레이터 | | |
```
V-02 행의 결과·날짜를 Step 10 결과로 채운다.

- [ ] **Step 13: ktlint 통과 확인 후 커밋**

Run: `G ktlintFormat ktlintCheck`
Expected: `BUILD SUCCESSFUL`

```bash
git add .gitmodules backend settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat \
  .editorconfig .gitignore vehicle-app .github docs/dev-setup.md docs/verification.md
git commit -m "build(app): 차량 앱 모듈과 Backend composite build 골격

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 색 토큰과 대비 테스트

**Files:**
- Create: `vehicle-app/src/main/kotlin/kr/autolog/vehicle/ui/design/Colors.kt`, `.../ui/design/Contrast.kt`
- Test: `vehicle-app/src/test/kotlin/kr/autolog/vehicle/ui/design/ContrastTest.kt`

**Interfaces:**
- Produces: `data class AutoLogColors(...)`, `val DarkColors`, `val LightColors`, `val AvatarGradients: List<Pair<Color, Color>>`, `fun contrastRatio(a: Color, b: Color): Double`

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// vehicle-app/src/test/kotlin/kr/autolog/vehicle/ui/design/ContrastTest.kt
package kr.autolog.vehicle.ui.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private fun assertAtLeast(name: String, fg: Color, backgrounds: List<Color>, min: Double) {
        backgrounds.forEach { bg ->
            val ratio = contrastRatio(fg, bg)
            assertTrue("$name 대비 $ratio < $min", ratio >= min)
        }
    }

    private fun AutoLogColors.textTokens() =
        mapOf(
            "textPrimary" to textPrimary, "textSecondary" to textSecondary, "textTertiary" to textTertiary,
            "accent" to accent, "success" to success, "warning" to warning, "danger" to danger,
        )

    @Test
    fun `흰색과 검정의 대비는 21이다`() {
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), 0.01)
    }

    @Test
    fun `다크 텍스트 토큰은 모든 서피스에서 4점5 이상`() {
        DarkColors.textTokens().forEach { (name, c) -> assertAtLeast("dark.$name", c, DarkColors.surfaces, 4.5) }
    }

    @Test
    fun `라이트 텍스트 토큰은 모든 서피스에서 4점5 이상`() {
        LightColors.textTokens().forEach { (name, c) -> assertAtLeast("light.$name", c, LightColors.surfaces, 4.5) }
    }

    @Test
    fun `버튼 글자는 채움색 위에서 4점5 이상`() {
        listOf(DarkColors, LightColors).forEach {
            assertAtLeast("onAccent", it.onAccent, listOf(it.accentFill), 4.5)
            assertAtLeast("onDanger", it.onAccent, listOf(it.dangerFill), 4.5)
        }
    }

    @Test
    fun `비텍스트 요소는 모든 서피스에서 3 이상`() {
        listOf(DarkColors, LightColors).forEach {
            assertAtLeast("outlineStrong", it.outlineStrong, it.surfaces, 3.0)
            assertAtLeast("accentFill", it.accentFill, it.surfaces, 3.0)
            assertAtLeast("dangerFill", it.dangerFill, it.surfaces, 3.0)
        }
    }

    @Test
    fun `아바타 그라데이션 양 끝에서 흰 이니셜이 4점5 이상`() {
        AvatarGradients.forEachIndexed { i, (start, end) ->
            assertAtLeast("avatar$i", Color.White, listOf(start, end), 4.5)
        }
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*ContrastTest*'`
Expected: FAIL — `Unresolved reference: contrastRatio`

- [ ] **Step 3: 구현**

```kotlin
// vehicle-app/src/main/kotlin/kr/autolog/vehicle/ui/design/Contrast.kt
package kr.autolog.vehicle.ui.design

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG 2.x 상대 휘도. 디자인 언어 5.6 검증용. */
private fun channel(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

fun relativeLuminance(color: Color): Double =
    0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val (hi, lo) = if (la >= lb) la to lb else lb to la
    return (hi + 0.05) / (lo + 0.05)
}
```

```kotlin
// vehicle-app/src/main/kotlin/kr/autolog/vehicle/ui/design/Colors.kt
package kr.autolog.vehicle.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 디자인 언어 5장. 값을 바꾸면 ContrastTest가 먼저 통과해야 한다. */
@Immutable
data class AutoLogColors(
    val isDark: Boolean,
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentFill: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerFill: Color,
    val scrim: Color,
) {
    val surfaces: List<Color> get() = listOf(bg, surface1, surface2, surface3)
}

val DarkColors =
    AutoLogColors(
        isDark = true,
        bg = Color(0xFF0A0B0D),
        surface1 = Color(0xFF131518),
        surface2 = Color(0xFF1B1E22),
        surface3 = Color(0xFF252930),
        outline = Color(0xFF2E333A),
        outlineStrong = Color(0xFF6B7280),
        textPrimary = Color(0xFFF4F5F7),
        textSecondary = Color(0xFFB0B6BF),
        textTertiary = Color(0xFF8D949E),
        accent = Color(0xFF5B9BFF),
        accentFill = Color(0xFF3268F8),
        onAccent = Color(0xFFFFFFFF),
        success = Color(0xFF3DD68C),
        warning = Color(0xFFF7B955),
        danger = Color(0xFFFF6B6B),
        dangerFill = Color(0xFFD93A3F),
        scrim = Color(0x8F000000), // 56%
    )

val LightColors =
    AutoLogColors(
        isDark = false,
        bg = Color(0xFFF4F5F7),
        surface1 = Color(0xFFFFFFFF),
        surface2 = Color(0xFFECEEF1),
        surface3 = Color(0xFFE1E4E9),
        outline = Color(0xFFD5D9DF),
        outlineStrong = Color(0xFF767D88),
        textPrimary = Color(0xFF0D0F12),
        textSecondary = Color(0xFF454C56),
        textTertiary = Color(0xFF59606B),
        accent = Color(0xFF1F55D1),
        accentFill = Color(0xFF2B63F0),
        onAccent = Color(0xFFFFFFFF),
        success = Color(0xFF0B6B3C),
        warning = Color(0xFF7A4E00),
        danger = Color(0xFFB02525),
        dangerFill = Color(0xFFD93A3F),
        scrim = Color(0x52000000), // 32%
    )

/** 디자인 언어 5.4. userId 해시로 고른다(Avatar.kt). */
val AvatarGradients: List<Pair<Color, Color>> =
    listOf(
        Color(0xFF3B5BDB) to Color(0xFF1E2F8C),
        Color(0xFF7048E8) to Color(0xFF3A1F8F),
        Color(0xFFC2255C) to Color(0xFF6E1236),
        Color(0xFFC2410C) to Color(0xFF8A2E06),
        Color(0xFF237A36) to Color(0xFF134A20),
        Color(0xFF0B7285) to Color(0xFF064B57),
        Color(0xFF5C6B7A) to Color(0xFF2C343D),
        Color(0xFFB5447F) to Color(0xFF5E1F44),
    )
```

- [ ] **Step 4: 통과 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*ContrastTest*'`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add vehicle-app/src/main/kotlin/kr/autolog/vehicle/ui/design/Colors.kt \
  vehicle-app/src/main/kotlin/kr/autolog/vehicle/ui/design/Contrast.kt \
  vehicle-app/src/test/kotlin/kr/autolog/vehicle/ui/design/ContrastTest.kt
git commit -m "feat(design): 다크·라이트 색 토큰과 대비 4.5:1 검증 테스트

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 서체·글자·간격·모양 토큰

**Files:**
- Create: `vehicle-app/src/main/res/font/asta_sans.ttf`, `vehicle-app/src/main/assets/licenses/asta_sans_OFL.txt`, `.../ui/design/Type.kt`, `.../ui/design/Dimens.kt`
- Test: `vehicle-app/src/test/kotlin/kr/autolog/vehicle/ui/design/TokenFloorTest.kt`

**Interfaces:**
- Produces: `val AstaSans: FontFamily`, `data class AutoLogType(hero, heroSmall, display, headline, title, body, label, caption: TextStyle)`, `fun autoLogType(): AutoLogType`, `object Space { s1..s9 }`, `object Size { touchMin, buttonMd, buttonLg, buttonXl, iconButton, icon, iconLg, avatarChip, profileTileWide, profileTileNarrow, peekMaxWidth, sheetMaxWidth }`, `object Radius { sm, md, lg, xl }`

- [ ] **Step 1: 서체 파일 받기**

```bash
mkdir -p vehicle-app/src/main/res/font vehicle-app/src/main/assets/licenses
curl -L -o vehicle-app/src/main/res/font/asta_sans.ttf "https://github.com/google/fonts/raw/main/ofl/astasans/AstaSans%5Bwght%5D.ttf"
curl -L -o vehicle-app/src/main/assets/licenses/asta_sans_OFL.txt "https://github.com/google/fonts/raw/main/ofl/astasans/OFL.txt"
file vehicle-app/src/main/res/font/asta_sans.ttf
```
Expected: `TrueType Font data` (HTML이면 URL 오류 — 다시 받는다)

- [ ] **Step 2: 실패하는 테스트**

```kotlin
// vehicle-app/src/test/kotlin/kr/autolog/vehicle/ui/design/TokenFloorTest.kt
package kr.autolog.vehicle.ui.design

import org.junit.Assert.assertTrue
import org.junit.Test

/** 디자인 언어 4·6장의 하한을 숫자로 강제한다. */
class TokenFloorTest {
    private val type = autoLogType()

    @Test
    fun `본문과 버튼 글자는 28sp 이상`() {
        assertTrue(type.body.fontSize.value >= 28f)
        assertTrue(type.label.fontSize.value >= 28f)
    }

    @Test
    fun `주요 정보 글자는 36sp 이상`() {
        assertTrue(type.title.fontSize.value >= 36f)
    }

    @Test
    fun `가장 작은 글자는 caption이고 22sp 이상`() {
        val all = listOf(type.hero, type.heroSmall, type.display, type.headline, type.title, type.body, type.label)
        assertTrue(all.all { it.fontSize.value >= type.caption.fontSize.value })
        assertTrue(type.caption.fontSize.value >= 22f)
    }

    @Test
    fun `숫자 글자는 고정폭 숫자를 쓴다`() {
        assertTrue(type.hero.fontFeatureSettings == "tnum")
        assertTrue(type.heroSmall.fontFeatureSettings == "tnum")
    }

    @Test
    fun `터치 대상과 버튼은 76dp 이상`() {
        listOf(Size.touchMin, Size.buttonMd, Size.buttonLg, Size.buttonXl, Size.iconButton)
            .forEach { assertTrue("$it", it.value >= 76f) }
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*TokenFloorTest*'`
Expected: FAIL — `Unresolved reference: autoLogType`

- [ ] **Step 4: 구현**

```kotlin
// vehicle-app/src/main/kotlin/kr/autolog/vehicle/ui/design/Type.kt
package kr.autolog.vehicle.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kr.autolog.vehicle.R

@OptIn(ExperimentalTextApi::class)
private fun astaSans(weight: Int) =
    Font(
        R.font.asta_sans,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

/** Asta Sans (42dot, OFL, 가변 300–800). 디자인 언어 6.1. */
val AstaSans = FontFamily(astaSans(300), astaSans(400), astaSans(500), astaSans(600), astaSans(700))

/** 디자인 언어 6.2. */
@Immutable
data class AutoLogType(
    val hero: TextStyle,
    val heroSmall: TextStyle,
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

private fun style(sizeSp: Int, weight: Int, lineHeight: Float, trackingEm: Float, tnum: Boolean = false) =
    TextStyle(
        fontFamily = AstaSans,
        fontWeight = FontWeight(weight),
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * lineHeight).sp,
        letterSpacing = trackingEm.em,
        fontFeatureSettings = if (tnum) "tnum" else null,
    )

fun autoLogType(): AutoLogType =
    AutoLogType(
        hero = style(140, 300, 1.0f, -0.02f, tnum = true),
        heroSmall = style(96, 300, 1.0f, -0.015f, tnum = true),
        display = style(56, 600, 1.15f, -0.01f),
        headline = style(44, 600, 1.2f, -0.005f),
        title = style(36, 600, 1.25f, 0f),
        body = style(28, 400, 1.4f, 0f),
        label = style(28, 600, 1.2f, 0f),
        caption = style(22, 500, 1.3f, 0.005f),
    )
```

```kotlin
// vehicle-app/src/main/kotlin/kr/autolog/vehicle/ui/design/Dimens.kt
package kr.autolog.vehicle.ui.design

import androidx.compose.ui.unit.dp

/** 디자인 언어 6.4 (8 dp 그리드). 문서 space.N = Space.sN */
object Space {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 24.dp
    val s6 = 32.dp
    val s7 = 48.dp
    val s8 = 64.dp
    val s9 = 96.dp
    val tallMargin = 40.dp
}

/** 디자인 언어 6.5. */
object Size {
    val touchMin = 76.dp
    val buttonMd = 76.dp
    val buttonLg = 96.dp
    val buttonXl = 120.dp
    val iconButton = 96.dp
    val icon = 36.dp
    val iconLg = 48.dp
    val avatarChip = 48.dp
    val chipHeight = 76.dp // StatusChip 터치 최소 높이(시각 높이 56 dp는 안쪽 여백으로)
    val profileTileWide = 200.dp
    val profileTileNarrow = 160.dp
    val peekMaxWidth = 560.dp
    val sheetMaxWidth = 960.dp
    val progressBar = 8.dp
    val ring = 3.dp
}

/** 디자인 언어 6.6. */
object Radius {
    val sm = 14.dp
    val md = 20.dp
    val lg = 28.dp
    val xl = 40.dp
}
```

- [ ] **Step 5: 통과 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*TokenFloorTest*'`
Expected: PASS (5 tests)

- [ ] **Step 6: 커밋**

```bash
git add vehicle-app/src/main/res/font vehicle-app/src/main/assets/licenses \
  vehicle-app/src/main/kotlin/kr/autolog/vehicle/ui/design/Type.kt \
  vehicle-app/src/main/kotlin/kr/autolog/vehicle/ui/design/Dimens.kt \
  vehicle-app/src/test/kotlin/kr/autolog/vehicle/ui/design/TokenFloorTest.kt
git commit -m "feat(design): Asta Sans 타입 스케일과 간격·크기·모서리 토큰

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 모션 토큰·테마·창 레이아웃·눌림 피드백

**Files:**
- Create: `.../ui/design/Motion.kt`, `.../ui/design/WindowLayout.kt`, `.../ui/design/Theme.kt`, `.../ui/design/Haptics.kt`, `.../ui/design/components/Press.kt`, `vehicle-app/src/main/res/values/colors.xml`, `.../res/values-night/colors.xml`, `.../res/values/themes.xml`
- Modify: `vehicle-app/src/main/AndroidManifest.xml` (테마 `@style/Theme.AutoLog`)
- Test: `.../test/.../ui/design/MotionTokensTest.kt`, `.../test/.../ui/design/WindowLayoutTest.kt`

**Interfaces:**
- Consumes: `AutoLogColors`, `DarkColors`, `LightColors`, `autoLogType()`, `AutoLogType`
- Produces:
  - `class MotionTokens { val isStatic: Boolean; fun <T> standard(); gentle(); snappy(); fadeQuick(); fadeStandard(): FiniteAnimationSpec<T>; val pressScale: Float; companion { Standard; Static } }`
  - `enum class WindowLayout { NARROW, WIDE, TALL }`, `fun windowLayoutOf(widthDp: Float, heightDp: Float): WindowLayout`, `enum class DriverSide { START, END }`
  - `val LocalWindowLayout`, `LocalDriverSide`, `LocalInputLocked: ProvidableCompositionLocal<Boolean>`
  - `@Composable fun AutoLogTheme(darkTheme: Boolean = isSystemInDarkTheme(), staticMotion: Boolean = false, content)`, `object AutoLogTheme { colors; type; motion }`, `@Composable fun WindowLayoutHost(content)`
  - `fun Modifier.pressFeedback(interaction: MutableInteractionSource): Modifier`, `fun View.confirmHaptic()`

- [ ] **Step 1: 실패하는 테스트 2개**

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/design/MotionTokensTest.kt
package kr.autolog.vehicle.ui.design

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTokensTest {
    @Test
    fun `정차 이상 단계의 정지형 세트는 위치·크기 변화를 즉시 끝낸다`() {
        assertTrue(MotionTokens.Static.standard<Float>() is SnapSpec)
        assertTrue(MotionTokens.Static.gentle<Float>() is SnapSpec)
        assertEquals(1f, MotionTokens.Static.pressScale)
    }

    @Test
    fun `주차 단계는 오버슈트 없는 스프링을 쓴다`() {
        val spec = MotionTokens.Standard.standard<Float>() as SpringSpec<Float>
        assertEquals(0.9f, spec.dampingRatio)
        assertEquals(400f, spec.stiffness)
        assertEquals(0.97f, MotionTokens.Standard.pressScale)
    }
}
```

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/design/WindowLayoutTest.kt
package kr.autolog.vehicle.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutTest {
    @Test
    fun `가로로 넓은 창은 WIDE`() {
        assertEquals(WindowLayout.WIDE, windowLayoutOf(1050f, 840f)) // aspect 1.25, width 840 이상
        assertEquals(WindowLayout.WIDE, windowLayoutOf(1024f, 640f))
    }

    @Test
    fun `가로 비율이어도 840dp 미만이면 NARROW`() {
        assertEquals(WindowLayout.NARROW, windowLayoutOf(839f, 500f))
    }

    @Test
    fun `세로로 긴 창은 TALL`() {
        assertEquals(WindowLayout.TALL, windowLayoutOf(800f, 1000f)) // aspect 0.80
        assertEquals(WindowLayout.TALL, windowLayoutOf(720f, 1100f))
    }

    @Test
    fun `분할 화면 한 칸과 정사각에 가까운 창은 NARROW`() {
        assertEquals(WindowLayout.NARROW, windowLayoutOf(504f, 600f)) // aspect 0.84
        assertEquals(WindowLayout.NARROW, windowLayoutOf(900f, 800f)) // aspect 1.125
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*MotionTokensTest*' --tests '*WindowLayoutTest*'`
Expected: FAIL — `Unresolved reference: MotionTokens`, `windowLayoutOf`

- [ ] **Step 3: 구현 — 모션·창 레이아웃**

```kotlin
// .../ui/design/Motion.kt
package kr.autolog.vehicle.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * 디자인 언어 8.2·8.4. [Static]은 tier != FULL(정차 Peek) 또는 시스템 "애니메이션 줄이기"일 때 쓴다.
 * 컴포넌트는 이 토큰만 쓰므로 단계가 바뀌면 자동으로 정지형이 된다.
 */
@Immutable
class MotionTokens private constructor(val isStatic: Boolean) {
    fun <T> standard(): FiniteAnimationSpec<T> = if (isStatic) snap() else spring(dampingRatio = 0.9f, stiffness = 400f)

    fun <T> gentle(): FiniteAnimationSpec<T> = if (isStatic) snap() else spring(dampingRatio = 1.0f, stiffness = 200f)

    fun <T> snappy(): FiniteAnimationSpec<T> = if (isStatic) snap() else spring(dampingRatio = 0.85f, stiffness = 700f)

    /** 숫자 교체·작은 요소. 정지형에서도 FULL→PEEK 전환 한 번은 이 값까지 허용. */
    fun <T> fadeQuick(): FiniteAnimationSpec<T> = tween(durationMillis = 150, easing = LinearOutSlowInEasing)

    fun <T> fadeStandard(): FiniteAnimationSpec<T> =
        if (isStatic) tween(150, easing = LinearOutSlowInEasing) else tween(220, easing = FastOutSlowInEasing)

    val pressScale: Float get() = if (isStatic) 1f else 0.97f

    companion object {
        val Standard = MotionTokens(isStatic = false)
        val Static = MotionTokens(isStatic = true)
    }
}
```

```kotlin
// .../ui/design/WindowLayout.kt
package kr.autolog.vehicle.ui.design

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** 스펙 6.8. 임계값은 잠정값(V-01) — FormFactorConsole 측정 후 고친다. */
enum class WindowLayout { NARROW, WIDE, TALL }

const val WIDE_MIN_ASPECT = 1.25f
const val WIDE_MIN_WIDTH_DP = 840f
const val TALL_MAX_ASPECT = 0.80f

fun windowLayoutOf(widthDp: Float, heightDp: Float): WindowLayout {
    val aspect = widthDp / heightDp
    return when {
        aspect >= WIDE_MIN_ASPECT && widthDp >= WIDE_MIN_WIDTH_DP -> WindowLayout.WIDE
        aspect <= TALL_MAX_ASPECT -> WindowLayout.TALL
        else -> WindowLayout.NARROW
    }
}

/** 디자인 언어 6.3. 한국 좌핸들 = START. */
enum class DriverSide { START, END }

val LocalWindowLayout = compositionLocalOf { WindowLayout.NARROW }
val LocalDriverSide = staticCompositionLocalOf { DriverSide.START }

/** 현재 창 크기로 [LocalWindowLayout]을 계산해 내려 준다. 루트와 스크린샷 테스트가 쓴다. */
@Composable
fun WindowLayoutHost(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = windowLayoutOf(maxWidth.value, maxHeight.value)
        CompositionLocalProvider(LocalWindowLayout provides layout) { content() }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*MotionTokensTest*' --tests '*WindowLayoutTest*'`
Expected: PASS (6 tests)

- [ ] **Step 5: 구현 — 테마·햅틱·눌림 피드백 (컴파일로 검증)**

```kotlin
// .../ui/design/Theme.kt
package kr.autolog.vehicle.ui.design

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalAutoLogColors = staticCompositionLocalOf { DarkColors }
val LocalAutoLogType = staticCompositionLocalOf { autoLogType() }
val LocalMotion = staticCompositionLocalOf { MotionTokens.Standard }

/** 스펙 6.9 4번: tier != FULL 또는 FULL 진입 직후 300 ms. 모든 상호작용 컴포넌트가 읽는다. */
val LocalInputLocked = compositionLocalOf { false }

object AutoLogTheme {
    val colors: AutoLogColors
        @Composable @ReadOnlyComposable
        get() = LocalAutoLogColors.current
    val type: AutoLogType
        @Composable @ReadOnlyComposable
        get() = LocalAutoLogType.current
    val motion: MotionTokens
        @Composable @ReadOnlyComposable
        get() = LocalMotion.current
}

@Composable
private fun systemAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** 디자인 언어 5.5 M3 매핑. 화면 코드는 MaterialTheme이 아니라 AutoLogTheme.colors를 쓴다. */
private fun AutoLogColors.toMaterial() =
    if (isDark) {
        darkColorScheme(
            primary = accentFill, onPrimary = onAccent, background = bg, surface = bg,
            surfaceContainerLow = surface1, surfaceContainer = surface2, surfaceContainerHigh = surface3,
            onBackground = textPrimary, onSurface = textPrimary, onSurfaceVariant = textSecondary,
            outline = outlineStrong, outlineVariant = outline, error = danger, onError = onAccent, scrim = scrim,
        )
    } else {
        lightColorScheme(
            primary = accentFill, onPrimary = onAccent, background = bg, surface = bg,
            surfaceContainerLow = surface1, surfaceContainer = surface2, surfaceContainerHigh = surface3,
            onBackground = textPrimary, onSurface = textPrimary, onSurfaceVariant = textSecondary,
            outline = outlineStrong, outlineVariant = outline, error = danger, onError = onAccent, scrim = scrim,
        )
    }

@Composable
fun AutoLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    staticMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val type = remember { autoLogType() }
    val motion = if (staticMotion || systemAnimationsOff()) MotionTokens.Static else MotionTokens.Standard
    CompositionLocalProvider(
        LocalAutoLogColors provides colors,
        LocalAutoLogType provides type,
        LocalMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = Typography(bodyLarge = type.body, labelLarge = type.label, titleLarge = type.title),
            content = content,
        )
    }
}
```

```kotlin
// .../ui/design/Haptics.kt
package kr.autolog.vehicle.ui.design

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** 디자인 언어 8.3. 헤드유닛 지원 여부는 V-07 — 지원 안 하면 조용히 무시된다. */
fun View.confirmHaptic() {
    val constant = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    performHapticFeedback(constant)
}
```

```kotlin
// .../ui/design/components/Press.kt
package kr.autolog.vehicle.ui.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kr.autolog.vehicle.ui.design.AutoLogTheme

/** 디자인 언어 8.2 press: 누름 0.97배 tween(90) / 뗌 spring.snappy. 정지형이면 크기 변화 없음. */
fun Modifier.pressFeedback(interaction: MutableInteractionSource): Modifier =
    composed {
        val pressed by interaction.collectIsPressedAsState()
        val motion = AutoLogTheme.motion
        val scale by animateFloatAsState(
            targetValue = if (pressed) motion.pressScale else 1f,
            animationSpec = if (pressed) tween(90) else motion.snappy(),
            label = "press",
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }
```

```xml
<!-- vehicle-app/src/main/res/values/colors.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="window_bg">#F4F5F7</color>
</resources>
```

```xml
<!-- vehicle-app/src/main/res/values-night/colors.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="window_bg">#0A0B0D</color>
</resources>
```

```xml
<!-- vehicle-app/src/main/res/values/themes.xml — 첫 프레임 배경을 토큰 bg와 맞춰 깜빡임을 없앤다 -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.AutoLog" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/window_bg</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

Manifest의 `android:theme="@android:style/Theme.Material.NoActionBar"`를 `android:theme="@style/Theme.AutoLog"`로 바꾼다.

- [ ] **Step 6: 빌드 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest :vehicle-app:assembleDemoDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 커밋**

```bash
git add vehicle-app/src/main vehicle-app/src/test
git commit -m "feat(design): 모션 토큰·테마·창 레이아웃 3단계·눌림 피드백

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 공용 시계·흐름 유틸과 앱 A interim 계약 + FakeTripSession

**Files:**
- Create: `.../common/AppClock.kt`, `.../common/FlowExt.kt`, `.../data/interim/InterimContracts.kt`, `.../data/interim/FakeTripSession.kt`
- Test: `.../test/.../common/FlowExtTest.kt`, `.../test/.../data/interim/FakeTripSessionTest.kt`

**Interfaces:**
- Consumes: `kr.autolog.vehicle.core.PurposeDetail`, `DriverSource`, `Suggestion`
- Produces:
  - `fun interface AppClock { fun nowMs(): Long; companion object { val System: AppClock } }`
  - `fun tickerFlow(clock: AppClock, periodMs: Long = 1_000): Flow<Long>`, `fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T>`
  - `enum class VehicleMotion { MOVING, STOPPED, PARKED }`, `enum class StopReason { GEAR_P, NO_MOVEMENT, NAVI_ARRIVED, USER_REQUEST }`, `enum class InputMode { TAP, VOICE }`, `enum class RecoveryChoice { RECORD, CONTINUE, DELETE }`
  - `data class LiveTrip(tripId, startTime, startOdometerKm: Double?, destinationLabel: String?, driverUserId: String?)`
  - `sealed interface SessionState { Idle; Recording(trip, liveKm: Double?, lastSavedAt: Long); StopCandidate(trip, liveKm: Double?, reason, since: Long, deadlineAt: Long); PendingReview(trip, distanceKm: Double?, suggestion: Suggestion?); NeedsRecovery(trip, extraKm: Double?) }`
  - `data class MissedTrip(gapKm: Double, lastKnownAt: Long)`, `sealed interface StartResult { Started(tripId); Failed(reason) }`
  - `interface TripSessionApi` (아래 코드), `class FakeTripSession(scope, clock, initialOdometerKm = 18_420.5, gearPWaitMs = GEAR_P_WAIT_MS)` + 데모 조작 `setMotion(VehicleMotion?)`, `addKm(Double)`, `setMissedTrip(MissedTrip?)`, `enterRecovery(extraKm: Double?)`, `const val GEAR_P_WAIT_MS = 5_000L`

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// .../test/kotlin/kr/autolog/vehicle/common/FlowExtTest.kt
package kr.autolog.vehicle.common

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowExtTest {
    @Test
    fun `1초 안에 여러 번 바뀌면 첫 값과 마지막 값만 내보낸다`() =
        runTest {
            val source =
                flow {
                    emit(1)
                    delay(100)
                    emit(2)
                    delay(100)
                    emit(3)
                }

            source.throttleLatest(1_000).test {
                assertEquals(1, awaitItem())
                assertEquals(3, awaitItem())
                awaitComplete()
            }
        }
}
```

```kotlin
// .../test/kotlin/kr/autolog/vehicle/data/interim/FakeTripSessionTest.kt
package kr.autolog.vehicle.data.interim

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.common.AppClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeTripSessionTest {
    private fun TestScope.session() = FakeTripSession(backgroundScope, AppClock { testScheduler.currentTime })

    @Test
    fun `기어 P면 5초 카운트다운을 시작하고 아무것도 안 누르면 기록이 끝난다`() =
        runTest {
            val s = session()
            s.startSession()
            s.addKm(23.4)

            s.setMotion(VehicleMotion.PARKED)

            val candidate = s.state.value as SessionState.StopCandidate
            assertEquals(StopReason.GEAR_P, candidate.reason)
            assertEquals(candidate.since + 5_000, candidate.deadlineAt)
            advanceTimeBy(5_001)
            runCurrent()
            val review = s.state.value as SessionState.PendingReview
            assertEquals(23.4, review.distanceKm!!, 0.001)
        }

    @Test
    fun `계속 기록을 누르면 자동 종료가 취소된다`() =
        runTest {
            val s = session()
            s.startSession()
            s.setMotion(VehicleMotion.PARKED)

            s.resume()
            advanceTimeBy(6_000)
            runCurrent()

            assertTrue(s.state.value is SessionState.Recording)
        }

    @Test
    fun `기록 종료를 누르면 기다리지 않고 바로 끝난다`() =
        runTest {
            val s = session()
            s.startSession()
            s.setMotion(VehicleMotion.PARKED)

            s.finalize()

            assertTrue(s.state.value is SessionState.PendingReview)
        }

    @Test
    fun `카운트다운 중 다시 출발하면 기록을 이어 간다`() =
        runTest {
            val s = session()
            s.startSession()
            s.setMotion(VehicleMotion.PARKED)

            s.setMotion(VehicleMotion.MOVING)
            advanceTimeBy(6_000)
            runCurrent()

            assertTrue(s.state.value is SessionState.Recording)
        }

    @Test
    fun `운행이 없을 때 출발하면 자동으로 기록을 시작한다`() =
        runTest {
            val s = session()

            s.setMotion(VehicleMotion.MOVING)
            runCurrent()

            assertTrue(s.state.value is SessionState.Recording)
        }

    @Test
    fun `거리를 더하면 누적거리와 이번 운행 거리가 함께 는다`() =
        runTest {
            val s = session()
            s.startSession()

            s.addKm(1.5)

            assertEquals(18_422.0, s.totalOdometerKm.value!!, 0.001)
            assertEquals(1.5, (s.state.value as SessionState.Recording).liveKm!!, 0.001)
        }
}
```

- [ ] **Step 2: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*FlowExtTest*' --tests '*FakeTripSessionTest*'`
Expected: FAIL — `Unresolved reference`

- [ ] **Step 3: 구현**

```kotlin
// .../common/AppClock.kt
package kr.autolog.vehicle.common

/** 현재 시각(epoch millis, UTC). 테스트에서 바꿔 끼운다(Backend CONVENTIONS 7.4). */
fun interface AppClock {
    fun nowMs(): Long

    companion object {
        val System = AppClock { java.lang.System.currentTimeMillis() }
    }
}
```

```kotlin
// .../common/FlowExt.kt
package kr.autolog.vehicle.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform

/** [periodMs]마다 현재 시각. 카운트다운·경과 시간 표시용. */
fun tickerFlow(clock: AppClock, periodMs: Long = 1_000): Flow<Long> =
    flow {
        while (true) {
            emit(clock.nowMs())
            delay(periodMs)
        }
    }

/** 첫 값은 바로, 이후는 [periodMs]에 최대 한 번(가장 최근 값). 스펙 6.11 거리 표시 1초 스로틀. */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> =
    conflate().transform {
        emit(it)
        delay(periodMs)
    }
```

```kotlin
// .../data/interim/InterimContracts.kt
package kr.autolog.vehicle.data.interim

import kotlinx.coroutines.flow.StateFlow
import kr.autolog.vehicle.core.DriverSource
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.Suggestion

// 앱 A 계약 초안 (스펙 6.6, R-2·R-7). 앱 A의 TripSessionApi가 core에 머지되면 이 파일을 지우고
// Repository 구현만 고친다. ui/ 패키지는 이 파일을 import하지 않는다.

/** 기어·움직임 (스펙 1.1.1 판정 결과). null = 판정 불가 → BACKGROUND. */
enum class VehicleMotion { MOVING, STOPPED, PARKED }

enum class StopReason { GEAR_P, NO_MOVEMENT, NAVI_ARRIVED, USER_REQUEST }

enum class InputMode { TAP, VOICE }

enum class RecoveryChoice { RECORD, CONTINUE, DELETE }

data class LiveTrip(
    val tripId: String,
    val startTime: Long,
    val startOdometerKm: Double?,
    val destinationLabel: String?,
    val driverUserId: String?,
)

sealed interface SessionState {
    data object Idle : SessionState

    /** [liveKm] null = 계기판을 못 읽음(거리 미확정, 추정 금지). */
    data class Recording(val trip: LiveTrip, val liveKm: Double?, val lastSavedAt: Long) : SessionState

    /** 자동 종료 대기. [deadlineAt]이 유일한 시계다 — 화면은 남은 시간만 계산한다. */
    data class StopCandidate(
        val trip: LiveTrip,
        val liveKm: Double?,
        val reason: StopReason,
        val since: Long,
        val deadlineAt: Long,
    ) : SessionState

    data class PendingReview(val trip: LiveTrip, val distanceKm: Double?, val suggestion: Suggestion?) : SessionState

    data class NeedsRecovery(val trip: LiveTrip, val extraKm: Double?) : SessionState
}

data class MissedTrip(val gapKm: Double, val lastKnownAt: Long)

sealed interface StartResult {
    data class Started(val tripId: String) : StartResult

    data class Failed(val reason: String) : StartResult
}

interface TripSessionApi {
    val state: StateFlow<SessionState>
    val motion: StateFlow<VehicleMotion?>
    val totalOdometerKm: StateFlow<Double?>
    val missedTrip: StateFlow<MissedTrip?>
    val currentDriver: StateFlow<String?>

    suspend fun selectDriver(userId: String?)

    suspend fun startSession(): StartResult

    /** [기록 종료] — StopCandidate에서도 즉시 종료. */
    suspend fun finalize()

    /** [계속 기록] — StopCandidate → Recording. */
    suspend fun resume()

    suspend fun confirmPurpose(tripId: String, detail: PurposeDetail, note: String?, inputMode: InputMode)

    suspend fun assignDriver(tripId: String, userId: String, source: DriverSource)

    suspend fun acceptMissedTrip()

    suspend fun ignoreMissedTrip()

    suspend fun resolveRecovery(choice: RecoveryChoice)
}
```

```kotlin
// .../data/interim/FakeTripSession.kt
package kr.autolog.vehicle.data.interim

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.DriverSource
import kr.autolog.vehicle.core.PurposeDetail

/** 기어 P 자동 종료 대기 (사용자 결정 2026-11-09, 스펙 D-18). 실제 값은 앱 A 소유. */
const val GEAR_P_WAIT_MS = 5_000L

/**
 * 앱 A [TripSessionApi]가 올 때까지 쓰는 가짜(스펙 6.6). 데모 패널과 테스트가 setMotion/addKm로 차량을 흉내 낸다.
 * 규칙: 운행 중 PARKED → GEAR_P 카운트다운 / 카운트다운 중 MOVING → 계속 / 운행 없을 때 MOVING → 자동 시작(F-02).
 */
class FakeTripSession(
    private val scope: CoroutineScope,
    private val clock: AppClock,
    initialOdometerKm: Double? = 18_420.5,
    private val gearPWaitMs: Long = GEAR_P_WAIT_MS,
) : TripSessionApi {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    private val _motion = MutableStateFlow<VehicleMotion?>(VehicleMotion.PARKED)
    private val _odometer = MutableStateFlow(initialOdometerKm)
    private val _missed = MutableStateFlow<MissedTrip?>(null)
    private val _driver = MutableStateFlow<String?>(null)
    private var autoEndJob: Job? = null

    override val state: StateFlow<SessionState> = _state.asStateFlow()
    override val motion: StateFlow<VehicleMotion?> = _motion.asStateFlow()
    override val totalOdometerKm: StateFlow<Double?> = _odometer.asStateFlow()
    override val missedTrip: StateFlow<MissedTrip?> = _missed.asStateFlow()
    override val currentDriver: StateFlow<String?> = _driver.asStateFlow()

    override suspend fun selectDriver(userId: String?) {
        _driver.value = userId
    }

    override suspend fun startSession(): StartResult {
        if (_state.value !is SessionState.Idle) return StartResult.Failed("이미 기록 중입니다")
        val now = clock.nowMs()
        val trip = LiveTrip("trip-$now", now, _odometer.value, "판교 테크노밸리 거래처", _driver.value)
        _state.value = SessionState.Recording(trip, liveKm = if (_odometer.value == null) null else 0.0, lastSavedAt = now)
        return StartResult.Started(trip.tripId)
    }

    override suspend fun finalize() {
        autoEndJob?.cancel()
        finalizeNow()
    }

    override suspend fun resume() {
        val s = _state.value as? SessionState.StopCandidate ?: return
        autoEndJob?.cancel()
        _state.value = SessionState.Recording(s.trip, s.liveKm, clock.nowMs())
    }

    override suspend fun confirmPurpose(tripId: String, detail: PurposeDetail, note: String?, inputMode: InputMode) {
        val s = _state.value as? SessionState.PendingReview ?: return
        if (s.trip.tripId == tripId) _state.value = SessionState.Idle
    }

    override suspend fun assignDriver(tripId: String, userId: String, source: DriverSource) = Unit

    override suspend fun acceptMissedTrip() {
        _missed.value = null
    }

    override suspend fun ignoreMissedTrip() {
        _missed.value = null
    }

    override suspend fun resolveRecovery(choice: RecoveryChoice) {
        val s = _state.value as? SessionState.NeedsRecovery ?: return
        _state.value =
            when (choice) {
                RecoveryChoice.RECORD -> SessionState.PendingReview(s.trip, s.extraKm, null)
                RecoveryChoice.CONTINUE -> SessionState.Recording(s.trip, s.extraKm, clock.nowMs())
                RecoveryChoice.DELETE -> SessionState.Idle
            }
    }

    // ---- 데모·테스트 조작 ----

    fun setMotion(motion: VehicleMotion?) {
        _motion.value = motion
        val s = _state.value
        when {
            motion == VehicleMotion.PARKED && s is SessionState.Recording -> startAutoEnd(s, StopReason.GEAR_P)
            motion == VehicleMotion.MOVING && s is SessionState.StopCandidate -> {
                autoEndJob?.cancel()
                _state.value = SessionState.Recording(s.trip, s.liveKm, clock.nowMs())
            }
            motion == VehicleMotion.MOVING && s is SessionState.Idle -> scope.launch { startSession() }
            else -> Unit
        }
    }

    fun addKm(km: Double) {
        _odometer.value = _odometer.value?.plus(km)
        val s = _state.value
        if (s is SessionState.Recording) {
            _state.value = s.copy(liveKm = s.liveKm?.plus(km), lastSavedAt = clock.nowMs())
        }
    }

    fun setMissedTrip(missed: MissedTrip?) {
        _missed.value = missed
    }

    fun enterRecovery(extraKm: Double?) {
        val now = clock.nowMs()
        val trip = LiveTrip("trip-rec-$now", now - 3_600_000, _odometer.value, null, _driver.value)
        _state.value = SessionState.NeedsRecovery(trip, extraKm)
    }

    private fun startAutoEnd(s: SessionState.Recording, reason: StopReason) {
        val now = clock.nowMs()
        _state.value = SessionState.StopCandidate(s.trip, s.liveKm, reason, since = now, deadlineAt = now + gearPWaitMs)
        autoEndJob?.cancel()
        autoEndJob =
            scope.launch {
                delay(gearPWaitMs)
                if (_state.value is SessionState.StopCandidate) finalizeNow()
            }
    }

    private fun finalizeNow() {
        val (trip, km) =
            when (val s = _state.value) {
                is SessionState.Recording -> s.trip to s.liveKm
                is SessionState.StopCandidate -> s.trip to s.liveKm
                else -> return
            }
        _state.value = SessionState.PendingReview(trip, km, null)
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*FlowExtTest*' --tests '*FakeTripSessionTest*'`
Expected: PASS (7 tests)

- [ ] **Step 5: 커밋**

```bash
git add vehicle-app/src/main/kotlin/kr/autolog/vehicle/common vehicle-app/src/main/kotlin/kr/autolog/vehicle/data/interim \
  vehicle-app/src/test/kotlin/kr/autolog/vehicle/common vehicle-app/src/test/kotlin/kr/autolog/vehicle/data/interim
git commit -m "feat(repo): 앱 A 세션 계약 초안과 기어 P 5초 자동 종료 가짜 (F-02, F-04)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: 사용 단계(UsageTier)와 VehicleUsageRepository

**Files:**
- Create: `.../data/usage/UsageTier.kt`, `.../data/usage/CarUxSource.kt`, `.../data/usage/AndroidCarUxSource.kt`, `.../data/usage/VehicleUsageRepository.kt`
- Test: `.../test/.../data/usage/UsageTierTest.kt`, `.../test/.../data/usage/VehicleUsageRepositoryTest.kt`

**Interfaces:**
- Consumes: `VehicleMotion`
- Produces: `enum class UsageTier { BACKGROUND, PEEK, FULL }`, `data class CarUxState(requiresDistractionOptimization: Boolean, noKeyboard: Boolean)`, `fun usageTierOf(motion: VehicleMotion?, carUx: CarUxState?): UsageTier`, `interface CarUxSource { val state: StateFlow<CarUxState?> }`, `class StaticCarUxSource(initial: CarUxState? = CarUxState(false, false))` + `fun set(state: CarUxState?)`, `class AndroidCarUxSource(context)` + `fun start()`, `interface VehicleUsageRepository { val tier: StateFlow<UsageTier>; val noKeyboard: StateFlow<Boolean> }`, `class DefaultVehicleUsageRepository(motion: StateFlow<VehicleMotion?>, carUx: StateFlow<CarUxState?>, scope: CoroutineScope)`

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// .../test/kotlin/kr/autolog/vehicle/data/usage/UsageTierTest.kt
package kr.autolog.vehicle.data.usage

import kr.autolog.vehicle.data.interim.VehicleMotion
import org.junit.Assert.assertEquals
import org.junit.Test

/** 스펙 6.9 표의 모든 조합. */
class UsageTierTest {
    private val free = CarUxState(requiresDistractionOptimization = false, noKeyboard = false)
    private val restricted = CarUxState(requiresDistractionOptimization = true, noKeyboard = true)

    @Test
    fun `주차이고 플랫폼 제한이 없으면 전체 앱`() {
        assertEquals(UsageTier.FULL, usageTierOf(VehicleMotion.PARKED, free))
    }

    @Test
    fun `정차면 Peek`() {
        assertEquals(UsageTier.PEEK, usageTierOf(VehicleMotion.STOPPED, free))
        assertEquals(UsageTier.PEEK, usageTierOf(VehicleMotion.STOPPED, restricted))
        assertEquals(UsageTier.PEEK, usageTierOf(VehicleMotion.STOPPED, null))
    }

    @Test
    fun `주행이나 판정 불가는 항상 백그라운드`() {
        listOf(free, restricted, null).forEach {
            assertEquals(UsageTier.BACKGROUND, usageTierOf(VehicleMotion.MOVING, it))
            assertEquals(UsageTier.BACKGROUND, usageTierOf(null, it))
        }
    }

    @Test
    fun `주차여도 플랫폼이 DO를 요구하거나 연결에 실패하면 Peek까지만`() {
        assertEquals(UsageTier.PEEK, usageTierOf(VehicleMotion.PARKED, restricted))
        assertEquals(UsageTier.PEEK, usageTierOf(VehicleMotion.PARKED, null))
    }
}
```

```kotlin
// .../test/kotlin/kr/autolog/vehicle/data/usage/VehicleUsageRepositoryTest.kt
package kr.autolog.vehicle.data.usage

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.data.interim.VehicleMotion
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleUsageRepositoryTest {
    @Test
    fun `차량 상태가 바뀌면 단계가 따라 바뀐다`() =
        runTest {
            val motion = MutableStateFlow<VehicleMotion?>(VehicleMotion.PARKED)
            val carUx = StaticCarUxSource()
            val repo = DefaultVehicleUsageRepository(motion, carUx.state, backgroundScope)

            repo.tier.test {
                assertEquals(UsageTier.FULL, awaitItem())
                motion.value = VehicleMotion.STOPPED
                assertEquals(UsageTier.PEEK, awaitItem())
                motion.value = VehicleMotion.MOVING
                assertEquals(UsageTier.BACKGROUND, awaitItem())
            }
        }

    @Test
    fun `플랫폼이 키보드를 막으면 noKeyboard가 켜지고 연결 실패도 막힌 것으로 본다`() =
        runTest {
            val carUx = StaticCarUxSource(CarUxState(false, noKeyboard = true))
            val repo = DefaultVehicleUsageRepository(MutableStateFlow(VehicleMotion.PARKED), carUx.state, backgroundScope)

            repo.noKeyboard.test {
                assertEquals(true, awaitItem())
                carUx.set(CarUxState(false, noKeyboard = false))
                assertEquals(false, awaitItem())
                carUx.set(null)
                assertEquals(true, awaitItem())
            }
        }
}
```

- [ ] **Step 2: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*UsageTierTest*' --tests '*VehicleUsageRepositoryTest*'`
Expected: FAIL — `Unresolved reference: usageTierOf`

- [ ] **Step 3: 구현**

```kotlin
// .../data/usage/UsageTier.kt
package kr.autolog.vehicle.data.usage

import kr.autolog.vehicle.data.interim.VehicleMotion

/** 스펙 1.1. 순서 = 제한이 강한 순(ordinal이 작을수록 제한적). */
enum class UsageTier { BACKGROUND, PEEK, FULL }

/** AAOS CarUxRestrictions에서 필요한 값만. */
data class CarUxState(val requiresDistractionOptimization: Boolean, val noKeyboard: Boolean)

/** 스펙 6.9: 차량 상태와 플랫폼 중 더 제한적인 쪽. 판정 불가 = BACKGROUND, CarUx 연결 실패(null) = PEEK. */
fun usageTierOf(motion: VehicleMotion?, carUx: CarUxState?): UsageTier {
    val fromVehicle =
        when (motion) {
            VehicleMotion.PARKED -> UsageTier.FULL
            VehicleMotion.STOPPED -> UsageTier.PEEK
            VehicleMotion.MOVING, null -> UsageTier.BACKGROUND
        }
    val fromPlatform = if (carUx == null || carUx.requiresDistractionOptimization) UsageTier.PEEK else UsageTier.FULL
    return minOf(fromVehicle, fromPlatform)
}
```

```kotlin
// .../data/usage/CarUxSource.kt
package kr.autolog.vehicle.data.usage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface CarUxSource {
    /** null = 연결 실패 → 보수적으로 PEEK까지만. */
    val state: StateFlow<CarUxState?>
}

/** demo flavor·테스트용. 기본값은 "제한 없음"(일반 에뮬레이터에서 전체 앱을 쓸 수 있게). */
class StaticCarUxSource(initial: CarUxState? = CarUxState(false, false)) : CarUxSource {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<CarUxState?> = _state.asStateFlow()

    fun set(state: CarUxState?) {
        _state.value = state
    }
}
```

```kotlin
// .../data/usage/AndroidCarUxSource.kt
package kr.autolog.vehicle.data.usage

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** vehicle flavor용. AAOS CarUxRestrictionsManager 구독 (스펙 6.9, V-04). 실패하면 null(=PEEK). */
class AndroidCarUxSource(private val context: Context) : CarUxSource {
    private val _state = MutableStateFlow<CarUxState?>(null)
    override val state: StateFlow<CarUxState?> = _state.asStateFlow()
    private var car: Car? = null

    fun start() {
        runCatching {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                Log.w(TAG, "차량 기기가 아님 — CarUxRestrictions 없음, PEEK으로 제한")
                return
            }
            val connected = Car.createCar(context)
            car = connected
            val manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            _state.value = manager.currentCarUxRestrictions.toState()
            manager.registerListener { restrictions -> _state.value = restrictions.toState() }
        }.onFailure {
            Log.w(TAG, "CarUxRestrictions 연결 실패 — PEEK으로 제한", it)
            _state.value = null
        }
    }

    private fun CarUxRestrictions.toState() =
        CarUxState(
            requiresDistractionOptimization = isRequiresDistractionOptimization,
            noKeyboard = (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD) != 0,
        )

    private companion object {
        const val TAG = "AutoLog/ui"
    }
}
```

```kotlin
// .../data/usage/VehicleUsageRepository.kt
package kr.autolog.vehicle.data.usage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kr.autolog.vehicle.data.interim.VehicleMotion

/** 사용 단계의 유일한 진실 (스펙 6.9 1번). */
interface VehicleUsageRepository {
    val tier: StateFlow<UsageTier>
    val noKeyboard: StateFlow<Boolean>
}

class DefaultVehicleUsageRepository(
    motion: StateFlow<VehicleMotion?>,
    carUx: StateFlow<CarUxState?>,
    scope: CoroutineScope,
) : VehicleUsageRepository {
    override val tier: StateFlow<UsageTier> =
        combine(motion, carUx, ::usageTierOf)
            .stateIn(scope, SharingStarted.Eagerly, usageTierOf(motion.value, carUx.value))

    override val noKeyboard: StateFlow<Boolean> =
        carUx.map { it?.noKeyboard ?: true }
            .stateIn(scope, SharingStarted.Eagerly, carUx.value?.noKeyboard ?: true)
}
```

- [ ] **Step 4: 통과 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*UsageTierTest*' --tests '*VehicleUsageRepositoryTest*' && G :vehicle-app:assembleVehicleDebug`
Expected: PASS (6 tests), vehicle 빌드 성공(`android.car` 컴파일 확인)

- [ ] **Step 5: 커밋**

```bash
git add vehicle-app/src/main/kotlin/kr/autolog/vehicle/data/usage vehicle-app/src/test/kotlin/kr/autolog/vehicle/data/usage
git commit -m "feat(repo): 주행·정차·주차 사용 단계 판정과 CarUxRestrictions 연동 (F-13)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Navigator

**Files:**
- Create: `.../nav/Route.kt`, `.../nav/Navigator.kt`
- Test: `.../test/.../nav/NavigatorTest.kt`

**Interfaces:**
- Consumes: `UsageTier`
- Produces: `sealed interface Route { data object ProfilePicker; data object Home }`, `class Navigator(start: Route = Route.ProfilePicker) { val backStack: StateFlow<List<Route>>; val current: Route; fun push(route): Boolean; fun replaceAll(route): Boolean; fun pop(): Boolean; fun onTierChanged(tier: UsageTier) }`

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// .../test/kotlin/kr/autolog/vehicle/nav/NavigatorTest.kt
package kr.autolog.vehicle.nav

import kr.autolog.vehicle.data.usage.UsageTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NavigatorTest {
    @Test
    fun `주차에서 화면을 쌓고 뒤로 갈 수 있다`() {
        val nav = Navigator(Route.Home)

        nav.push(Route.ProfilePicker)
        nav.pop()

        assertEquals(listOf(Route.Home), nav.backStack.value)
    }

    @Test
    fun `마지막 화면은 뒤로 가지 않는다`() {
        val nav = Navigator(Route.Home)

        assertFalse(nav.pop())
        assertEquals(Route.Home, nav.current)
    }

    @Test
    fun `주차에서 정차로 내려가면 백스택이 Home 하나로 줄어든다`() {
        val nav = Navigator(Route.Home)
        nav.push(Route.ProfilePicker)

        nav.onTierChanged(UsageTier.PEEK)

        assertEquals(listOf(Route.Home), nav.backStack.value)
    }

    @Test
    fun `주차가 아니면 화면을 열 수 없다`() {
        val nav = Navigator(Route.Home)
        nav.onTierChanged(UsageTier.BACKGROUND)

        assertFalse(nav.push(Route.ProfilePicker))
        assertFalse(nav.replaceAll(Route.ProfilePicker))
        assertEquals(listOf(Route.Home), nav.backStack.value)
    }

    @Test
    fun `다시 주차해도 닫힌 화면을 되살리지 않는다`() {
        val nav = Navigator(Route.ProfilePicker)
        nav.onTierChanged(UsageTier.PEEK)

        nav.onTierChanged(UsageTier.FULL)

        assertEquals(listOf(Route.Home), nav.backStack.value)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*NavigatorTest*'`
Expected: FAIL — `Unresolved reference: Navigator`

- [ ] **Step 3: 구현**

```kotlin
// .../nav/Route.kt
package kr.autolog.vehicle.nav

/** 백스택의 모든 화면은 주차(FULL) 전용이다(스펙 6.10). Peek은 Route가 아니라 루트가 그리는 층. */
sealed interface Route {
    data object ProfilePicker : Route

    data object Home : Route
}
```

```kotlin
// .../nav/Navigator.kt
package kr.autolog.vehicle.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.autolog.vehicle.data.usage.UsageTier

/** 스펙 D-06·6.10. 단계 규칙을 한 곳에서 처리한다. */
class Navigator(start: Route = Route.ProfilePicker) {
    private val _backStack = MutableStateFlow(listOf(start))
    val backStack: StateFlow<List<Route>> = _backStack.asStateFlow()
    private var tier = UsageTier.FULL

    val current: Route get() = _backStack.value.last()

    fun push(route: Route): Boolean {
        if (tier != UsageTier.FULL) return false
        _backStack.value = _backStack.value + route
        return true
    }

    fun replaceAll(route: Route): Boolean {
        if (tier != UsageTier.FULL) return false
        _backStack.value = listOf(route)
        return true
    }

    fun pop(): Boolean {
        if (_backStack.value.size <= 1) return false
        _backStack.value = _backStack.value.dropLast(1)
        return true
    }

    /** FULL에서 내려가면 [Home]만 남긴다(스펙 6.9 3번). 올라올 때는 아무것도 되살리지 않는다. */
    fun onTierChanged(newTier: UsageTier) {
        if (newTier != UsageTier.FULL && (tier == UsageTier.FULL || _backStack.value != listOf(Route.Home))) {
            _backStack.value = listOf(Route.Home)
        }
        tier = newTier
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*NavigatorTest*'`
Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
git add vehicle-app/src/main/kotlin/kr/autolog/vehicle/nav vehicle-app/src/test/kotlin/kr/autolog/vehicle/nav
git commit -m "feat(nav): 단계 규칙을 가진 백스택 Navigator (F-13)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Repository 3종·표시 포맷·AppGraph·flavor 조립

**Files:**
- Create: `.../data/repository/SessionRepository.kt`, `.../data/repository/ProfileRepository.kt`, `.../data/repository/ContextRepository.kt`, `.../ui/design/Format.kt`, `.../di/AppGraph.kt`, `.../AutoLogApp.kt`, `vehicle-app/src/demo/kotlin/kr/autolog/vehicle/di/FlavorGraph.kt`, `vehicle-app/src/vehicle/kotlin/kr/autolog/vehicle/di/FlavorGraph.kt`
- Modify: `vehicle-app/src/main/AndroidManifest.xml` (`android:name=".AutoLogApp"`)
- Test: `.../test/.../data/repository/ProfileRepositoryTest.kt`, `.../test/.../ui/design/FormatTest.kt`, `.../test/.../TestAppGraph.kt`(테스트 유틸)

**Interfaces:**
- Consumes: `TripSessionApi`, `FakeTripSession`, `SessionState`, `MissedTrip`, `RecoveryChoice`, `StartResult`, `VehicleMotion`, `VehicleUsageRepository`, `Navigator`, `AppClock`, `throttleLatest`, core `PurposeEngine`·`SettingsApi`·`ContextApi`·`UserBadge`·`DriverSource`, fake `FakePurposeEngine`·`FakeSettingsApi`·`FakeContext`
- Produces:
  - `interface SessionRepository { val state: Flow<SessionState>; val totalOdometerKm: Flow<Double?>; val missedTrip: Flow<MissedTrip?>; val currentDriverId: StateFlow<String?>; suspend fun selectDriver(userId: String?); suspend fun startTrip(): StartResult; suspend fun endNow(); suspend fun keepRecording(); suspend fun assignDriver(tripId: String, userId: String); suspend fun acceptMissedTrip(); suspend fun ignoreMissedTrip(); suspend fun resolveRecovery(choice: RecoveryChoice) }` + `class DefaultSessionRepository(api: TripSessionApi)`
  - `enum class ProfileBadge { SCHEDULED, RECENT }`, `data class DriverProfile(userId, name, dept: String?, badge: ProfileBadge?)`, `data class UnassignedTripItem(tripId, label, candidates: List<DriverProfile>)`, `interface ProfileRepository { fun orderedProfiles(): Flow<List<DriverProfile>>; fun currentDriver(): Flow<DriverProfile?>; suspend fun unassignedTrip(): UnassignedTripItem? }` + `class DefaultProfileRepository(purpose, settings, currentDriverId: StateFlow<String?>, vehicleId: () -> String, clock: AppClock)`
  - `interface ContextRepository { val destinationLabel: Flow<String?> }` + `class DefaultContextRepository(api: ContextApi)`
  - `fun formatKm(km: Double?): String?`, `fun remainingSeconds(deadlineAt: Long, now: Long): Int`, `fun progressOf(since: Long, deadlineAt: Long, now: Long): Float`
  - `interface AppGraph { clock; navigator; session; usage; profile; context; demoOverlay: (@Composable () -> Unit)? }`, `val LocalAppGraph`, `fun createAppGraph(app: Application): AppGraph`(flavor별), `class AutoLogApp : Application { lateinit var graph: AppGraph }`
  - 테스트 유틸 `class TestAppGraph(scope: CoroutineScope, clock: AppClock = AppClock.System) : AppGraph { val trip: FakeTripSession; val carUx: StaticCarUxSource }`

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/design/FormatTest.kt
package kr.autolog.vehicle.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test
    fun `거리는 천 단위 쉼표와 소수 1자리`() {
        assertEquals("18,420.5", formatKm(18_420.5))
        assertEquals("23.4", formatKm(23.44))
        assertEquals("0.0", formatKm(0.0))
        assertNull(formatKm(null))
    }

    @Test
    fun `남은 초는 올림하고 0 아래로 내려가지 않는다`() {
        assertEquals(5, remainingSeconds(deadlineAt = 10_000, now = 5_000))
        assertEquals(5, remainingSeconds(deadlineAt = 10_000, now = 5_001))
        assertEquals(0, remainingSeconds(deadlineAt = 10_000, now = 12_000))
    }

    @Test
    fun `진행률은 남은 비율이고 0에서 1 사이`() {
        assertEquals(1f, progressOf(since = 0, deadlineAt = 5_000, now = 0), 0.001f)
        assertEquals(0.4f, progressOf(since = 0, deadlineAt = 5_000, now = 3_000), 0.001f)
        assertEquals(0f, progressOf(since = 0, deadlineAt = 5_000, now = 9_000), 0.001f)
    }
}
```

```kotlin
// .../test/kotlin/kr/autolog/vehicle/data/repository/ProfileRepositoryTest.kt
package kr.autolog.vehicle.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.fake.FakePurposeEngine
import kr.autolog.vehicle.fake.FakeSettingsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileRepositoryTest {
    private val driverId = MutableStateFlow<String?>(null)
    private val repo =
        DefaultProfileRepository(FakePurposeEngine(), FakeSettingsApi(), driverId, { "veh-1" }, AppClock { 0L })

    @Test
    fun `추천 엔진의 순서와 배지를 그대로 옮긴다`() =
        runTest {
            val profiles = repo.orderedProfiles().first()

            assertEquals(listOf("u-1", "u-2", "u-3"), profiles.map { it.userId })
            assertEquals(ProfileBadge.SCHEDULED, profiles[0].badge)
            assertEquals(ProfileBadge.RECENT, profiles[1].badge)
            assertNull(profiles[2].badge)
        }

    @Test
    fun `현재 운전자는 직원 목록에서 이름을 찾는다`() =
        runTest {
            repo.currentDriver().test {
                assertNull(awaitItem())
                driverId.value = "u-2"
                assertEquals("이현장", awaitItem()?.name)
            }
        }

    @Test
    fun `운전자 미확정 운행은 후보를 먼저 두고 최대 3명`() =
        runTest {
            val item = repo.unassignedTrip()!!

            assertEquals("t-9", item.tripId)
            assertEquals("u-1", item.candidates.first().userId)
            assertEquals(3, item.candidates.size)
        }
}
```

- [ ] **Step 2: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*FormatTest*' --tests '*ProfileRepositoryTest*'`
Expected: FAIL — `Unresolved reference`

- [ ] **Step 3: 구현 — 포맷·Repository**

```kotlin
// .../ui/design/Format.kt
package kr.autolog.vehicle.ui.design

import java.util.Locale
import kotlin.math.ceil

/** 표시용 숫자 포맷은 여기 한 곳(스펙 6.3). */
fun formatKm(km: Double?): String? = km?.let { String.format(Locale.US, "%,.1f", it) }

fun remainingSeconds(deadlineAt: Long, now: Long): Int = ceil((deadlineAt - now) / 1000.0).toInt().coerceAtLeast(0)

fun progressOf(since: Long, deadlineAt: Long, now: Long): Float {
    val total = (deadlineAt - since).coerceAtLeast(1)
    return ((deadlineAt - now).toFloat() / total).coerceIn(0f, 1f)
}
```

```kotlin
// .../data/repository/SessionRepository.kt
package kr.autolog.vehicle.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kr.autolog.vehicle.common.throttleLatest
import kr.autolog.vehicle.core.DriverSource
import kr.autolog.vehicle.data.interim.MissedTrip
import kr.autolog.vehicle.data.interim.RecoveryChoice
import kr.autolog.vehicle.data.interim.SessionState
import kr.autolog.vehicle.data.interim.StartResult
import kr.autolog.vehicle.data.interim.TripSessionApi

/** 운행 세션 (스펙 6.5). 앱 A 실계약이 오면 구현만 바꾼다. */
interface SessionRepository {
    /** 1초 스로틀(스펙 6.11). */
    val state: Flow<SessionState>
    val totalOdometerKm: Flow<Double?>
    val missedTrip: Flow<MissedTrip?>
    val currentDriverId: StateFlow<String?>

    suspend fun selectDriver(userId: String?)

    suspend fun startTrip(): StartResult

    suspend fun endNow()

    suspend fun keepRecording()

    suspend fun assignDriver(tripId: String, userId: String)

    suspend fun acceptMissedTrip()

    suspend fun ignoreMissedTrip()

    suspend fun resolveRecovery(choice: RecoveryChoice)
}

class DefaultSessionRepository(private val api: TripSessionApi) : SessionRepository {
    override val state: Flow<SessionState> = api.state.throttleLatest(1_000)
    override val totalOdometerKm: Flow<Double?> = api.totalOdometerKm.throttleLatest(1_000)
    override val missedTrip: Flow<MissedTrip?> = api.missedTrip
    override val currentDriverId: StateFlow<String?> = api.currentDriver

    override suspend fun selectDriver(userId: String?) = api.selectDriver(userId)

    override suspend fun startTrip(): StartResult = api.startSession()

    override suspend fun endNow() = api.finalize()

    override suspend fun keepRecording() = api.resume()

    override suspend fun assignDriver(tripId: String, userId: String) =
        api.assignDriver(tripId, userId, DriverSource.DRIVER_SELECTED)

    override suspend fun acceptMissedTrip() = api.acceptMissedTrip()

    override suspend fun ignoreMissedTrip() = api.ignoreMissedTrip()

    override suspend fun resolveRecovery(choice: RecoveryChoice) = api.resolveRecovery(choice)
}
```

```kotlin
// .../data/repository/ProfileRepository.kt
package kr.autolog.vehicle.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.PurposeEngine
import kr.autolog.vehicle.core.SettingsApi
import kr.autolog.vehicle.core.UserBadge

enum class ProfileBadge { SCHEDULED, RECENT }

data class DriverProfile(val userId: String, val name: String, val dept: String?, val badge: ProfileBadge? = null)

/** 운전자 미확정 운행 1건과 1탭으로 고를 후보(최대 3명, 배차·직전 후보가 먼저). */
data class UnassignedTripItem(val tripId: String, val label: String, val candidates: List<DriverProfile>)

interface ProfileRepository {
    fun orderedProfiles(): Flow<List<DriverProfile>>

    fun currentDriver(): Flow<DriverProfile?>

    suspend fun unassignedTrip(): UnassignedTripItem?
}

class DefaultProfileRepository(
    private val purpose: PurposeEngine,
    private val settings: SettingsApi,
    private val currentDriverId: StateFlow<String?>,
    private val vehicleId: () -> String,
    private val clock: AppClock,
) : ProfileRepository {
    override fun orderedProfiles(): Flow<List<DriverProfile>> =
        purpose.orderedUsers(vehicleId(), clock.nowMs()).map { cards ->
            cards.map { card ->
                DriverProfile(
                    userId = card.userId,
                    name = card.name,
                    dept = card.dept,
                    badge =
                        when (card.badge) {
                            UserBadge.SCHEDULED -> ProfileBadge.SCHEDULED
                            UserBadge.RECENT -> ProfileBadge.RECENT
                            null -> null
                        },
                )
            }
        }

    override fun currentDriver(): Flow<DriverProfile?> =
        combine(currentDriverId, settings.users()) { id, users ->
            users.firstOrNull { it.userId == id }?.let { DriverProfile(it.userId, it.name, it.dept) }
        }.distinctUntilChanged()

    override suspend fun unassignedTrip(): UnassignedTripItem? {
        val trip = purpose.unassignedTrips(vehicleId(), clock.nowMs()).firstOrNull() ?: return null
        val others = orderedProfiles().first()
        val first = trip.candidate?.let { DriverProfile(it.userId, it.name, null, ProfileBadge.SCHEDULED) }
        val candidates = (listOfNotNull(first) + others.filter { it.userId != first?.userId }).take(3)
        return UnassignedTripItem(trip.tripId, trip.label, candidates)
    }
}
```

```kotlin
// .../data/repository/ContextRepository.kt
package kr.autolog.vehicle.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.autolog.vehicle.core.ContextApi

interface ContextRepository {
    /** 내비 목적지 이름. 없으면 null. */
    val destinationLabel: Flow<String?>
}

class DefaultContextRepository(api: ContextApi) : ContextRepository {
    override val destinationLabel: Flow<String?> = api.destination.map { it?.label }
}
```

- [ ] **Step 4: 통과 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*FormatTest*' --tests '*ProfileRepositoryTest*'`
Expected: PASS (6 tests)

- [ ] **Step 5: 구현 — AppGraph·flavor·Application·테스트 그래프**

```kotlin
// .../di/AppGraph.kt
package kr.autolog.vehicle.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.data.repository.ContextRepository
import kr.autolog.vehicle.data.repository.ProfileRepository
import kr.autolog.vehicle.data.repository.SessionRepository
import kr.autolog.vehicle.data.usage.VehicleUsageRepository
import kr.autolog.vehicle.nav.Navigator

/** 수동 DI (스펙 D-05·6.7). flavor마다 createAppGraph()가 조립한다. */
interface AppGraph {
    val clock: AppClock
    val navigator: Navigator
    val session: SessionRepository
    val usage: VehicleUsageRepository
    val profile: ProfileRepository
    val context: ContextRepository

    /** demo flavor 데모 패널. vehicle flavor는 null. */
    val demoOverlay: (@Composable () -> Unit)?
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph가 제공되지 않았습니다") }
```

```kotlin
// .../AutoLogApp.kt
package kr.autolog.vehicle

import android.app.Application
import kr.autolog.vehicle.di.AppGraph
import kr.autolog.vehicle.di.createAppGraph

class AutoLogApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createAppGraph(this)
    }
}
```

```kotlin
// vehicle-app/src/demo/kotlin/kr/autolog/vehicle/di/FlavorGraph.kt — Task 15에서 demoOverlay를 연결한다
package kr.autolog.vehicle.di

import android.app.Application
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.data.interim.FakeTripSession
import kr.autolog.vehicle.data.repository.DefaultContextRepository
import kr.autolog.vehicle.data.repository.DefaultProfileRepository
import kr.autolog.vehicle.data.repository.DefaultSessionRepository
import kr.autolog.vehicle.data.usage.DefaultVehicleUsageRepository
import kr.autolog.vehicle.data.usage.StaticCarUxSource
import kr.autolog.vehicle.fake.FakeContext
import kr.autolog.vehicle.fake.FakePurposeEngine
import kr.autolog.vehicle.fake.FakeSettingsApi
import kr.autolog.vehicle.nav.Navigator

fun createAppGraph(app: Application): AppGraph = DemoAppGraph(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))

/** 전부 Fake (스펙 6.7). 일반 에뮬레이터·시연 영상 백업용. */
class DemoAppGraph(scope: CoroutineScope) : AppGraph {
    override val clock: AppClock = AppClock.System
    val trip = FakeTripSession(scope, clock)
    override val navigator = Navigator()
    override val session = DefaultSessionRepository(trip)
    override val usage = DefaultVehicleUsageRepository(trip.motion, StaticCarUxSource().state, scope)
    override val profile = DefaultProfileRepository(FakePurposeEngine(), FakeSettingsApi(), trip.currentDriver, { "veh-demo" }, clock)
    override val context = DefaultContextRepository(FakeContext())
    override val demoOverlay: (@Composable () -> Unit)? = null
}
```

```kotlin
// vehicle-app/src/vehicle/kotlin/kr/autolog/vehicle/di/FlavorGraph.kt
package kr.autolog.vehicle.di

import android.app.Application
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.data.interim.FakeTripSession
import kr.autolog.vehicle.data.repository.DefaultContextRepository
import kr.autolog.vehicle.data.repository.DefaultProfileRepository
import kr.autolog.vehicle.data.repository.DefaultSessionRepository
import kr.autolog.vehicle.data.usage.AndroidCarUxSource
import kr.autolog.vehicle.data.usage.DefaultVehicleUsageRepository
import kr.autolog.vehicle.fake.FakeContext
import kr.autolog.vehicle.fake.FakePurposeEngine
import kr.autolog.vehicle.fake.FakeSettingsApi
import kr.autolog.vehicle.nav.Navigator

/**
 * 실차·Pleos 에뮬레이터용. 지금은 CarUxRestrictions만 실제이고 나머지는 Fake다.
 * 스펙 R-1(`AutoLog.create(context)`)·R-2(앱 A 계약)가 머지되면 여기서 실구현으로 바꾼다.
 */
fun createAppGraph(app: Application): AppGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val carUx = AndroidCarUxSource(app).also { it.start() }
    val appClock = AppClock.System
    val trip = FakeTripSession(scope, appClock)
    return object : AppGraph {
        override val clock = appClock
        override val navigator = Navigator()
        override val session = DefaultSessionRepository(trip)
        override val usage = DefaultVehicleUsageRepository(trip.motion, carUx.state, scope)
        override val profile = DefaultProfileRepository(FakePurposeEngine(), FakeSettingsApi(), trip.currentDriver, { "veh-1" }, appClock)
        override val context = DefaultContextRepository(FakeContext())
        override val demoOverlay: (@Composable () -> Unit)? = null
    }
}
```

```kotlin
// .../test/kotlin/kr/autolog/vehicle/TestAppGraph.kt
package kr.autolog.vehicle

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.data.interim.FakeTripSession
import kr.autolog.vehicle.data.repository.DefaultContextRepository
import kr.autolog.vehicle.data.repository.DefaultProfileRepository
import kr.autolog.vehicle.data.repository.DefaultSessionRepository
import kr.autolog.vehicle.data.usage.DefaultVehicleUsageRepository
import kr.autolog.vehicle.data.usage.StaticCarUxSource
import kr.autolog.vehicle.di.AppGraph
import kr.autolog.vehicle.fake.FakeContext
import kr.autolog.vehicle.fake.FakePurposeEngine
import kr.autolog.vehicle.fake.FakeSettingsApi
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route

/** UI 테스트용 그래프. 데모 패널이 없다. */
class TestAppGraph(scope: CoroutineScope, override val clock: AppClock = AppClock.System, start: Route = Route.ProfilePicker) : AppGraph {
    val trip = FakeTripSession(scope, clock)
    val carUx = StaticCarUxSource()
    override val navigator = Navigator(start)
    override val session = DefaultSessionRepository(trip)
    override val usage = DefaultVehicleUsageRepository(trip.motion, carUx.state, scope)
    override val profile = DefaultProfileRepository(FakePurposeEngine(), FakeSettingsApi(), trip.currentDriver, { "veh-test" }, clock)
    override val context = DefaultContextRepository(FakeContext())
    override val demoOverlay: (@Composable () -> Unit)? = null
}
```

Manifest `<application`에 `android:name=".AutoLogApp"` 속성을 추가한다.

- [ ] **Step 6: 빌드·테스트**

Run: `G :vehicle-app:testDemoDebugUnitTest :vehicle-app:assembleDemoDebug :vehicle-app:assembleVehicleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 커밋**

```bash
git add vehicle-app/src
git commit -m "feat(repo): 세션·운전자·맥락 Repository와 flavor별 AppGraph 조립 (F-15)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: 공용 컴포넌트 1 — 버튼·칩·숫자·배너·오버레이 + 스크린샷 기반

**Files:**
- Create: `.../ui/design/components/Buttons.kt`, `StatusChip.kt`, `HeroMetric.kt`, `Banner.kt`, `Overlay.kt`, `.../ui/design/Texts.kt`
- Modify: `vehicle-app/src/main/res/values/strings.xml`
- Test: `.../test/.../screenshot/Screenshots.kt`(유틸), `.../test/.../ui/design/components/ComponentsTest.kt`(동작 `ComponentsTest` + 스크린샷 `ComponentsScreenshotTest` 두 클래스)

**Interfaces:**
- Consumes: 토큰 전부, `pressFeedback`, `confirmHaptic`, `LocalInputLocked`, `WindowLayoutHost`
- Produces:
  - `@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = Size.buttonLg, loading: Boolean = false, enabled: Boolean = true)`; `SecondaryButton(text, onClick, modifier, height = Size.buttonMd, enabled = true)`; `TertiaryButton(text, onClick, modifier, enabled = true)`; `DangerButton(text, onClick, modifier, enabled = true)`
  - `enum class ChipTone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER }`, `@Composable fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier, icon: ImageVector? = null, leading: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null)`
  - `@Composable fun HeroMetric(value: String?, unit: String, label: String?, unavailableText: String, modifier: Modifier = Modifier, large: Boolean = true)`
  - `data class BannerAction(val text: String, val onClick: () -> Unit)`, `@Composable fun Banner(text: String, actions: List<BannerAction>, modifier: Modifier = Modifier)`
  - `@Composable fun ScrimSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)` — 화면 아래 시트 + scrim
  - `@Composable fun durationText(sec: Int): String`
  - 테스트 유틸 `enum class ShotSize { WIDE, NARROW, TALL }`, `fun captureAll(name: String, sizes: List<ShotSize> = ShotSize.entries, content: @Composable () -> Unit)`

- [ ] **Step 1: 문자열 추가**

`strings.xml`의 `<resources>` 안에 추가:

```xml
    <string name="duration_sec">%1$d초</string>
    <string name="duration_min_sec">%1$d분 %2$d초</string>
    <string name="unit_km">km</string>
    <string name="distance_unavailable">계기판 거리를 읽지 못했어요</string>
```

- [ ] **Step 2: 실패하는 테스트(잠금 동작 + 스크린샷)**

```kotlin
// .../test/kotlin/kr/autolog/vehicle/screenshot/Screenshots.kt
package kr.autolog.vehicle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.WindowLayoutHost
import org.robolectric.RuntimeEnvironment

/** 스펙 10.2 잠정 크기 [V-01]. FormFactorConsole 측정 후 바꾼다. */
enum class ShotSize(val qualifier: String) {
    WIDE("w1024dp-h640dp"),
    NARROW("w504dp-h640dp"),
    TALL("w720dp-h1100dp"),
}

/** 레이아웃 × 테마마다 한 장씩. 파일: src/test/screenshots/<name>_<size>_<theme>.png */
fun captureAll(name: String, sizes: List<ShotSize> = ShotSize.entries, content: @Composable () -> Unit) {
    for (size in sizes) {
        for (dark in listOf(true, false)) {
            RuntimeEnvironment.setQualifiers("${size.qualifier}-${if (dark) "night" else "notnight"}-xhdpi")
            val theme = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_${size.name.lowercase()}_$theme.png") {
                AutoLogTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize().background(AutoLogTheme.colors.bg)) {
                        WindowLayoutHost { content() }
                    }
                }
            }
        }
    }
}
```

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/design/components/ComponentsTest.kt
package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.screenshot.ShotSize
import kr.autolog.vehicle.screenshot.captureAll
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.Space
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `잠금이면 버튼이 비활성이고 눌러도 실행되지 않는다`() {
        var clicks = 0
        compose.setContent {
            AutoLogTheme {
                CompositionLocalProvider(LocalInputLocked provides true) {
                    PrimaryButton("운행 시작", onClick = { clicks++ })
                }
            }
        }

        compose.onNodeWithText("운행 시작").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun `잠금이 아니면 1탭으로 실행된다`() {
        var clicks = 0
        compose.setContent { AutoLogTheme { PrimaryButton("운행 시작", onClick = { clicks++ }) } }

        compose.onNodeWithText("운행 시작").assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }
}

/** 스크린샷은 compose rule과 섞지 않고 별도 클래스로(각 캡처가 자기 Activity를 띄운다). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ComponentsScreenshotTest {
    @Test
    fun components() =
        captureAll("components", listOf(ShotSize.NARROW)) {
            Column(Modifier.padding(Space.s6), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                PrimaryButton("계속 기록", onClick = {})
                PrimaryButton("불러오는 중", onClick = {}, loading = true)
                SecondaryButton("기록 종료", onClick = {})
                TertiaryButton("나중에 선택", onClick = {})
                StatusChip("확인 필요 1건", ChipTone.WARNING, onClick = {})
                HeroMetric("23.4", "km", "이번 운행", "계기판 거리를 읽지 못했어요", large = false)
                Banner("앱이 꺼진 동안 15.0 km를 달렸어요", listOf(BannerAction("추가") {}, BannerAction("무시") {}))
            }
        }
}
```

- [ ] **Step 3: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*ComponentsTest*'`
Expected: FAIL — `Unresolved reference: PrimaryButton`

- [ ] **Step 4: 구현**

```kotlin
// .../ui/design/Texts.kt
package kr.autolog.vehicle.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R

/** "5초" / "2분 41초" */
@Composable
fun durationText(sec: Int): String =
    if (sec < 60) stringResource(R.string.duration_sec, sec) else stringResource(R.string.duration_min_sec, sec / 60, sec % 60)
```

```kotlin
// .../ui/design/components/Buttons.kt
package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.confirmHaptic

/** 모든 버튼의 공통 몸체. 잠금(LocalInputLocked)이면 스스로 비활성(디자인 언어 11장). */
@Composable
private fun ButtonBody(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    height: Dp,
    container: Color,
    content: Color,
    disabledContainer: Color,
    enabled: Boolean,
    loading: Boolean = false,
) {
    val colors = AutoLogTheme.colors
    val active = enabled && !LocalInputLocked.current && !loading
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    Box(
        modifier =
            modifier
                .heightIn(min = height)
                .defaultMinSize(minWidth = Size.touchMin)
                .pressFeedback(interaction)
                .clip(RoundedCornerShape(Radius.lg))
                .background(if (active || loading) container else disabledContainer)
                .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button) {
                    view.confirmHaptic()
                    onClick()
                }.padding(horizontal = Space.s6),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = Space.s1, modifier = Modifier.size(Size.icon))
        } else {
            Text(
                text = text,
                style = AutoLogTheme.type.label,
                color = if (active) content else colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Size.buttonLg,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val c = AutoLogTheme.colors
    ButtonBody(text, onClick, modifier, height, c.accentFill, c.onAccent, c.surface3, enabled, loading)
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Size.buttonMd,
    enabled: Boolean = true,
) {
    val c = AutoLogTheme.colors
    ButtonBody(text, onClick, modifier, height, c.surface2, c.textPrimary, c.surface2, enabled)
}

@Composable
fun TertiaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = AutoLogTheme.colors
    ButtonBody(text, onClick, modifier, Size.touchMin, Color.Transparent, c.accent, Color.Transparent, enabled)
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = AutoLogTheme.colors
    ButtonBody(text, onClick, modifier, Size.buttonMd, c.dangerFill, c.onAccent, c.surface3, enabled)
}
```

```kotlin
// .../ui/design/components/StatusChip.kt
package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.confirmHaptic

enum class ChipTone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER }

/** [onClick]이 null이면 터치할 수 없는 표시용(Peek). */
@Composable
fun StatusChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = AutoLogTheme.colors
    val fg =
        when (tone) {
            ChipTone.NEUTRAL -> c.textPrimary
            ChipTone.ACCENT -> c.accent
            ChipTone.SUCCESS -> c.success
            ChipTone.WARNING -> c.warning
            ChipTone.DANGER -> c.danger
        }
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val clickable = onClick != null && !LocalInputLocked.current
    var m = modifier.heightIn(min = if (onClick != null) Size.chipHeight else Size.iconLg)
    if (onClick != null) {
        m =
            m.pressFeedback(interaction).clickable(interaction, null, enabled = clickable, role = Role.Button) {
                view.confirmHaptic()
                onClick()
            }
    }
    Row(m, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(CircleShape).background(c.surface3).padding(horizontal = Space.s5, vertical = Space.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            leading?.invoke()
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(Size.icon))
            Text(text, style = AutoLogTheme.type.label, color = fg)
        }
    }
}
```

```kotlin
// .../ui/design/components/HeroMetric.kt
package kr.autolog.vehicle.ui.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Space

/** 숫자 + 단위 + 라벨. [value]가 null이면 숫자 대신 [unavailableText](추정값을 그리지 않는다). */
@Composable
fun HeroMetric(
    value: String?,
    unit: String,
    label: String?,
    unavailableText: String,
    modifier: Modifier = Modifier,
    large: Boolean = true,
) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val motion = AutoLogTheme.motion
    Column(modifier) {
        if (label != null) Text(label, style = t.body, color = c.textSecondary)
        if (value == null) {
            Text(unavailableText, style = t.title, color = c.warning)
        } else {
            Row {
                val numberStyle = if (large) t.hero else t.heroSmall
                if (motion.isStatic) {
                    Text(value, style = numberStyle, color = c.textPrimary, modifier = Modifier.alignByBaseline())
                } else {
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = { fadeIn(motion.fadeQuick()) togetherWith fadeOut(motion.fadeQuick()) },
                        label = "metric",
                        modifier = Modifier.alignByBaseline(),
                    ) { v -> Text(v, style = numberStyle, color = c.textPrimary) }
                }
                Spacer(Modifier.width(Space.s3))
                Text(unit, style = t.headline, color = c.textTertiary, modifier = Modifier.alignByBaseline())
            }
        }
    }
}
```

```kotlin
// .../ui/design/components/Banner.kt
package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space

data class BannerAction(val text: String, val onClick: () -> Unit)

/** 화면 상단 전폭 알림. 버튼 최대 2개(첫 번째가 주 동작). */
@Composable
fun Banner(text: String, actions: List<BannerAction>, modifier: Modifier = Modifier) {
    val c = AutoLogTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.surface2).padding(Space.s5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(Size.icon))
        Text(text, style = AutoLogTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
        actions.take(2).forEachIndexed { i, a ->
            if (i == 0) PrimaryButton(a.text, a.onClick, height = Size.buttonMd) else SecondaryButton(a.text, a.onClick)
        }
    }
}
```

```kotlin
// .../ui/design/components/Overlay.kt
package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space

/** 아래에서 올라오는 시트 + scrim(디자인 언어 11장 Sheet). 단계가 FULL에서 내려가면 루트가 화면째 지운다. */
@Composable
fun ScrimSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = AutoLogTheme.colors
    // scrim은 뒤 화면으로 터치가 새지 않게 삼킨다(클릭 의미론은 없음 — Peek 검사에 걸리지 않는다)
    Box(
        modifier.fillMaxSize().background(c.scrim).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = Size.sheetMaxWidth)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg))
                .background(c.surface2)
                .padding(Space.s6),
            content = content,
        )
    }
}
```

- [ ] **Step 5: 통과 확인 + 스크린샷 기록**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*ComponentsTest*' && G :vehicle-app:testDemoDebugUnitTest --tests '*ComponentsScreenshotTest*' -Proborazzi.test.record=true`
Expected: PASS, `vehicle-app/src/test/screenshots/components_narrow_dark.png`·`_light.png` 생성. 두 이미지를 열어 Asta Sans로 렌더링됐는지(시스템 서체가 아닌지) 확인하고 V-09를 `docs/verification.md`에 기록한다.

- [ ] **Step 6: 커밋**

```bash
git add vehicle-app/src docs/verification.md
git commit -m "feat(design): 버튼·칩·숫자·배너·시트 컴포넌트와 스크린샷 기반

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: 공용 컴포넌트 2 — 아바타·ProfileTile·shared element

**Files:**
- Create: `.../ui/design/components/Avatar.kt`, `.../ui/design/components/ProfileTile.kt`, `.../ui/design/SharedAvatar.kt`
- Modify: `strings.xml`
- Test: `.../test/.../ui/design/components/AvatarTest.kt`

**Interfaces:**
- Consumes: `AvatarGradients`, 토큰, `pressFeedback`
- Produces: `fun avatarGradientIndex(userId: String): Int`, `fun initialsOf(name: String): String`, `@Composable fun Avatar(userId: String, name: String, size: Dp, modifier: Modifier = Modifier)`, `@Composable fun ProfileTile(userId: String, name: String, dept: String?, scheduled: Boolean, recent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tileSize: Dp = Size.profileTileNarrow)`, `val LocalSharedTransitionScope`, `val LocalNavAnimatedScope`, `@Composable fun Modifier.sharedAvatar(userId: String): Modifier`

- [ ] **Step 1: 문자열 추가**

```xml
    <string name="badge_scheduled">배차</string>
    <string name="badge_recent">최근</string>
```

- [ ] **Step 2: 실패하는 테스트**

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/design/components/AvatarTest.kt
package kr.autolog.vehicle.ui.design.components

import kr.autolog.vehicle.ui.design.AvatarGradients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarTest {
    @Test
    fun `같은 사람은 항상 같은 색`() {
        assertEquals(avatarGradientIndex("u-1"), avatarGradientIndex("u-1"))
    }

    @Test
    fun `색 번호는 팔레트 범위 안`() {
        listOf("u-1", "u-2", "local-9", "", "가나다").forEach {
            assertTrue(avatarGradientIndex(it) in AvatarGradients.indices)
        }
    }

    @Test
    fun `한글 이름은 첫 글자, 영문 이름은 두 단어 첫 글자`() {
        assertEquals("김", initialsOf("김영업"))
        assertEquals("JS", initialsOf("john smith"))
        assertEquals("A", initialsOf("Alice"))
        assertEquals("?", initialsOf("  "))
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*AvatarTest*'`
Expected: FAIL — `Unresolved reference: avatarGradientIndex`

- [ ] **Step 4: 구현**

```kotlin
// .../ui/design/components/Avatar.kt
package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.AvatarGradients

/** 디자인 언어 5.4: userId 해시로 8색 중 하나. */
fun avatarGradientIndex(userId: String): Int = Math.floorMod(userId.hashCode(), AvatarGradients.size)

/** 한글 = 첫 글자, 라틴 = 앞 두 단어의 첫 글자(대문자). */
fun initialsOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val first = trimmed.first()
    return if (first.code < 128 && first.isLetter()) {
        trimmed.split(Regex("\\s+")).take(2).joinToString("") { it.take(1).uppercase() }
    } else {
        first.toString()
    }
}

/** 이니셜 크기는 아바타 크기의 40%(토큰이 아니라 비율 — 아바타 크기 자체가 토큰). */
@Composable
fun Avatar(userId: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val (start, end) = AvatarGradients[avatarGradientIndex(userId)]
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2f))
            .background(Brush.linearGradient(listOf(start, end), start = Offset.Zero, end = Offset.Infinite)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(name),
            style = AutoLogTheme.type.display.copy(fontSize = (size.value * 0.4f).sp, lineHeight = (size.value * 0.45f).sp),
            color = Color.White,
        )
    }
}
```

```kotlin
// .../ui/design/SharedAvatar.kt
package kr.autolog.vehicle.ui.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** 디자인 언어 8.2 motion.hero: 프로필 타일 → Home 운전자 칩. 정지형이거나 스코프가 없으면 아무것도 안 한다. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedAvatar(userId: String): Modifier {
    val transition = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedScope.current ?: return this
    val motion = AutoLogTheme.motion
    if (motion.isStatic) return this
    return with(transition) {
        this@sharedAvatar.sharedElement(
            state = rememberSharedContentState(key = "avatar-$userId"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> motion.gentle() },
        )
    }
}
```

```kotlin
// .../ui/design/components/ProfileTile.kt
package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.confirmHaptic
import kr.autolog.vehicle.ui.design.sharedAvatar

/** 디자인 언어 11장 ProfileTile. 1탭 = 선택 + 진입(조작 최소화 2.6). */
@Composable
fun ProfileTile(
    userId: String,
    name: String,
    dept: String?,
    scheduled: Boolean,
    recent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tileSize: Dp = Size.profileTileNarrow,
) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    Column(
        modifier
            .width(tileSize)
            .pressFeedback(interaction)
            .clickable(interaction, null, enabled = !LocalInputLocked.current, role = Role.Button) {
                view.confirmHaptic()
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            val ring =
                if (scheduled) {
                    Modifier.border(Size.ring, c.accent, RoundedCornerShape(Radius.xl)).padding(Space.s2)
                } else {
                    Modifier.padding(Space.s2)
                }
            Avatar(userId, name, tileSize - Space.s4, ring.sharedAvatar(userId))
            if (scheduled || recent) {
                StatusChip(
                    text = stringResource(if (scheduled) R.string.badge_scheduled else R.string.badge_recent),
                    tone = if (scheduled) ChipTone.ACCENT else ChipTone.NEUTRAL,
                )
            }
        }
        Text(name, style = t.title, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (dept != null) Text(dept, style = t.caption, color = c.textSecondary, maxLines = 1)
    }
}
```

- [ ] **Step 5: 통과 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*AvatarTest*' && G :vehicle-app:assembleDemoDebug`
Expected: PASS (3 tests), 빌드 성공

- [ ] **Step 6: 커밋**

```bash
git add vehicle-app/src
git commit -m "feat(design): 이니셜 아바타·프로필 타일·공유 요소 전환 (F-15)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: 루트 — 사용 단계별 층 전환·입력 잠금·백그라운드

**Files:**
- Create: `.../ui/root/AutoLogRoot.kt`, `.../ui/root/FullApp.kt`
- Modify: `.../MainActivity.kt`
- Test: `.../test/.../ui/root/AutoLogRootTest.kt`

**Interfaces:**
- Consumes: `AppGraph`, `LocalAppGraph`, `UsageTier`, `Navigator`, `Route`, `AutoLogTheme`, `WindowLayoutHost`, `LocalInputLocked`, `LocalSharedTransitionScope`, `LocalNavAnimatedScope`; 화면 진입점 `ProfilePickerRoute()`(Task 13), `HomeRoute()`(Task 14), `PeekRoute()`(Task 12)
- Produces: `@Composable fun AutoLogRoot(graph: AppGraph, onRequestBackground: () -> Unit)`, `const val FULL_ENTRY_GUARD_MS = 300L`

> 이 작업은 Task 12~14의 `PeekRoute`·`ProfilePickerRoute`·`HomeRoute`를 부른다. **Task 11 Step 3에서 세 함수를 임시 본문으로 먼저 만들고**(각 파일에 `@Composable fun XxxRoute() { }` 한 줄), Task 12~14에서 실제 내용으로 바꾼다. 임시 본문 파일 경로는 Task 12~14의 Files와 같다.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/root/AutoLogRootTest.kt
package kr.autolog.vehicle.ui.root

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kr.autolog.vehicle.TestAppGraph
import kr.autolog.vehicle.data.interim.VehicleMotion
import kr.autolog.vehicle.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AutoLogRootTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Test
    fun `정차면 누를 수 있는 것이 하나도 없다`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        graph.trip.setMotion(VehicleMotion.STOPPED)

        compose.setContent { AutoLogRoot(graph, onRequestBackground = {}) }
        compose.waitForIdle()

        assertEquals(0, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun `주행이 시작되면 앱이 스스로 뒤로 물러난다`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        var backgrounded = false
        compose.setContent { AutoLogRoot(graph, onRequestBackground = { backgrounded = true }) }

        graph.trip.setMotion(VehicleMotion.MOVING)
        compose.waitForIdle()

        assertTrue(backgrounded)
    }

    @Test
    fun `주차에서 내려가면 열린 화면이 닫히고 Home만 남는다`() {
        val graph = TestAppGraph(scope, start = Route.Home)
        graph.navigator.push(Route.ProfilePicker)
        compose.setContent { AutoLogRoot(graph, onRequestBackground = {}) }

        graph.trip.setMotion(VehicleMotion.STOPPED)
        compose.waitForIdle()

        assertEquals(listOf(Route.Home), graph.navigator.backStack.value)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*AutoLogRootTest*'`
Expected: FAIL — `Unresolved reference: AutoLogRoot`

- [ ] **Step 3: 화면 진입점 임시 본문**

```kotlin
// .../ui/peek/PeekScreen.kt (Task 12에서 교체)
package kr.autolog.vehicle.ui.peek

import androidx.compose.runtime.Composable

@Composable
fun PeekRoute() = Unit
```

```kotlin
// .../ui/profile/ProfilePickerScreen.kt (Task 13에서 교체)
package kr.autolog.vehicle.ui.profile

import androidx.compose.runtime.Composable

@Composable
fun ProfilePickerRoute() = Unit
```

```kotlin
// .../ui/home/HomeScreen.kt (Task 14에서 교체)
package kr.autolog.vehicle.ui.home

import androidx.compose.runtime.Composable

@Composable
fun HomeRoute() = Unit
```

- [ ] **Step 4: 구현**

```kotlin
// .../ui/root/FullApp.kt
package kr.autolog.vehicle.ui.root

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.autolog.vehicle.di.AppGraph
import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalNavAnimatedScope
import kr.autolog.vehicle.ui.design.LocalSharedTransitionScope
import kr.autolog.vehicle.ui.home.HomeRoute
import kr.autolog.vehicle.ui.profile.ProfilePickerRoute

/** 주차(FULL) 층: 백스택 맨 위 화면. 전환은 motion.screen, 프로필→Home은 motion.hero. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FullApp(graph: AppGraph) {
    val stack by graph.navigator.backStack.collectAsStateWithLifecycle()
    val motion = AutoLogTheme.motion
    BackHandler(enabled = stack.size > 1) { graph.navigator.pop() }
    SharedTransitionLayout {
        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = {
                (
                    fadeIn(motion.fadeStandard()) +
                        scaleIn(motion.gentle(), initialScale = 0.98f) +
                        slideInHorizontally(motion.gentle()) { it / 25 }
                ) togetherWith fadeOut(motion.fadeQuick())
            },
            label = "route",
        ) { route ->
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalNavAnimatedScope provides this,
            ) {
                when (route) {
                    Route.ProfilePicker -> ProfilePickerRoute()
                    Route.Home -> HomeRoute()
                }
            }
        }
    }
}
```

```kotlin
// .../ui/root/AutoLogRoot.kt
package kr.autolog.vehicle.ui.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kr.autolog.vehicle.data.usage.UsageTier
import kr.autolog.vehicle.di.AppGraph
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.WindowLayoutHost
import kr.autolog.vehicle.ui.peek.PeekRoute

/** FULL 진입 직후 오조작 방지(디자인 언어 12장). */
const val FULL_ENTRY_GUARD_MS = 300L

/**
 * 스펙 6.9 2번: FULL → 전체 앱 / PEEK → Peek 패널만 / BACKGROUND → 물러남.
 * 입력 잠금 = tier != FULL 또는 FULL 진입 직후 300 ms.
 */
@Composable
fun AutoLogRoot(graph: AppGraph, onRequestBackground: () -> Unit) {
    val tier by graph.usage.tier.collectAsStateWithLifecycle()
    var entryGuard by remember { mutableStateOf(false) }
    LaunchedEffect(tier) {
        graph.navigator.onTierChanged(tier)
        when (tier) {
            UsageTier.BACKGROUND -> onRequestBackground()
            UsageTier.FULL -> {
                entryGuard = true
                delay(FULL_ENTRY_GUARD_MS)
                entryGuard = false
            }
            UsageTier.PEEK -> Unit
        }
    }
    CompositionLocalProvider(LocalAppGraph provides graph) {
        AutoLogTheme(staticMotion = tier != UsageTier.FULL) {
            CompositionLocalProvider(LocalInputLocked provides (tier != UsageTier.FULL || entryGuard)) {
                Box(Modifier.fillMaxSize().background(AutoLogTheme.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    WindowLayoutHost {
                        when (tier) {
                            UsageTier.FULL -> FullApp(graph)
                            UsageTier.PEEK -> PeekRoute()
                            UsageTier.BACKGROUND -> Unit
                        }
                    }
                    graph.demoOverlay?.invoke()
                }
            }
        }
    }
}
```

```kotlin
// .../MainActivity.kt (교체)
package kr.autolog.vehicle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kr.autolog.vehicle.ui.root.AutoLogRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as AutoLogApp).graph
        setContent { AutoLogRoot(graph = graph, onRequestBackground = { moveTaskToBack(true) }) }
    }
}
```

- [ ] **Step 5: 통과 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*AutoLogRootTest*'`
Expected: PASS (3 tests)

- [ ] **Step 6: 커밋**

```bash
git add vehicle-app/src
git commit -m "feat(app): 사용 단계별 루트 층 전환과 입력 잠금·자동 물러남 (F-13)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Peek — 정차 중 읽기 전용 패널

**Files:**
- Create: `.../ui/peek/PeekUiState.kt`, `.../ui/peek/PeekViewModel.kt`
- Modify: `.../ui/peek/PeekScreen.kt`(임시 본문 교체), `strings.xml`
- Test: `.../test/.../ui/peek/PeekReducerTest.kt`, `.../test/.../ui/peek/PeekScreenshotTest.kt`

**Interfaces:**
- Consumes: `SessionRepository`, `ProfileRepository`, `ContextRepository`, `AppClock`, `tickerFlow`, `SessionState`, `StopReason`, `formatKm`, `remainingSeconds`, `durationText`, `StatusChip`, `HeroMetric`, `LocalAppGraph`
- Produces: `sealed interface PeekUiState { Loading; Recording(driverName: String?, tripKm: String?, destination: String?, savedSecondsAgo: Int, autoEndInSec: Int?); NoTrip(odometerKm: String?) }`, `internal fun reducePeek(state: SessionState, odometerKm: Double?, driverName: String?, destination: String?, now: Long): PeekUiState`, `class PeekViewModel(session, profile, context, clock, ticker: Flow<Long> = tickerFlow(clock))`, `@Composable fun PeekRoute()`, `@Composable fun PeekScreen(state: PeekUiState, modifier: Modifier = Modifier)`

- [ ] **Step 1: 문자열 추가**

```xml
    <string name="peek_recording">기록 중</string>
    <string name="peek_driver_unknown">운전자 미선택</string>
    <string name="peek_trip_label">이번 운행</string>
    <string name="peek_no_destination">목적지 없음 — 도착 후 물어볼게요</string>
    <string name="peek_source_saved">계기판 거리 · %1$d초 전 저장</string>
    <string name="peek_auto_end">멈춰 있으면 %1$s 뒤 기록을 마쳐요</string>
    <string name="peek_no_trip">기록 중인 운행이 없어요</string>
    <string name="peek_no_trip_hint">출발하면 자동으로 기록해요</string>
    <string name="odometer_label">누적 거리</string>
```

- [ ] **Step 2: 실패하는 테스트**

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/peek/PeekReducerTest.kt
package kr.autolog.vehicle.ui.peek

import kr.autolog.vehicle.data.interim.LiveTrip
import kr.autolog.vehicle.data.interim.SessionState
import kr.autolog.vehicle.data.interim.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeekReducerTest {
    private val trip = LiveTrip("t-1", 0L, 18_420.5, "판교", "u-1")

    @Test
    fun `기록 중이면 거리·목적지·저장 시각을 보여 준다`() {
        val state = reducePeek(SessionState.Recording(trip, 23.4, lastSavedAt = 10_000), 18_443.9, "김영업", "판교", now = 13_000)

        val r = state as PeekUiState.Recording
        assertEquals("23.4", r.tripKm)
        assertEquals("판교", r.destination)
        assertEquals(3, r.savedSecondsAgo)
        assertNull(r.autoEndInSec)
    }

    @Test
    fun `움직임 없음 자동 종료 대기면 남은 시간을 보여 준다`() {
        val candidate = SessionState.StopCandidate(trip, 23.4, StopReason.NO_MOVEMENT, since = 0, deadlineAt = 180_000)

        val r = reducePeek(candidate, null, null, null, now = 19_000) as PeekUiState.Recording

        assertEquals(161, r.autoEndInSec)
    }

    @Test
    fun `계기판을 못 읽으면 거리를 비운다`() {
        val r = reducePeek(SessionState.Recording(trip, null, 0), null, null, null, now = 0) as PeekUiState.Recording

        assertNull(r.tripKm)
    }

    @Test
    fun `운행이 없으면 누적거리만`() {
        val r = reducePeek(SessionState.Idle, 18_420.5, null, null, now = 0)

        assertEquals(PeekUiState.NoTrip("18,420.5"), r)
    }
}
```

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/peek/PeekScreenshotTest.kt
package kr.autolog.vehicle.ui.peek

import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.screenshot.captureAll
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PeekScreenshotTest {
    @Test
    fun recording() = captureAll("peek_recording") { PeekScreen(PeekUiState.Recording("김영업", "23.4", "판교 테크노밸리 거래처", 10, null)) }

    @Test
    fun autoEnd() = captureAll("peek_autoend") { PeekScreen(PeekUiState.Recording("김영업", "23.4", null, 4, 161)) }

    @Test
    fun noTrip() = captureAll("peek_notrip") { PeekScreen(PeekUiState.NoTrip("18,420.5")) }
}
```

- [ ] **Step 3: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*Peek*'`
Expected: FAIL — `Unresolved reference: reducePeek`

- [ ] **Step 4: 구현**

```kotlin
// .../ui/peek/PeekUiState.kt
package kr.autolog.vehicle.ui.peek

import kr.autolog.vehicle.data.interim.SessionState
import kr.autolog.vehicle.data.interim.StopReason
import kr.autolog.vehicle.ui.design.formatKm
import kr.autolog.vehicle.ui.design.remainingSeconds

sealed interface PeekUiState {
    data object Loading : PeekUiState

    /** [tripKm] null = 계기판 미확정. [autoEndInSec] = 움직임 없음·내비 도착 자동 종료까지 남은 초. */
    data class Recording(
        val driverName: String?,
        val tripKm: String?,
        val destination: String?,
        val savedSecondsAgo: Int,
        val autoEndInSec: Int?,
    ) : PeekUiState

    data class NoTrip(val odometerKm: String?) : PeekUiState
}

/** 스펙 7.0 내용 ①~⑤. 순수 함수(테스트 대상). */
internal fun reducePeek(
    state: SessionState,
    odometerKm: Double?,
    driverName: String?,
    destination: String?,
    now: Long,
): PeekUiState =
    when (state) {
        is SessionState.Recording ->
            PeekUiState.Recording(
                driverName = driverName,
                tripKm = formatKm(state.liveKm),
                destination = destination,
                savedSecondsAgo = ((now - state.lastSavedAt) / 1000).toInt().coerceAtLeast(0),
                autoEndInSec = null,
            )
        is SessionState.StopCandidate ->
            PeekUiState.Recording(
                driverName = driverName,
                tripKm = formatKm(state.liveKm),
                destination = destination,
                savedSecondsAgo = 0,
                autoEndInSec = if (state.reason == StopReason.GEAR_P) null else remainingSeconds(state.deadlineAt, now),
            )
        else -> PeekUiState.NoTrip(formatKm(odometerKm))
    }
```

```kotlin
// .../ui/peek/PeekViewModel.kt
package kr.autolog.vehicle.ui.peek

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.common.tickerFlow
import kr.autolog.vehicle.data.repository.ContextRepository
import kr.autolog.vehicle.data.repository.ProfileRepository
import kr.autolog.vehicle.data.repository.SessionRepository

/** 읽기 전용 — Action이 없다. */
class PeekViewModel(
    session: SessionRepository,
    profile: ProfileRepository,
    context: ContextRepository,
    clock: AppClock,
    ticker: Flow<Long> = tickerFlow(clock),
) : ViewModel() {
    val uiState: StateFlow<PeekUiState> =
        combine(session.state, session.totalOdometerKm, profile.currentDriver(), context.destinationLabel, ticker) {
                s, odo, driver, dest, now ->
            reducePeek(s, odo, driver?.name, dest, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeekUiState.Loading)
}
```

```kotlin
// .../ui/peek/PeekScreen.kt (교체)
package kr.autolog.vehicle.ui.peek

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.autolog.vehicle.R
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ChipTone
import kr.autolog.vehicle.ui.design.components.HeroMetric
import kr.autolog.vehicle.ui.design.components.StatusChip
import kr.autolog.vehicle.ui.design.durationText

@Composable
fun PeekRoute() {
    val graph = LocalAppGraph.current
    val vm = viewModel { PeekViewModel(graph.session, graph.profile, graph.context, graph.clock) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    PeekScreen(state)
}

/** 스펙 7.0. 클릭 가능한 요소 0개, 모션 없음. 넘치면 ⑤ → ④ 순으로 생략(세로 공간이 좁은 창). */
@Composable
fun PeekScreen(state: PeekUiState, modifier: Modifier = Modifier) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    Box(modifier.fillMaxSize().padding(Space.s6), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = Size.peekMaxWidth), verticalArrangement = Arrangement.spacedBy(Space.s5)) {
            when (state) {
                PeekUiState.Loading -> Unit
                is PeekUiState.Recording -> {
                    StatusChip(
                        text = stringResource(R.string.peek_recording) + " · " + (state.driverName ?: stringResource(R.string.peek_driver_unknown)),
                        tone = ChipTone.SUCCESS,
                        icon = Icons.Rounded.FiberManualRecord,
                    )
                    HeroMetric(
                        value = state.tripKm,
                        unit = stringResource(R.string.unit_km),
                        label = stringResource(R.string.peek_trip_label),
                        unavailableText = stringResource(R.string.distance_unavailable),
                        large = false,
                    )
                    Text(state.destination ?: stringResource(R.string.peek_no_destination), style = t.title, color = c.textPrimary)
                    Text(stringResource(R.string.peek_source_saved, state.savedSecondsAgo), style = t.body, color = c.textSecondary)
                    state.autoEndInSec?.let {
                        Text(stringResource(R.string.peek_auto_end, durationText(it)), style = t.body, color = c.warning)
                    }
                }
                is PeekUiState.NoTrip -> {
                    Text(stringResource(R.string.peek_no_trip), style = t.title, color = c.textPrimary)
                    Text(stringResource(R.string.peek_no_trip_hint), style = t.body, color = c.textSecondary)
                    HeroMetric(state.odometerKm, stringResource(R.string.unit_km), stringResource(R.string.odometer_label), stringResource(R.string.distance_unavailable), large = false)
                }
            }
        }
    }
}
```

- [ ] **Step 5: 통과 확인 + 스크린샷**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*Peek*' --tests '*AutoLogRootTest*' && G :vehicle-app:testDemoDebugUnitTest --tests '*PeekScreenshotTest*' -Proborazzi.test.record=true`
Expected: PASS, `peek_*_{wide,narrow,tall}_{dark,light}.png` 18장. NARROW에서 ①~⑤가 잘리지 않는지 확인한다.

- [ ] **Step 6: 커밋**

```bash
git add vehicle-app/src
git commit -m "feat(app): 정차 중 읽기 전용 Peek 패널 (F-03)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: ProfilePicker — 넷플릭스식 운전자 선택

**Files:**
- Create: `.../ui/profile/ProfilePickerUiState.kt`, `.../ui/profile/ProfilePickerViewModel.kt`
- Modify: `.../ui/profile/ProfilePickerScreen.kt`(임시 본문 교체), `strings.xml`
- Test: `.../test/.../ui/profile/ProfilePickerViewModelTest.kt`, `.../test/.../ui/profile/ProfilePickerScreenTest.kt`(동작 `ProfilePickerScreenTest` + 스크린샷 `ProfilePickerScreenshotTest`)

**Interfaces:**
- Consumes: `SessionRepository`, `ProfileRepository`, `DriverProfile`, `ProfileBadge`, `UnassignedTripItem`, `Navigator`, `Route`, `ProfileTile`, `TertiaryButton`, `ScrimSheet`, `LocalWindowLayout`, `WindowLayout`, `TestAppGraph`
- Produces: `data class ProfilePickerUiState(profiles: List<DriverProfile> = emptyList(), unassigned: UnassignedTripItem? = null, showAll: Boolean = false, loaded: Boolean = false)` + `val visible: List<DriverProfile>` + `val hasMore: Boolean`, `sealed interface ProfilePickerAction { Select(userId); Later; AssignPrevious(tripId, userId); ShowAll; HideAll }`, `class ProfilePickerViewModel(session, profile, navigator)`, `@Composable fun ProfilePickerRoute()`, `@Composable fun ProfilePickerScreen(state, onAction, modifier)`, `const val PICKER_VISIBLE_MAX = 5`

- [ ] **Step 1: 문자열 추가**

```xml
    <string name="picker_title">누가 운전하나요?</string>
    <string name="picker_later">나중에 선택</string>
    <string name="picker_more">다른 사람</string>
    <string name="picker_unassigned_title">이전 운행 운전자는 누구였나요?</string>
    <string name="picker_empty">아직 등록된 직원이 없어요</string>
    <string name="picker_all_title">운전자 선택</string>
```

- [ ] **Step 2: 실패하는 테스트**

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/profile/ProfilePickerViewModelTest.kt
package kr.autolog.vehicle.ui.profile

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.autolog.vehicle.TestAppGraph
import kr.autolog.vehicle.nav.Route
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePickerViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `타일 1탭으로 운전자를 정하고 Home으로 간다`() =
        runTest {
            val graph = TestAppGraph(backgroundScope)
            val vm = ProfilePickerViewModel(graph.session, graph.profile, graph.navigator)

            vm.onAction(ProfilePickerAction.Select("u-2"))

            assertEquals("u-2", graph.trip.currentDriver.value)
            assertEquals(listOf(Route.Home), graph.navigator.backStack.value)
        }

    @Test
    fun `나중에 선택하면 운전자 없이 Home으로 간다`() =
        runTest {
            val graph = TestAppGraph(backgroundScope)
            val vm = ProfilePickerViewModel(graph.session, graph.profile, graph.navigator)

            vm.onAction(ProfilePickerAction.Later)

            assertNull(graph.trip.currentDriver.value)
            assertEquals(Route.Home, graph.navigator.current)
        }

    @Test
    fun `배차 운전자가 첫 자리이고 미확정 운행 카드가 뜬다`() =
        runTest {
            val graph = TestAppGraph(backgroundScope)
            val vm = ProfilePickerViewModel(graph.session, graph.profile, graph.navigator)

            vm.uiState.test {
                val loaded = expectMostRecentItem().takeIf { it.loaded } ?: awaitItem()
                assertEquals("u-1", loaded.visible.first().userId)
                assertEquals("t-9", loaded.unassigned?.tripId)
            }
        }

    @Test
    fun `이전 운행 운전자를 고르면 카드가 사라진다`() =
        runTest {
            val graph = TestAppGraph(backgroundScope)
            val vm = ProfilePickerViewModel(graph.session, graph.profile, graph.navigator)

            vm.uiState.test {
                skipItems(1)
                vm.onAction(ProfilePickerAction.AssignPrevious("t-9", "u-1"))
                assertNull(expectMostRecentItem().unassigned)
            }
        }
}
```

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/profile/ProfilePickerScreenTest.kt
package kr.autolog.vehicle.ui.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.data.repository.DriverProfile
import kr.autolog.vehicle.data.repository.ProfileBadge
import kr.autolog.vehicle.data.repository.UnassignedTripItem
import kr.autolog.vehicle.screenshot.captureAll
import kr.autolog.vehicle.ui.design.AutoLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ProfilePickerScreenTest {
    @get:Rule val compose = createComposeRule()

    private val profiles =
        listOf(
            DriverProfile("u-1", "김영업", "영업1팀", ProfileBadge.SCHEDULED),
            DriverProfile("u-2", "이현장", "기술지원팀", ProfileBadge.RECENT),
            DriverProfile("u-3", "박관리", "총무팀"),
        )
    private val state =
        ProfilePickerUiState(
            profiles = profiles,
            unassigned = UnassignedTripItem("t-9", "09:00 본사 → 판교 거래처 · 23.4 km", profiles),
            loaded = true,
        )

    @Test
    fun `조작 예산 — 운전자 선택은 1탭`() {
        val actions = mutableListOf<ProfilePickerAction>()
        compose.setContent { AutoLogTheme { ProfilePickerScreen(state.copy(unassigned = null), onAction = { actions += it }) } }

        compose.onNodeWithText("이현장").performClick()

        assertEquals(listOf(ProfilePickerAction.Select("u-2")), actions)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ProfilePickerScreenshotTest {
    private val profiles =
        listOf(
            DriverProfile("u-1", "김영업", "영업1팀", ProfileBadge.SCHEDULED),
            DriverProfile("u-2", "이현장", "기술지원팀", ProfileBadge.RECENT),
            DriverProfile("u-3", "박관리", "총무팀"),
        )

    @Test
    fun picker() =
        captureAll("picker") {
            ProfilePickerScreen(
                ProfilePickerUiState(profiles, UnassignedTripItem("t-9", "09:00 본사 → 판교 거래처 · 23.4 km", profiles), loaded = true),
                onAction = {},
            )
        }
}
```

- [ ] **Step 3: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*ProfilePicker*'`
Expected: FAIL — `Unresolved reference: ProfilePickerViewModel`

- [ ] **Step 4: 구현**

```kotlin
// .../ui/profile/ProfilePickerUiState.kt
package kr.autolog.vehicle.ui.profile

import kr.autolog.vehicle.data.repository.DriverProfile
import kr.autolog.vehicle.data.repository.UnassignedTripItem

/** 스펙 7.1: 타일 최대 5개, 넘치면 6번째 자리에 [다른 사람]. */
const val PICKER_VISIBLE_MAX = 5

data class ProfilePickerUiState(
    val profiles: List<DriverProfile> = emptyList(),
    val unassigned: UnassignedTripItem? = null,
    val showAll: Boolean = false,
    val loaded: Boolean = false,
) {
    val visible: List<DriverProfile> get() = profiles.take(PICKER_VISIBLE_MAX)
    val hasMore: Boolean get() = profiles.size > PICKER_VISIBLE_MAX
}

sealed interface ProfilePickerAction {
    data class Select(val userId: String) : ProfilePickerAction

    data object Later : ProfilePickerAction

    data class AssignPrevious(val tripId: String, val userId: String) : ProfilePickerAction

    data object ShowAll : ProfilePickerAction

    data object HideAll : ProfilePickerAction
}
```

```kotlin
// .../ui/profile/ProfilePickerViewModel.kt
package kr.autolog.vehicle.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.autolog.vehicle.data.repository.ProfileRepository
import kr.autolog.vehicle.data.repository.SessionRepository
import kr.autolog.vehicle.data.repository.UnassignedTripItem
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route

class ProfilePickerViewModel(
    private val session: SessionRepository,
    profile: ProfileRepository,
    private val navigator: Navigator,
) : ViewModel() {
    private val unassigned = MutableStateFlow<UnassignedTripItem?>(null)
    private val showAll = MutableStateFlow(false)

    init {
        viewModelScope.launch { unassigned.value = profile.unassignedTrip() }
    }

    val uiState: StateFlow<ProfilePickerUiState> =
        combine(profile.orderedProfiles(), unassigned, showAll) { p, u, s ->
            ProfilePickerUiState(profiles = p, unassigned = u, showAll = s, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilePickerUiState())

    fun onAction(action: ProfilePickerAction) {
        viewModelScope.launch {
            when (action) {
                is ProfilePickerAction.Select -> {
                    session.selectDriver(action.userId)
                    navigator.replaceAll(Route.Home)
                }
                ProfilePickerAction.Later -> {
                    session.selectDriver(null)
                    navigator.replaceAll(Route.Home)
                }
                is ProfilePickerAction.AssignPrevious -> {
                    session.assignDriver(action.tripId, action.userId)
                    unassigned.value = null
                }
                ProfilePickerAction.ShowAll -> showAll.value = true
                ProfilePickerAction.HideAll -> showAll.value = false
            }
        }
    }
}
```

```kotlin
// .../ui/profile/ProfilePickerScreen.kt (교체)
package kr.autolog.vehicle.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.autolog.vehicle.R
import kr.autolog.vehicle.data.repository.DriverProfile
import kr.autolog.vehicle.data.repository.ProfileBadge
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.WindowLayout
import kr.autolog.vehicle.ui.design.components.ProfileTile
import kr.autolog.vehicle.ui.design.components.ScrimSheet
import kr.autolog.vehicle.ui.design.components.TertiaryButton

@Composable
fun ProfilePickerRoute() {
    val graph = LocalAppGraph.current
    val vm = viewModel { ProfilePickerViewModel(graph.session, graph.profile, graph.navigator) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    ProfilePickerScreen(state, vm::onAction)
}

/** 스펙 7.1. WIDE = 한 줄, NARROW·TALL = 2열(FlowRow가 처리). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePickerScreen(state: ProfilePickerUiState, onAction: (ProfilePickerAction) -> Unit, modifier: Modifier = Modifier) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val layout = LocalWindowLayout.current
    val tile = if (layout == WindowLayout.WIDE) Size.profileTileWide else Size.profileTileNarrow
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.s6, vertical = Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s6),
        ) {
            if (layout == WindowLayout.TALL) Spacer(Modifier.height(Space.s9))
            state.unassigned?.let { item -> UnassignedCard(item.label, item.candidates) { onAction(ProfilePickerAction.AssignPrevious(item.tripId, it)) } }
            Text(stringResource(R.string.picker_title), style = t.display, color = c.textPrimary, textAlign = TextAlign.Center)
            if (state.loaded && state.profiles.isEmpty()) {
                Text(stringResource(R.string.picker_empty), style = t.body, color = c.textSecondary)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s6, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Space.s6),
                maxItemsInEachRow = if (layout == WindowLayout.WIDE) PICKER_VISIBLE_MAX + 1 else 2,
            ) {
                state.visible.forEach { p -> p.Tile(tile) { onAction(ProfilePickerAction.Select(p.userId)) } }
                if (state.hasMore) MoreTile(tile) { onAction(ProfilePickerAction.ShowAll) }
            }
            TertiaryButton(stringResource(R.string.picker_later), onClick = { onAction(ProfilePickerAction.Later) })
        }
        if (state.showAll) {
            ScrimSheet {
                Text(stringResource(R.string.picker_all_title), style = t.headline, color = c.textPrimary)
                Spacer(Modifier.height(Space.s5))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(Size.profileTileNarrow),
                    horizontalArrangement = Arrangement.spacedBy(Space.s5),
                    verticalArrangement = Arrangement.spacedBy(Space.s5),
                    modifier = Modifier.fillMaxWidth().height(Size.profileTileWide * 2),
                ) {
                    items(state.profiles, key = { it.userId }) { p ->
                        p.Tile(Size.profileTileNarrow) { onAction(ProfilePickerAction.Select(p.userId)) }
                    }
                }
                TertiaryButton(stringResource(android.R.string.cancel), onClick = { onAction(ProfilePickerAction.HideAll) })
            }
        }
    }
}

@Composable
private fun DriverProfile.Tile(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) =
    ProfileTile(userId, name, dept, scheduled = badge == ProfileBadge.SCHEDULED, recent = badge == ProfileBadge.RECENT, onClick = onClick, tileSize = size)

@Composable
private fun MoreTile(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val c = AutoLogTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        Box(
            Modifier
                .size(size - Space.s4)
                .clip(RoundedCornerShape(Radius.xl))
                .background(c.surface2)
                .clickable(enabled = !LocalInputLocked.current, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.picker_more), tint = c.textSecondary, modifier = Modifier.size(Size.iconLg)) }
        Text(stringResource(R.string.picker_more), style = AutoLogTheme.type.title, color = c.textSecondary)
    }
}

/** 스펙 7.1 ④ / F-20: 후보 1탭으로 지정. */
@Composable
private fun UnassignedCard(label: String, candidates: List<DriverProfile>, onPick: (String) -> Unit) {
    val c = AutoLogTheme.colors
    Column(
        Modifier.widthIn(max = Size.sheetMaxWidth).fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.surface1).padding(Space.s5),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Text(stringResource(R.string.picker_unassigned_title), style = AutoLogTheme.type.title, color = c.textPrimary)
        Text(label, style = AutoLogTheme.type.body, color = c.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s5)) {
            candidates.forEach { p -> ProfileTile(p.userId + "-prev", p.name, null, scheduled = p.badge == ProfileBadge.SCHEDULED, recent = false, onClick = { onPick(p.userId) }, tileSize = Size.buttonXl) }
        }
    }
}
```
(미확정 카드의 타일은 `userId + "-prev"`로 shared element 키를 피한다 — 같은 사람이 위 그리드에도 있기 때문.)

- [ ] **Step 5: 통과 확인 + 스크린샷**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*ProfilePicker*' && G :vehicle-app:testDemoDebugUnitTest --tests '*ProfilePickerScreenshotTest*' -Proborazzi.test.record=true`
Expected: PASS, `picker_{wide,narrow,tall}_{dark,light}.png` 6장

- [ ] **Step 6: 커밋**

```bash
git add vehicle-app/src
git commit -m "feat(profile): 넷플릭스식 1탭 운전자 선택과 미확정 운행 카드 (F-15, F-20)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 14: Home — 4상태·자동 종료 안내·복구

**Files:**
- Create: `.../ui/home/HomeUiState.kt`, `.../ui/home/HomeViewModel.kt`, `.../ui/home/AutoEndPrompt.kt`, `.../ui/home/RecoveryDialog.kt`
- Modify: `.../ui/home/HomeScreen.kt`(임시 본문 교체), `strings.xml`
- Test: `.../test/.../ui/home/HomeReducerTest.kt`, `.../test/.../ui/home/HomeViewModelTest.kt`, `.../test/.../ui/home/HomeScreenTest.kt`(동작 `HomeScreenTest` + 스크린샷 `HomeScreenshotTest`)

**Interfaces:**
- Consumes: `SessionRepository`, `ProfileRepository`, `ContextRepository`, `Navigator`, `Route`, `AppClock`, `tickerFlow`, `SessionState`, `StopReason`, `MissedTrip`, `RecoveryChoice`, `DriverProfile`, `formatKm`, `remainingSeconds`, `progressOf`, `durationText`, 컴포넌트 전부, `LocalWindowLayout`, `LocalDriverSide`, `sharedAvatar`, `TestAppGraph`
- Produces: `enum class HomePhase { READY, RECORDING_PARKED, AUTO_ENDING, NEEDS_RECOVERY }`, `data class AutoEndUi(remainingSec: Int, progress: Float, reason: StopReason)`, `data class HomeUiState(...)`(아래), `sealed interface HomeAction { StartTrip; EndNow; KeepRecording; OpenProfilePicker; AcceptMissedTrip; IgnoreMissedTrip; Recover(choice) }`, `internal fun reduceHome(...)`, `class HomeViewModel(session, profile, context, navigator, clock, ticker = tickerFlow(clock))`, `@Composable fun HomeRoute()`, `@Composable fun HomeScreen(state, onAction, modifier)`, `@Composable fun AutoEndPrompt(state: AutoEndUi, tripKm: String?, destination: String?, onKeep, onEnd)`, `@Composable fun RecoveryDialog(extraKm: String?, onChoice: (RecoveryChoice) -> Unit)`

- [ ] **Step 1: 문자열 추가**

```xml
    <string name="home_ready_hint">출발하면 자동으로 기록해요</string>
    <string name="home_start">운행 시작</string>
    <string name="home_end">기록 종료</string>
    <string name="home_trip_label">이번 운행</string>
    <string name="home_elapsed">%1$d분째 기록 중</string>
    <string name="home_review_chip">확인 필요 %1$d건</string>
    <string name="home_choose_driver">운전자 선택</string>
    <string name="home_missed">앱이 꺼진 동안 %1$s km를 달렸어요</string>
    <string name="home_missed_add">추가</string>
    <string name="home_missed_ignore">무시</string>
    <string name="autoend_title">기록을 마칠까요?</string>
    <string name="autoend_remaining">%1$s 뒤 자동으로 마쳐요</string>
    <string name="autoend_keep">계속 기록</string>
    <string name="autoend_end">기록 종료</string>
    <string name="recovery_title">기록 중이던 운행이 있어요</string>
    <string name="recovery_body">마지막 저장 뒤 +%1$s km</string>
    <string name="recovery_record">기록하기</string>
    <string name="recovery_continue">계속 기록</string>
    <string name="recovery_delete">삭제</string>
    <string name="recovery_delete_confirm">이 운행을 지울까요? 되돌릴 수 없어요</string>
    <string name="recovery_delete_yes">지우기</string>
```

- [ ] **Step 2: 실패하는 테스트**

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/home/HomeReducerTest.kt
package kr.autolog.vehicle.ui.home

import kr.autolog.vehicle.data.interim.LiveTrip
import kr.autolog.vehicle.data.interim.MissedTrip
import kr.autolog.vehicle.data.interim.SessionState
import kr.autolog.vehicle.data.interim.StopReason
import kr.autolog.vehicle.data.repository.DriverProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeReducerTest {
    private val trip = LiveTrip("t-1", startTime = 0L, 18_420.5, "판교", "u-1")
    private val driver = DriverProfile("u-1", "김영업", "영업1팀")

    @Test
    fun `운행이 없으면 누적거리와 놓친 운행 배너`() {
        val s = reduceHome(SessionState.Idle, 18_420.5, MissedTrip(15.0, 0), driver, null, now = 0)

        assertEquals(HomePhase.READY, s.phase)
        assertEquals("18,420.5", s.odometerKm)
        assertEquals("15.0", s.missedTripKm)
    }

    @Test
    fun `기어 P 카운트다운은 남은 초와 진행률`() {
        val candidate = SessionState.StopCandidate(trip, 23.4, StopReason.GEAR_P, since = 0, deadlineAt = 5_000)

        val s = reduceHome(candidate, null, null, driver, "판교", now = 2_000)

        assertEquals(HomePhase.AUTO_ENDING, s.phase)
        assertEquals(3, s.autoEnd!!.remainingSec)
        assertEquals(0.6f, s.autoEnd!!.progress, 0.001f)
        assertEquals("23.4", s.tripKm)
    }

    @Test
    fun `자동 종료를 취소하고 주차 중이면 기록 중 화면과 경과 분`() {
        val s = reduceHome(SessionState.Recording(trip, 23.4, 0), null, null, driver, "판교", now = 35 * 60_000L)

        assertEquals(HomePhase.RECORDING_PARKED, s.phase)
        assertEquals(35, s.elapsedMin)
        assertNull(s.missedTripKm)
    }

    @Test
    fun `운행이 끝나 목적 확인이 남으면 확인 필요 1건`() {
        val s = reduceHome(SessionState.PendingReview(trip, 23.4, null), 18_443.9, null, driver, null, now = 0)

        assertEquals(HomePhase.READY, s.phase)
        assertEquals(1, s.reviewCount)
    }

    @Test
    fun `복구 상황이면 늘어난 거리를 보여 준다`() {
        val s = reduceHome(SessionState.NeedsRecovery(trip, 3.2), null, null, null, null, now = 0)

        assertEquals(HomePhase.NEEDS_RECOVERY, s.phase)
        assertEquals("3.2", s.recoveryExtraKm)
    }
}
```

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/home/HomeViewModelTest.kt
package kr.autolog.vehicle.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.autolog.vehicle.TestAppGraph
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.data.interim.SessionState
import kr.autolog.vehicle.data.interim.VehicleMotion
import kr.autolog.vehicle.nav.Route
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.graph() =
        TestAppGraph(backgroundScope, clock = AppClock { testScheduler.currentTime }, start = Route.Home)

    @Test
    fun `계속 기록을 누르면 자동 종료가 취소된다`() =
        runTest {
            val g = graph()
            val vm = HomeViewModel(g.session, g.profile, g.context, g.navigator, g.clock)
            g.trip.startSession()
            g.trip.setMotion(VehicleMotion.PARKED)

            vm.onAction(HomeAction.KeepRecording)

            assertTrue(g.trip.state.value is SessionState.Recording)
        }

    @Test
    fun `기록 종료를 누르면 바로 끝난다`() =
        runTest {
            val g = graph()
            val vm = HomeViewModel(g.session, g.profile, g.context, g.navigator, g.clock)
            g.trip.startSession()
            g.trip.setMotion(VehicleMotion.PARKED)

            vm.onAction(HomeAction.EndNow)

            assertTrue(g.trip.state.value is SessionState.PendingReview)
        }

    @Test
    fun `운전자 칩을 누르면 운전자 선택 화면이 열린다`() =
        runTest {
            val g = graph()
            val vm = HomeViewModel(g.session, g.profile, g.context, g.navigator, g.clock)

            vm.onAction(HomeAction.OpenProfilePicker)

            assertEquals(Route.ProfilePicker, g.navigator.current)
        }
}
```

```kotlin
// .../test/kotlin/kr/autolog/vehicle/ui/home/HomeScreenTest.kt
package kr.autolog.vehicle.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.data.interim.StopReason
import kr.autolog.vehicle.data.repository.DriverProfile
import kr.autolog.vehicle.screenshot.captureAll
import kr.autolog.vehicle.ui.design.AutoLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private val driver = DriverProfile("u-1", "김영업", "영업1팀")
    private val recording =
        HomeUiState(phase = HomePhase.RECORDING_PARKED, driver = driver, tripKm = "23.4", destination = "판교 테크노밸리 거래처", elapsedMin = 35)
    private val autoEnding =
        recording.copy(phase = HomePhase.AUTO_ENDING, autoEnd = AutoEndUi(remainingSec = 3, progress = 0.6f, reason = StopReason.GEAR_P))
    private val recovery = HomeUiState(phase = HomePhase.NEEDS_RECOVERY, recoveryExtraKm = "3.2")

    @Test
    fun `자동 종료 안내에서 계속 기록은 1탭`() {
        val actions = mutableListOf<HomeAction>()
        compose.setContent { AutoLogTheme { HomeScreen(autoEnding, onAction = { actions += it }) } }

        compose.onNodeWithText("계속 기록").performClick()

        assertEquals(listOf<HomeAction>(HomeAction.KeepRecording), actions)
    }

    @Test
    fun `삭제는 한 번 더 확인한다`() {
        val actions = mutableListOf<HomeAction>()
        compose.setContent { AutoLogTheme { HomeScreen(recovery, onAction = { actions += it }) } }

        compose.onNodeWithText("삭제").performClick()
        compose.onNodeWithText("지우기").performClick()

        assertEquals(listOf<HomeAction>(HomeAction.Recover(kr.autolog.vehicle.data.interim.RecoveryChoice.DELETE)), actions)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HomeScreenshotTest {
    private val driver = DriverProfile("u-1", "김영업", "영업1팀")
    private val recording =
        HomeUiState(phase = HomePhase.RECORDING_PARKED, driver = driver, tripKm = "23.4", destination = "판교 테크노밸리 거래처", elapsedMin = 35)

    @Test
    fun ready() =
        captureAll("home_ready") {
            HomeScreen(HomeUiState(phase = HomePhase.READY, driver = driver, odometerKm = "18,420.5", reviewCount = 1, missedTripKm = "15.0"), onAction = {})
        }

    @Test
    fun recording() = captureAll("home_recording") { HomeScreen(recording, onAction = {}) }

    @Test
    fun autoEnding() =
        captureAll("home_autoend") {
            HomeScreen(recording.copy(phase = HomePhase.AUTO_ENDING, autoEnd = AutoEndUi(3, 0.6f, StopReason.GEAR_P)), onAction = {})
        }

    @Test
    fun recovery() = captureAll("home_recovery") { HomeScreen(HomeUiState(phase = HomePhase.NEEDS_RECOVERY, recoveryExtraKm = "3.2"), onAction = {}) }
}
```

- [ ] **Step 3: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*Home*'`
Expected: FAIL — `Unresolved reference: reduceHome`

- [ ] **Step 4: 구현 — 상태·ViewModel**

```kotlin
// .../ui/home/HomeUiState.kt
package kr.autolog.vehicle.ui.home

import kr.autolog.vehicle.data.interim.MissedTrip
import kr.autolog.vehicle.data.interim.RecoveryChoice
import kr.autolog.vehicle.data.interim.SessionState
import kr.autolog.vehicle.data.interim.StopReason
import kr.autolog.vehicle.data.repository.DriverProfile
import kr.autolog.vehicle.ui.design.formatKm
import kr.autolog.vehicle.ui.design.progressOf
import kr.autolog.vehicle.ui.design.remainingSeconds

/** 스펙 7.2 상태 4가지. */
enum class HomePhase { READY, RECORDING_PARKED, AUTO_ENDING, NEEDS_RECOVERY }

data class AutoEndUi(val remainingSec: Int, val progress: Float, val reason: StopReason)

data class HomeUiState(
    val phase: HomePhase = HomePhase.READY,
    val driver: DriverProfile? = null,
    val odometerKm: String? = null,
    /** 기록 중 거리. null = 계기판 미확정(추정 금지). */
    val tripKm: String? = null,
    val destination: String? = null,
    val elapsedMin: Int? = null,
    val autoEnd: AutoEndUi? = null,
    val reviewCount: Int = 0,
    val missedTripKm: String? = null,
    val recoveryExtraKm: String? = null,
)

sealed interface HomeAction {
    data object StartTrip : HomeAction

    data object EndNow : HomeAction

    data object KeepRecording : HomeAction

    data object OpenProfilePicker : HomeAction

    data object AcceptMissedTrip : HomeAction

    data object IgnoreMissedTrip : HomeAction

    data class Recover(val choice: RecoveryChoice) : HomeAction
}

internal fun reduceHome(
    state: SessionState,
    odometerKm: Double?,
    missed: MissedTrip?,
    driver: DriverProfile?,
    destination: String?,
    now: Long,
): HomeUiState {
    val base = HomeUiState(driver = driver, odometerKm = formatKm(odometerKm), destination = destination)
    return when (state) {
        SessionState.Idle -> base.copy(missedTripKm = formatKm(missed?.gapKm))
        is SessionState.PendingReview -> base.copy(reviewCount = 1, missedTripKm = formatKm(missed?.gapKm))
        is SessionState.Recording ->
            base.copy(
                phase = HomePhase.RECORDING_PARKED,
                tripKm = formatKm(state.liveKm),
                elapsedMin = ((now - state.trip.startTime) / 60_000).toInt().coerceAtLeast(0),
            )
        is SessionState.StopCandidate ->
            base.copy(
                phase = HomePhase.AUTO_ENDING,
                tripKm = formatKm(state.liveKm),
                autoEnd =
                    AutoEndUi(
                        remainingSec = remainingSeconds(state.deadlineAt, now),
                        progress = progressOf(state.since, state.deadlineAt, now),
                        reason = state.reason,
                    ),
            )
        is SessionState.NeedsRecovery -> base.copy(phase = HomePhase.NEEDS_RECOVERY, recoveryExtraKm = formatKm(state.extraKm))
    }
}
```

```kotlin
// .../ui/home/HomeViewModel.kt
package kr.autolog.vehicle.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.common.tickerFlow
import kr.autolog.vehicle.data.repository.ContextRepository
import kr.autolog.vehicle.data.repository.ProfileRepository
import kr.autolog.vehicle.data.repository.SessionRepository
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route

class HomeViewModel(
    private val session: SessionRepository,
    profile: ProfileRepository,
    context: ContextRepository,
    private val navigator: Navigator,
    clock: AppClock,
    ticker: Flow<Long> = tickerFlow(clock),
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> =
        combine(
            combine(session.state, session.totalOdometerKm, session.missedTrip, ::Triple),
            profile.currentDriver(),
            context.destinationLabel,
            ticker,
        ) { (s, odo, missed), driver, dest, now -> reduceHome(s, odo, missed, driver, dest, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onAction(action: HomeAction) {
        viewModelScope.launch {
            when (action) {
                HomeAction.StartTrip -> session.startTrip()
                HomeAction.EndNow -> session.endNow()
                HomeAction.KeepRecording -> session.keepRecording()
                HomeAction.OpenProfilePicker -> navigator.push(Route.ProfilePicker)
                HomeAction.AcceptMissedTrip -> session.acceptMissedTrip()
                HomeAction.IgnoreMissedTrip -> session.ignoreMissedTrip()
                is HomeAction.Recover -> session.resolveRecovery(action.choice)
            }
        }
    }
}
```

- [ ] **Step 5: 구현 — 자동 종료 안내·복구 다이얼로그**

```kotlin
// .../ui/home/AutoEndPrompt.kt
package kr.autolog.vehicle.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.DriverSide
import kr.autolog.vehicle.ui.design.LocalDriverSide
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.WindowLayout
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.ScrimSheet
import kr.autolog.vehicle.ui.design.components.SecondaryButton
import kr.autolog.vehicle.ui.design.durationText

/**
 * 스펙 7.2.1 / 디자인 언어 2.6·11장. 아무것도 안 누르면 끝나는 것이 기본값이므로
 * 기본값에서 벗어나는 [계속 기록]이 PrimaryButton(운전자 쪽), 기본값을 앞당기는 [기록 종료]가 Secondary.
 */
@Composable
fun AutoEndPrompt(state: AutoEndUi, tripKm: String?, destination: String?, onKeep: () -> Unit, onEnd: () -> Unit) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val motion = AutoLogTheme.motion
    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = if (motion.isStatic) snap() else tween(durationMillis = 1_000, easing = LinearEasing),
        label = "autoEndProgress",
    )
    ScrimSheet {
        Text(stringResource(R.string.autoend_title), style = t.headline, color = c.textPrimary)
        Spacer(Modifier.height(Space.s3))
        Text(
            listOfNotNull(tripKm?.let { "$it ${stringResource(R.string.unit_km)}" }, destination).joinToString(" · "),
            style = t.title,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(Space.s3))
        Text(stringResource(R.string.autoend_remaining, durationText(state.remainingSec)), style = t.body, color = c.textSecondary)
        Spacer(Modifier.height(Space.s4))
        Box(Modifier.fillMaxWidth().height(Size.progressBar).clip(CircleShape).background(c.surface3)) {
            Box(Modifier.fillMaxWidth(progress).height(Size.progressBar).clip(CircleShape).background(c.accent))
        }
        Spacer(Modifier.height(Space.s6))
        val keep: @Composable (Modifier) -> Unit = { m -> PrimaryButton(stringResource(R.string.autoend_keep), onKeep, m) }
        val end: @Composable (Modifier) -> Unit = { m -> SecondaryButton(stringResource(R.string.autoend_end), onEnd, m, height = Size.buttonLg) }
        if (LocalWindowLayout.current == WindowLayout.WIDE) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                if (LocalDriverSide.current == DriverSide.START) {
                    keep(Modifier.weight(1f))
                    end(Modifier.weight(1f))
                } else {
                    end(Modifier.weight(1f))
                    keep(Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                end(Modifier.fillMaxWidth())
                keep(Modifier.fillMaxWidth())
            }
        }
    }
}
```

```kotlin
// .../ui/home/RecoveryDialog.kt
package kr.autolog.vehicle.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.data.interim.RecoveryChoice
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.DangerButton
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.ScrimSheet
import kr.autolog.vehicle.ui.design.components.SecondaryButton
import kr.autolog.vehicle.ui.design.components.TertiaryButton

/** Backend F-10 복구. 되돌릴 수 없는 [삭제]만 한 번 더 묻는다(디자인 언어 2.6). */
@Composable
fun RecoveryDialog(extraKm: String?, onChoice: (RecoveryChoice) -> Unit) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    ScrimSheet {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            if (!confirmingDelete) {
                Text(stringResource(R.string.recovery_title), style = t.headline, color = c.textPrimary)
                extraKm?.let { Text(stringResource(R.string.recovery_body, it), style = t.title, color = c.textSecondary) }
                PrimaryButton(stringResource(R.string.recovery_record), { onChoice(RecoveryChoice.RECORD) }, Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.recovery_continue), { onChoice(RecoveryChoice.CONTINUE) }, Modifier.fillMaxWidth())
                TertiaryButton(stringResource(R.string.recovery_delete), { confirmingDelete = true }, Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.recovery_delete_confirm), style = t.title, color = c.textPrimary)
                DangerButton(stringResource(R.string.recovery_delete_yes), { onChoice(RecoveryChoice.DELETE) }, Modifier.fillMaxWidth())
                SecondaryButton(stringResource(android.R.string.cancel), { confirmingDelete = false }, Modifier.fillMaxWidth())
            }
        }
    }
}
```

- [ ] **Step 6: 구현 — HomeScreen (임시 본문 교체)**

```kotlin
// .../ui/home/HomeScreen.kt
package kr.autolog.vehicle.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.autolog.vehicle.R
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.DriverSide
import kr.autolog.vehicle.ui.design.LocalDriverSide
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.WindowLayout
import kr.autolog.vehicle.ui.design.components.Avatar
import kr.autolog.vehicle.ui.design.components.Banner
import kr.autolog.vehicle.ui.design.components.BannerAction
import kr.autolog.vehicle.ui.design.components.ChipTone
import kr.autolog.vehicle.ui.design.components.HeroMetric
import kr.autolog.vehicle.ui.design.components.PrimaryButton
import kr.autolog.vehicle.ui.design.components.StatusChip
import kr.autolog.vehicle.ui.design.sharedAvatar

@Composable
fun HomeRoute() {
    val graph = LocalAppGraph.current
    val vm = viewModel { HomeViewModel(graph.session, graph.profile, graph.context, graph.navigator, graph.clock) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, vm::onAction)
}

/** 스펙 7.2. WIDE = 동작 컬럼 40%(운전자 쪽) + hero 60%, NARROW·TALL = 세로 스택 + 하단 고정 주 버튼. */
@Composable
fun HomeScreen(state: HomeUiState, onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier) {
    val layout = LocalWindowLayout.current
    Box(modifier.fillMaxSize()) {
        if (layout == WindowLayout.WIDE) {
            Row(Modifier.fillMaxSize().padding(Space.s7), horizontalArrangement = Arrangement.spacedBy(Space.s7)) {
                val actions =
                    @Composable {
                        Column(Modifier.weight(0.4f).fillMaxHeight()) {
                            HomeChips(state, onAction)
                            Spacer(Modifier.weight(1f))
                            HomePrimary(state, onAction)
                        }
                    }
                val hero =
                    @Composable {
                        Column(Modifier.weight(0.6f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                            HomeHero(state, onAction)
                        }
                    }
                if (LocalDriverSide.current == DriverSide.START) {
                    actions()
                    hero()
                } else {
                    hero()
                    actions()
                }
            }
        } else {
            val margin = if (layout == WindowLayout.TALL) Space.tallMargin else Space.s6
            Column(Modifier.fillMaxSize().padding(margin)) {
                HomeChips(state, onAction)
                Spacer(Modifier.weight(if (layout == WindowLayout.TALL) 0.5f else 1f))
                HomeHero(state, onAction)
                Spacer(Modifier.weight(1f))
                HomePrimary(state, onAction)
            }
        }
        val autoEnd = state.autoEnd
        if (state.phase == HomePhase.AUTO_ENDING && autoEnd != null) {
            AutoEndPrompt(autoEnd, state.tripKm, state.destination, onKeep = { onAction(HomeAction.KeepRecording) }, onEnd = { onAction(HomeAction.EndNow) })
        }
        if (state.phase == HomePhase.NEEDS_RECOVERY) {
            RecoveryDialog(state.recoveryExtraKm) { onAction(HomeAction.Recover(it)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeChips(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        val d = state.driver
        StatusChip(
            text = d?.name ?: stringResource(R.string.home_choose_driver),
            tone = if (d == null) ChipTone.ACCENT else ChipTone.NEUTRAL,
            leading =
                if (d != null) {
                    { Avatar(d.userId, d.name, Size.avatarChip, Modifier.sharedAvatar(d.userId)) }
                } else {
                    null
                },
            onClick = { onAction(HomeAction.OpenProfilePicker) },
        )
        if (state.reviewCount > 0) {
            StatusChip(stringResource(R.string.home_review_chip, state.reviewCount), ChipTone.WARNING, icon = Icons.Rounded.ErrorOutline)
        }
    }
}

@Composable
private fun ColumnScope.HomeHero(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    if (state.phase == HomePhase.READY) {
        state.missedTripKm?.let {
            Banner(
                stringResource(R.string.home_missed, it),
                listOf(
                    BannerAction(stringResource(R.string.home_missed_add)) { onAction(HomeAction.AcceptMissedTrip) },
                    BannerAction(stringResource(R.string.home_missed_ignore)) { onAction(HomeAction.IgnoreMissedTrip) },
                ),
            )
            Spacer(Modifier.height(Space.s4))
        }
        HeroMetric(state.odometerKm, stringResource(R.string.unit_km), stringResource(R.string.odometer_label), stringResource(R.string.distance_unavailable), large = false)
        Text(stringResource(R.string.home_ready_hint), style = t.title, color = c.textSecondary)
    } else {
        HeroMetric(state.tripKm, stringResource(R.string.unit_km), stringResource(R.string.home_trip_label), stringResource(R.string.distance_unavailable))
        state.destination?.let { Text(it, style = t.title, color = c.textPrimary) }
        state.elapsedMin?.let { Text(stringResource(R.string.home_elapsed, it), style = t.body, color = c.textSecondary) }
    }
}

@Composable
private fun HomePrimary(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    when (state.phase) {
        HomePhase.READY -> PrimaryButton(stringResource(R.string.home_start), { onAction(HomeAction.StartTrip) }, Modifier.fillMaxWidth(), height = Size.buttonXl)
        HomePhase.RECORDING_PARKED, HomePhase.AUTO_ENDING ->
            PrimaryButton(stringResource(R.string.home_end), { onAction(HomeAction.EndNow) }, Modifier.fillMaxWidth(), height = Size.buttonXl)
        HomePhase.NEEDS_RECOVERY -> Unit
    }
}
```

- [ ] **Step 7: 통과 확인 + 스크린샷**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*Home*' && G :vehicle-app:testDemoDebugUnitTest --tests '*HomeScreenshotTest*' -Proborazzi.test.record=true`
Expected: PASS, `home_{ready,recording,autoend,recovery}_{wide,narrow,tall}_{dark,light}.png` 24장. WIDE에서 [운행 시작]·[계속 기록]이 왼쪽(운전자 쪽)에 있는지 본다.

- [ ] **Step 8: 커밋**

```bash
git add vehicle-app/src
git commit -m "feat(home): 4상태 Home과 기어 P 5초 자동 종료 안내·복구 다이얼로그 (F-02, F-04, F-10)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 15: 데모 패널 (demo flavor 전용)

**Files:**
- Create: `vehicle-app/src/demo/kotlin/kr/autolog/vehicle/demo/DemoScenario.kt`, `vehicle-app/src/demo/kotlin/kr/autolog/vehicle/demo/DemoPanel.kt`, `vehicle-app/src/demo/res/values/strings.xml`
- Modify: `vehicle-app/src/demo/kotlin/kr/autolog/vehicle/di/FlavorGraph.kt`(`demoOverlay` 연결)
- Test: `vehicle-app/src/testDemo/kotlin/kr/autolog/vehicle/demo/DemoScenarioTest.kt`

**Interfaces:**
- Consumes: `FakeTripSession`, `VehicleMotion`, `MissedTrip`, `SecondaryButton`, `ScrimSheet`, `LocalInputLocked`
- Produces: `class DemoScenario(trip: FakeTripSession, scope: CoroutineScope, clock: AppClock)` + `playDrive()`, `stop()`, `park()`, `addKm(km)`, `missedTrip()`, `recovery()`; `@Composable fun DemoPanelOverlay(scenario: DemoScenario)`

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// vehicle-app/src/testDemo/kotlin/kr/autolog/vehicle/demo/DemoScenarioTest.kt
package kr.autolog.vehicle.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.data.interim.FakeTripSession
import kr.autolog.vehicle.data.interim.SessionState
import kr.autolog.vehicle.data.interim.VehicleMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoScenarioTest {
    @Test
    fun `주행 시나리오는 출발→신호 대기→출발→주차 후 5초 뒤 자동 종료`() =
        runTest {
            val clock = AppClock { testScheduler.currentTime }
            val trip = FakeTripSession(backgroundScope, clock)
            val demo = DemoScenario(trip, backgroundScope, clock)

            demo.playDrive()
            runCurrent()
            assertEquals(VehicleMotion.MOVING, trip.motion.value)
            advanceTimeBy(10_500)
            assertEquals(VehicleMotion.STOPPED, trip.motion.value)
            advanceTimeBy(15_500)
            assertEquals(VehicleMotion.PARKED, trip.motion.value)
            advanceTimeBy(5_100)
            runCurrent()

            val review = trip.state.value as SessionState.PendingReview
            assertEquals(9.0, review.distanceKm!!, 0.001) // 10 × 0.4 + 10 × 0.5
            assertTrue(trip.totalOdometerKm.value!! > 18_420.5)
        }
}
```

- [ ] **Step 2: 실패 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*DemoScenarioTest*'`
Expected: FAIL — `Unresolved reference: DemoScenario`

- [ ] **Step 3: 구현**

```xml
<!-- vehicle-app/src/demo/res/values/strings.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="demo_title">데모 컨트롤</string>
    <string name="demo_play_drive">주행 시나리오 재생</string>
    <string name="demo_stop">정차(신호 대기)</string>
    <string name="demo_park">주차(P)</string>
    <string name="demo_add_km">+1 km</string>
    <string name="demo_missed">놓친 운행 15 km</string>
    <string name="demo_recovery">강제 종료 후 복구</string>
    <string name="demo_close">닫기</string>
</resources>
```

```kotlin
// vehicle-app/src/demo/kotlin/kr/autolog/vehicle/demo/DemoScenario.kt
package kr.autolog.vehicle.demo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.data.interim.FakeTripSession
import kr.autolog.vehicle.data.interim.MissedTrip
import kr.autolog.vehicle.data.interim.VehicleMotion

/**
 * 스펙 6.7 데모 패널의 동작. VHAL 없이 시연 영상을 찍는다.
 * 주행 중에는 앱이 물러나므로 시나리오가 스스로 주차까지 진행해야 한다.
 */
class DemoScenario(private val trip: FakeTripSession, private val scope: CoroutineScope, private val clock: AppClock) {
    private var job: Job? = null

    /** 10초 주행(4 km) → 5초 신호 대기 → 10초 주행(5 km) → 주차. */
    fun playDrive() {
        job?.cancel()
        job =
            scope.launch {
                trip.setMotion(VehicleMotion.MOVING)
                repeat(10) {
                    delay(1_000)
                    trip.addKm(0.4)
                }
                trip.setMotion(VehicleMotion.STOPPED)
                delay(5_000)
                trip.setMotion(VehicleMotion.MOVING)
                repeat(10) {
                    delay(1_000)
                    trip.addKm(0.5)
                }
                trip.setMotion(VehicleMotion.PARKED)
            }
    }

    fun stop() = trip.setMotion(VehicleMotion.STOPPED)

    fun park() = trip.setMotion(VehicleMotion.PARKED)

    fun addKm(km: Double = 1.0) = trip.addKm(km)

    fun missedTrip() = trip.setMissedTrip(MissedTrip(15.0, clock.nowMs() - 7_200_000))

    fun recovery() = trip.enterRecovery(3.2)
}
```

```kotlin
// vehicle-app/src/demo/kotlin/kr/autolog/vehicle/demo/DemoPanel.kt
package kr.autolog.vehicle.demo

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.ScrimSheet
import kr.autolog.vehicle.ui.design.components.SecondaryButton

/** 오른쪽 위 96 dp 영역 길게 누르기 → 패널. 데모 전용이라 잠금을 무시한다(정차 중에도 주차로 되돌릴 수 있게). */
@Composable
fun DemoPanelOverlay(scenario: DemoScenario) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Box(Modifier.size(Size.iconButton).pointerInput(Unit) { detectTapGestures(onLongPress = { open = true }) })
    }
    if (open) {
        CompositionLocalProvider(LocalInputLocked provides false) {
            ScrimSheet {
                Text(stringResource(R.string.demo_title), style = AutoLogTheme.type.headline, color = AutoLogTheme.colors.textPrimary)
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    val item = @Composable { label: Int, action: () -> Unit ->
                        SecondaryButton(stringResource(label), { action(); open = false }, Modifier.fillMaxWidth())
                    }
                    item(R.string.demo_play_drive) { scenario.playDrive() }
                    item(R.string.demo_stop) { scenario.stop() }
                    item(R.string.demo_park) { scenario.park() }
                    item(R.string.demo_add_km) { scenario.addKm() }
                    item(R.string.demo_missed) { scenario.missedTrip() }
                    item(R.string.demo_recovery) { scenario.recovery() }
                    item(R.string.demo_close) { }
                }
            }
        }
    }
}
```

`demo/.../di/FlavorGraph.kt`의 `DemoAppGraph`에서 마지막 줄을 아래로 바꾼다:

```kotlin
    private val scenario = kr.autolog.vehicle.demo.DemoScenario(trip, scope, clock)
    override val demoOverlay: (@Composable () -> Unit)? = { kr.autolog.vehicle.demo.DemoPanelOverlay(scenario) }
```
(`DemoAppGraph(scope: CoroutineScope)`의 `scope`를 `private val scope: CoroutineScope`로 바꿔 프로퍼티로 쓸 수 있게 한다.)

- [ ] **Step 4: 통과 확인**

Run: `G :vehicle-app:testDemoDebugUnitTest --tests '*DemoScenarioTest*' && G :vehicle-app:assembleDemoDebug`
Expected: PASS, 빌드 성공

- [ ] **Step 5: 커밋**

```bash
git add vehicle-app/src/demo vehicle-app/src/testDemo
git commit -m "feat(app): 데모 패널과 주행 시나리오 (demo flavor)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 16: 디자인 게이트 패키지 — 전체 게이트·스크린샷·맥 점검표

**Files:**
- Modify: `docs/verification.md`, `README.md`
- Create: `docs/design-gate-2026-11-11.md`
- Test: 전체 게이트

**Interfaces:**
- Consumes: 앞 작업 전부
- Produces: 사용자 디자인 리뷰용 스크린샷 세트와 맥 점검 절차

- [ ] **Step 1: 전체 게이트 (CONVENTIONS 5장과 같은 명령)**

Run:
```bash
G ktlintFormat
G ktlintCheck :vehicle-app:testDemoDebugUnitTest :vehicle-app:assembleDemoDebug :vehicle-app:assembleVehicleDebug
G :vehicle-app:recordRoborazziDemoDebug
G :vehicle-app:verifyRoborazziDemoDebug
ls vehicle-app/src/test/screenshots | wc -l
```
Expected: 모두 `BUILD SUCCESSFUL`, 스크린샷 50장(components 2 + peek 18 + picker 6 + home 24).

- [ ] **Step 2: 스크린샷 자체 리뷰**

각 PNG를 열어 `docs/design-language.md` 13장 체크리스트로 본다. 특히:
- 잘림·겹침이 없다(NARROW의 Home 자동 종료 시트, TALL의 프로필 2열)
- 다크·라이트 모두 글자가 Asta Sans다(V-09)
- 거리 숫자 폭이 흔들리지 않는다 — `home_recording`과 `home_autoend`의 "23.4" 위치 비교(V-05)
- WIDE에서 주 버튼이 왼쪽(운전자 쪽) 아래다

문제가 있으면 해당 작업의 화면 코드를 고치고 Step 1을 다시 돈다.

- [ ] **Step 3: `docs/design-gate-2026-11-11.md` 작성**

```markdown
# 디자인 게이트 (2026-11-11)

사용자 승인 전 확인용. 스크린샷 위치: `vehicle-app/src/test/screenshots/` (Linux 기준 렌더링).

## 볼 것
| 화면 | 파일 | 확인 포인트 |
|---|---|---|
| 운전자 선택 | `picker_*` | 배차 운전자 링·첫 자리, 1탭, 미확정 운행 카드 |
| Home 대기 | `home_ready_*` | 누적거리 숫자, 놓친 운행 배너, [운행 시작] 위치 |
| Home 기록 중(주차) | `home_recording_*` | 140 sp 거리, 목적지·경과 |
| 자동 종료 안내 | `home_autoend_*` | "5초 뒤 자동으로 마쳐요", 진행 막대, [계속 기록]이 주 버튼 |
| 복구 | `home_recovery_*` | 삭제만 한 번 더 확인 |
| Peek | `peek_*` | 버튼 없음, 5줄 이하, 사이드 창(NARROW)에서 읽힘 |

## 맥 에뮬레이터에서 (스펙 10.3)
1. `./gradlew :vehicle-app:installDemoDebug` → 앱 실행 → 운전자 선택 → Home
2. 오른쪽 위 길게 누르기 → 데모 패널 → [주행 시나리오 재생] → 앱이 물러남(V-12) → 25초 뒤 앱을 다시 열면 자동 종료 안내 또는 확인 필요 1건
3. [정차(신호 대기)] → Peek, 누를 것 없음
4. 윈도 바로 portrait ↔ landscape, 다른 앱과 1:1 분할 → 잘림·겹침 없음, 상태 유지
5. 차량 설정에서 주간·야간 전환 → 깜빡임 없음(V-08)
6. FormFactorConsole 프리셋별 dp·DPI·px 기록(V-01) → `docs/verification.md`
7. vehicle flavor 설치 후 VHAL로 기어 D·속도 주입 → CarUxRestrictions 값 로그(`adb logcat -s AutoLog/ui`)(V-04, V-11)

## 결정이 필요한 것
- 토큰 값 조정(색·크기)이 있으면 `docs/design-language.md`를 먼저 고치고 `ContrastTest`를 통과시킨다.
- V-01 측정값으로 `WindowLayout.kt` 임계값과 `Screenshots.kt` 크기를 바꾼다.
```

- [ ] **Step 4: README에 실행 방법 추가**

`README.md` 끝에 추가:

```markdown
## 실행

```bash
./gradlew :vehicle-app:installDemoDebug     # 전부 Fake. 오른쪽 위를 길게 누르면 데모 패널
./gradlew :vehicle-app:installVehicleDebug  # Pleos 에뮬레이터용(CarUxRestrictions 실제, 나머지는 앱 A·B 연결 전까지 Fake)
```

개발 환경은 [docs/dev-setup.md](docs/dev-setup.md).
```

- [ ] **Step 5: 커밋**

```bash
git add vehicle-app/src/test/screenshots docs/design-gate-2026-11-11.md docs/verification.md README.md
git commit -m "docs(app): 디자인 게이트 스크린샷 세트와 맥 점검 절차

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 6: 사용자에게 디자인 게이트 요청**

스크린샷 경로와 `docs/design-gate-2026-11-11.md`를 알리고 승인을 받는다. 승인 전에는 다음 계획(ReviewTrip·영수증·운행기록부·설정)에 들어가지 않는다. 작업 후 WSL 디스크 정리: `rm -rf vehicle-app/build/intermediates vehicle-app/build/tmp` (빌드 산출물이 커진다).
