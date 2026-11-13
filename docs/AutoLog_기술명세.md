# AutoLog 기술 명세서

AutoLog_기획서.md의 7장(기술 아키텍처)과 4장(SDK 활용 계획)을 구현 수준으로 상세화한 문서다. 공모전 기획서에는 요약본을 넣고, 이 문서는 팀 내부 개발 기준 및 심사 Q&A 대응 자료로 쓴다.

> 표기 규칙
> - **[확인됨]** Pleos 공식 문서 또는 공식 샘플(Pleos-Playground/pleos-sample-vehicle-app)에서 확인한 내용
> - **[검증 필요]** 문서상 존재하지만 에뮬레이터·실차에서 동작·값 범위·갱신 주기를 아직 확인하지 않은 내용
> - **[가정]** 팀이 설계상 정한 값. 측정 후 조정

---

## 1. 전체 시스템 구성

**원칙: 운행 기록의 원본은 차량 Room이다. 서버는 폰·관리자가 차에 넣어주는 것만 중계하는 우체통(inbox)이고, 엑셀·메일은 차가 직접 만든다.**

```
┌───────────────────────────────────────────────────────────────┐
│  Pleos Connect 차량 (AAOS, Pleos_Connect_v2.1 / Android 14)        │
│                                                               │
│  AutoLog Vehicle App (Kotlin / Compose)  ← 장부의 원본            │
│   ├─ Vehicle SDK ─────────► Odometer / Gear / CarInfo / Fuel      │
│   ├─ NaviHelper SDK ──────► 목적지 / 경로상태 / 최근 목적지         │
│   ├─ Fused Location SDK ──► 출발·도착 좌표                        │
│   ├─ Gleo AI SDK ─────────► STT(목적·주유 음성 입력), TTS           │
│   ├─ ProfilePicker · Home · ReviewTrip · Receipts · Report · Settings │
│   ├─ TripRecordingService (Foreground) ─ TripSessionManager        │
│   ├─ Room: trips/stops/users/places/schedules/expenses/recipients   │
│   ├─ ReportBuilder (Apache POI → XLSX) · Mailer (Jakarta Mail SMTP) │
│   ├─ ReviewCalculator (검토 열)                                    │
│   └─ InboxSyncWorker (서버 우체통 → Room, 단방향)                    │
└──────────┬───────────────────────────────┬────────────────────┘
           │ GET /inbox · POST /inbox/{id}/ack │ SMTP 587 (회계 담당자에게 XLSX)
           ▼                               ▼
┌──────────────────────────────┐    [회사 메일 서버 / SendGrid]
│  Relay Server (Kotlin, Spring Boot) │
│   devices · orgs · admins · inbox   │
│   ├─ /devices        기기 등록       │
│   ├─ /r/{code}       카메라 페이지    │
│   ├─ /r/{code}/receipt → Gemini OCR → inbox(RECEIPT) │
│   ├─ /inbox, /inbox/{id}/ack        │
│   └─ /admin/*  직원·장소·배차·수신자 → inbox(USERS…) │
│   PostgreSQL · MinIO(영수증 30일)    │
└───────▲──────────────▲───────────────┘
        │              │
   폰 브라우저      운영 웹(경량, React)
   (QR → 촬영)     차량·직원·장소·배차·수신자
```

**설계 요점**

- 차량 앱은 **읽기 전용**이다. Vehicle SDK의 CONTROL_* 권한은 선언하지 않는다.
- Pleos SDK는 차 안에서 읽는 도구다. 서버는 Pleos API를 호출하지 않는다.
- **차→서버 방향은 없다.** 운행·비용·엑셀은 서버에 올라가지 않는다. 서버가 죽어도 운행 기록·엑셀·메일은 된다.
- 서버→차는 `inbox` 하나로 통일한다. 영수증 OCR 결과도, 직원 목록도 같은 우체통의 다른 종류 편지다.
- 운행 중 앱은 Foreground Service로 뒤에서 기록하고 운전자는 내비를 본다. 꺼져 있던 구간은 계기판 차이로 복구한다.
- 모든 거리·목적·운전자·금액에 `source`와 `status`를 붙인다.
- 저장소는 모노레포: `:shared`(inbox payload DTO) · `:vehicle-core`(차량 로직, 화면 없음) · `:app`(차량 화면) · `:relay`(서버) · `web/`(운영 웹 + 카메라 페이지). 의존 방향 `app → vehicle-core → shared ← relay`(7장).
- **로드맵:** `POST /trips`(운행 업로드)를 추가하면 다차량 합산·폰 조회가 열린다. Room은 그대로 원본, 서버는 사본.

---

## 2. 차량 앱 — Pleos SDK 사용 명세

### 2.1 개발 환경 **[확인됨]**

| 항목 | 값 |
|---|---|
| 플랫폼 | Pleos Connect (Android Automotive OS 기반) |
| 에뮬레이터 | Pleos Connect Emulator (Android Studio AVD, 샘플 기준 `Pleos_Connect_v2.1`, Android 14, 2560×1440) |
| 언어 / UI | Kotlin 2.0.x / Jetpack Compose (Material 3), 코루틴 + Flow |
| JDK | 17 (AGP 8.x 요구사항) |
| Android SDK | minSdk 28, compile/target 35 |
| Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| NaviHelper SDK | `ai.pleos.playground:NaviHelper:3.0.x` |
| Fused Location SDK | `ai.pleos.playground:FusedLocation:1.0.4` |
| Gleo AI SDK (STT/TTS) | 아티팩트 좌표는 Gleo AI SDK 프로젝트 설정 문서에서 확인 [검증 필요] |
| Maven 저장소 | `https://nexus-playground.pleos.ai/repository/maven-releases/` |

Pleos SDK는 **Kotlin으로 작성된 Android 라이브러리**이고 공식 샘플 앱(pleos-sample-vehicle-app)도 Kotlin이다 **[확인됨]**. 따라서 샘플 코드를 거의 그대로 가져올 수 있고, 콜백을 코루틴 `suspendCancellableCoroutine`으로 감싸 순차 코드로 쓸 수 있다. Gradle은 **Kotlin DSL(`.kts`)**, 의존성은 Version Catalog(`gradle/libs.versions.toml`)로 관리한다. Android Studio 새 프로젝트 마법사 기본값이 이 조합이다.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")   // Pleos SDK
    }
}
rootProject.name = "AutoLog"

// 저장소에 있는 Gradle 모듈 목록일 뿐, APK 포함 여부와는 무관하다.
// APK에 무엇이 들어가는지는 vehicle-app/build.gradle.kts 의 dependencies 가 결정한다.
// 2026-11-11 AutoLog-Frontend와 합치며 화면 모듈이 :app → :vehicle-app (프론트 스펙 D-33·5.3)
include(":shared", ":vehicle-core", ":relay", ":vehicle-app")
```

```toml
# gradle/libs.versions.toml (발췌)
[versions]
agp = "8.8.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
compose-bom = "2024.12.01"
room = "2.6.1"
work = "2.9.1"
lifecycle = "2.8.7"
coroutines = "1.9.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
serialization = "1.7.3"
spring-boot = "3.4.1"
spring-dependency = "1.1.7"
pleos-vehicle = "2.3.2"
pleos-navi = "3.0.5"
pleos-fused = "1.0.4"

[libraries]
# Pleos Connect SDK (vehicle-core)
pleos-vehicle  = { module = "ai.pleos.playground:Vehicle",       version.ref = "pleos-vehicle" }
pleos-navi     = { module = "ai.pleos.playground:NaviHelper",    version.ref = "pleos-navi" }
pleos-fused    = { module = "ai.pleos.playground:FusedLocation", version.ref = "pleos-fused" }
# Compose (app)
compose-bom    = { module = "androidx.compose:compose-bom",      version.ref = "compose-bom" }
compose-ui     = { module = "androidx.compose.ui:ui" }
compose-m3     = { module = "androidx.compose.material3:material3" }
compose-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-tooling = { module = "androidx.compose.ui:ui-tooling" }
activity-compose = { module = "androidx.activity:activity-compose", version = "1.9.3" }
lifecycle-vm-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
# 로직 (vehicle-core)
lifecycle-service = { module = "androidx.lifecycle:lifecycle-service", version.ref = "lifecycle" }
room-runtime   = { module = "androidx.room:room-runtime",  version.ref = "room" }
room-ktx       = { module = "androidx.room:room-ktx",      version.ref = "room" }
room-compiler  = { module = "androidx.room:room-compiler", version.ref = "room" }
work-ktx       = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
security-crypto = { module = "androidx.security:security-crypto", version = "1.1.0-alpha06" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
retrofit       = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx = { module = "com.squareup.retrofit2:converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp         = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
android-mail   = { module = "com.sun.mail:android-mail", version = "1.6.7" }
android-activation = { module = "com.sun.mail:android-activation", version = "1.6.7" }
# 공용 (shared)
kotlinx-serialization = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
junit          = { module = "junit:junit", version = "4.13.2" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library     = { id = "com.android.library", version.ref = "agp" }        # vehicle-core
kotlin-android      = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm          = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }  # shared
kotlin-compose      = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization= { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-spring       = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
kotlin-jpa          = { id = "org.jetbrains.kotlin.plugin.jpa", version.ref = "kotlin" }
ksp                 = { id = "com.google.devtools.ksp", version.ref = "ksp" }
spring-boot         = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency   = { id = "io.spring.dependency-management", version.ref = "spring-dependency" }
ktlint              = { id = "org.jlleitschuh.gradle.ktlint", version = "12.1.2" }
```

```kotlin
// build.gradle.kts (프로젝트 루트)
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
}
subprojects { apply(plugin = "org.jlleitschuh.gradle.ktlint") }   // ./gradlew ktlintFormat · ktlintCheck
```

차량 쪽은 **두 모듈**로 나뉜다. Pleos SDK·Room·SMTP 등 로직 의존성은 전부 `:vehicle-core`(Android library)에 있고, `:app`(화면)은 Compose와 `project(":vehicle-core")`만 가진다. 빌드하면 `vehicle-core`가 APK 안에 합쳐진다.

```kotlin
// vehicle-core/build.gradle.kts — 차량 로직 (화면 없음). 이 모듈은 Compose/UI 를 모른다.
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
        buildConfigField("String", "RELAY_BASE_URL", "\"http://10.0.2.2:8080/\"")   // 에뮬레이터에서 호스트 PC
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    api(project(":shared"))                       // inbox DTO. :relay 는 절대 넣지 않는다

    // Pleos Connect SDK
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navi)
    implementation(libs.pleos.fused)
    // implementation("ai.pleos.playground:SpeechToText:<ver>")   // Gleo AI SDK 좌표 확인 후 [검증 필요]

    api(libs.coroutines.android)                  // 상태 노출 (app 이 collect)
    implementation(libs.lifecycle.service)        // Foreground Service
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    implementation(libs.work.ktx)
    implementation(libs.security.crypto)          // SMTP 앱 비밀번호 암호화 저장
    implementation(libs.retrofit); implementation(libs.retrofit.kotlinx); implementation(libs.okhttp)   // inbox 수신
    implementation(libs.android.mail); implementation(libs.android.activation)                        // SMTP

    testImplementation(libs.junit)
}
```

```kotlin
// (예전) app/build.gradle.kts — 차량 앱 화면 (Compose). 로직은 :vehicle-core 가 담당한다.
// 지금 화면 모듈은 vehicle-app/build.gradle.kts(데모·실차 flavor, Roborazzi) — 프론트 스펙 5.3
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "kr.autolog.vehicle"
    compileSdk = 35
    defaultConfig { applicationId = "kr.autolog.vehicle"; minSdk = 28; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    buildTypes {
        release { isMinifyEnabled = true; isShrinkResources = true
                  proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
}

dependencies {
    implementation(project(":vehicle-core"))      // 차량 로직 (→ :shared 포함). :relay 는 절대 넣지 않는다
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui); implementation(libs.compose.m3); implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.activity.compose); implementation(libs.lifecycle.vm.compose); implementation(libs.lifecycle.runtime.compose)
    testImplementation(libs.junit)
}
```

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

### 2.2 권한 선언 **[확인됨: Vehicle 2.3.2 AAR의 `@RequiresPermission`, 2026-11-09]**

Vehicle SDK 권한은 `pleos.car.permission.CAR_*`(읽기) / `CONTROL_CAR_*`(제어) 형식이며 `protectionLevel="normal"`이라 런타임 다이얼로그 없이 매니페스트 선언만으로 동작한다. AutoLog는 읽기 권한만 선언한다. 상수는 `ai.pleos.playground.vehicle.constant.PleosPermission`에 있다.

```xml
<!-- vehicle-core AndroidManifest.xml -->
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />     <!-- Odometer, Gear -->
<uses-permission android:name="pleos.car.permission.CAR_INFO" />      <!-- CarInfo(VIN) -->
<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />    <!-- Fuel -->
<uses-permission android:name="pleos.car.permission.FUSED_LOCATION" />  <!-- Fused Location -->
<!-- 시동 상태(IGNITION_STATE)는 Pleos 공개 API가 없어 AAOS CarPropertyManager로 읽는다 -->
<uses-permission android:name="android.car.permission.CAR_POWERTRAIN" />
```

이전 판의 `CAR_MILEAGE`·`CAR_DRIVING_MODE`는 SDK에 없다. Vehicle 서비스 바인딩 권한(`VEHICLE_BINDING`)과 `queries`는 SDK 매니페스트가 자동 병합한다.

### 2.3 SDK 콜백 → 코루틴 어댑터

Pleos SDK의 조회 API는 전부 `onComplete` / `onFailed` 콜백 쌍이다. 중첩을 피하기 위해 공통 어댑터 하나를 두고 모든 게이트웨이가 쓴다.

```kotlin
// vehicle/SdkCall.kt
suspend fun <T> sdkCall(
    block: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit
): T = suspendCancellableCoroutine { cont ->
    block(
        { value -> if (cont.isActive) cont.resume(value) },
        { e     -> if (cont.isActive) cont.resumeWithException(e) }
    )
}

// 리스너형 API → Flow
fun <L, T> sdkListenerFlow(
    register: (L) -> Unit,
    unregister: (L) -> Unit,
    makeListener: (emit: (T) -> Unit, fail: (Exception) -> Unit) -> L
): Flow<T> = callbackFlow {
    val listener = makeListener({ trySend(it) }, { close(it) })
    register(listener)
    awaitClose { unregister(listener) }
}
```

### 2.4 Vehicle SDK 호출 패턴 **[확인됨: Vehicle 2.3.2 AAR 시그니처, 2026-11-09 / 에뮬레이터 동작은 `sdk-findings.md`]**

순서: **initialize → capability 확인 → get → listener 등록 → (종료 시) unregister → release**. `Vehicle.initialize()`는 콜백이 없어 연결 완료 시점을 알 수 없다. 그래서 첫 조회가 `onFailed`면 5초 간격으로 최대 12회 다시 시도한다(F-01). 조회는 전부 `getXxx(onSuccess, onFailed)` 형태이고, 리스너는 모두 `VehicleListener.onFailed(Exception)`을 상속한다.

```kotlin
// vehicle/VehicleGateway.kt — SDK import는 이 패키지에만 둔다
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener

class VehicleGateway(context: Context, private val clock: Clock) {
    private val vehicle = Vehicle(context.applicationContext)

    fun start() = vehicle.initialize()   // 콜백 없음
    fun stop()  = vehicle.release()

    // ---- Odometer: 누적 주행거리(km, Float) — 권한 CAR_DRIVE ----------
    suspend fun readTotalDistance(): OdometerReading? = runCatching {
        val odo = vehicle.odometer
        val cap = sdkCall { ok, fail -> odo.checkCurrentDistanceDrivenCapabilities(ok, fail) }
        if (cap.accessType != AccessType.READ && cap.accessType != AccessType.READ_WRITE) return null
        val raw: Float = sdkCall { ok, fail -> odo.getCurrentDistanceDriven(ok, fail) }
        OdometerReading(km = raw.toExactKm(), at = clock.millis())
    }.getOrNull()                         // 실패는 null → distanceSource = UNAVAILABLE (추정 금지)

    fun totalDistance(): Flow<Double> = sdkListenerFlow(
        register   = { l: DistanceDrivenListener -> vehicle.odometer.registerDistanceDriven(l) },
        unregister = { l -> vehicle.odometer.unregisterDistanceDriven(l) },
        makeListener = { emit, fail ->
            object : DistanceDrivenListener {
                override fun onDistanceDrivenUpdated(km: Float) = emit(km.toExactKm())
                override fun onFailed(e: Exception)             = fail(e)
            }
        }
    )

    // ---- Gear: P/R/N/D — 권한 CAR_DRIVE --------------------------------
    fun gear(): Flow<VehicleGear> = sdkListenerFlow(
        register   = { l: GearSelectionListener -> vehicle.gear.registerGearSelection(l) },
        unregister = { l -> vehicle.gear.unregisterGearSelection(l) },
        makeListener = { emit, fail ->
            object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear) = emit(gear)
                override fun onFailed(e: Exception)        = fail(e)
            }
        }
    )
}

/** Float 계기판 값을 10진수 표기 그대로 Double로. 18443.9f.toDouble()은 18443.900390625가 된다. */
private fun Float.toExactKm(): Double = BigDecimal(toString()).toDouble()

data class OdometerReading(val km: Double, val at: Long)
```

SDK 콜백은 메인 스레드에서 올 수 있다. `Flow`는 `flowOn(Dispatchers.Default)`로 돌리고, Room 쓰기는 주입받은 IO 디스패처로 넘긴다. Gateway는 SDK 타입(`VehicleGear`)을 밖으로 내보내지 않고 `session/`이 쓰는 자체 enum으로 바꿔서 내보낸다.

**AutoLog가 쓰는 Vehicle SDK 도메인과 용도**

| 도메인 | API | AutoLog 용도 | 권한 | 상태 |
|---|---|---|---|---|
| **Odometer** | `checkCurrentDistanceDrivenCapabilities` · `getCurrentDistanceDriven` · `registerDistanceDriven` (`Float`, km) | 운행 시작·종료 스냅샷, 10초 체크포인트, 주행 중 거리 | `CAR_DRIVE` | [확인됨: 시그니처] [검증 필요: 소수점·갱신 주기]. 내부적으로 VHAL `PERF_ODOMETER`를 읽는다 |
| **Gear** | `getCurrentGearSelectionState` · `registerGearSelection` (`VehicleGear`: PARK·DRIVE·REVERSE·NEUTRAL·UNKNOWN·1~9) | 주차(P)·주행 판정, P 순간 즉시 저장 + 자동 종료(F-04, F-13) | `CAR_DRIVE` | [확인됨: 시그니처] [검증 필요: 에뮬레이터 반응] |
| **CarInfo** | `checkVINInfoCapabilities` · `getVINInfo` · `getCRNInfo` (`String`) | 차량 식별 보조(설정 화면) | `CAR_INFO` | [검증 필요: 에뮬레이터에서 값 제공 여부] |
| **Fuel** | `checkFuelPercentCapabilities` · `getFuelPercent` · `registerFuelPercent` (`Int`, %) | 주유 이벤트 보조 감지(연료량 급증) | `CAR_ENERGY` | [확인됨: 시그니처, 보조 신호로만 사용] |
| (AAOS) 시동 | `CarPropertyManager` `IGNITION_STATE` | OFF·ACC가 되는 순간 즉시 확정(받으면 보너스) | `android.car.permission.CAR_POWERTRAIN` | Pleos 공개 API 없음. [검증 필요] |
| **EvBattery** | — | EV 충전비 확장(로드맵) | — | 확장 |

**정정(2026-11-09):** 이전 판은 `DrivingMode`를 기어 P/D로 가정했다. 실제 `DrivingMode`는 주행 모드(`NORMAL / ECO / SPORT`)라 AutoLog는 쓰지 않는다. 기어는 `Gear` 도메인으로 읽는다. 거리 단위(km/mile)를 돌려주는 공개 API도 없어서 km로 간주한다([검증 필요]).

**사용하지 않는 것:** DrivingMode, Door, Window, HVAC, Seat 등 제어 도메인 전부. Toll 도메인은 통행료 확장 시 검토.

### 2.5 NaviHelper SDK 호출 패턴 **[확인됨]**

```kotlin
// navi/NaviGateway.kt
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

class NaviGateway(context: Context) {
    private val navi = NaviHelper(context)

    sealed interface NaviEvent {
        data class Destination(val info: DestinationInfo) : NaviEvent
        data class RouteState(val info: RouteStateInfo)   : NaviEvent
        data class Location(val info: LocationInfo)       : NaviEvent
        data class Recent(val list: List<DestinationInfo>): NaviEvent
    }

    // 콜백 메서드명·파라미터 타입은 SDK 3.0.x 레퍼런스에서 확인 [검증 필요]
    val events: Flow<NaviEvent> = callbackFlow {
        val listener = object : NaviHelperEventListener {
            override fun onDestinationInfo(info: DestinationInfo)        { trySend(NaviEvent.Destination(info)) }
            override fun onRouteStateInfo(info: RouteStateInfo)          { trySend(NaviEvent.RouteState(info)) }
            override fun onCurrentLocationInfo(info: LocationInfo)       { trySend(NaviEvent.Location(info)) }
            override fun onRecentDestinationInfo(list: List<DestinationInfo>) { trySend(NaviEvent.Recent(list)) }
        }
        navi.addListener(listener)
        awaitClose { navi.removeListener(listener) }
    }

    fun start() = navi.initialize()
    fun stop()  = navi.release()

    fun requestDestination() = navi.getDestinationInfo()        // 현재 목적지 → events
    fun requestRouteState()  = navi.getRouteStateInfo()         // 안내 중 여부·잔여 거리
    fun requestLocation()    = navi.getCurrentLocationInfo()    // 현재 위치(내비 기준)
    fun requestRecent()      = navi.getRecentDestinationInfo()  // 최근 목적지(출발지 추정 보조)
}
```

| API | AutoLog 용도 | 시점 |
|---|---|---|
| `getDestinationInfo()` | 운행 목적지 라벨·좌표 → SmartPurpose 입력 | 세션 시작 직후, 목적지 변경 콜백 시 |
| `getRouteStateInfo()` | 안내 중 여부, 도착 판정 보조 | 주기 폴링(30초) + 콜백 |
| `getCurrentLocationInfo()` | 내비 기준 현재 위치. Fused Location 콜백이 아직 없을 때의 폴백 | 세션 시작·종료 시 1회 |
| `getRecentDestinationInfo()` | 목적지 미설정 운행의 출발지·도착지 후보 제시 | 종료 확인 UI |

`requestRoute`, `cancelRoute`, `addWaypoint` 등 **내비를 조작하는 API는 사용하지 않는다** (운전자 주의 분산 및 기획 원칙).

### 2.6 Fused Location SDK 호출 패턴 **[확인됨]**

Fused Location SDK는 차량의 GPS·IMU·카메라 입력을 융합한 위치를 제공한다. API는 `initialize` / `release` / `registerFusedLocationCallback` / `unregisterFusedLocationCallback` 네 개뿐이다.

```kotlin
// location/LocationGateway.kt
import ai.pleos.playground.location.fused.FusedLocation

class LocationGateway(context: Context, scope: CoroutineScope) {
    private val fused = FusedLocation(context.applicationContext)

    // 콜백 메서드명·파라미터 타입은 SDK 1.0.x 레퍼런스에서 확인 [검증 필요]
    private val fixes: Flow<LocationFix> = callbackFlow {
        val listener = object : FusedLocationListener {
            override fun onLocationChanged(info: FusedLocationInfo) { trySend(LocationFix.from(info)) }
            override fun onFailed(e: Exception)                      { close(e) }
        }
        fused.registerFusedLocationCallback(listener)
        awaitClose { fused.unregisterFusedLocationCallback(listener) }
    }

    /** 최신 좌표 1개만 유지. 콜백이 아직 없으면 null → NaviHelper 현재위치 폴백 */
    val latest: StateFlow<LocationFix?> = fixes
        .catch { emit(LocationFix.UNAVAILABLE) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun start() = fused.initialize()
    fun stop()  = fused.release()
}

data class LocationFix(val lat: Double, val lng: Double, val accuracyM: Double?, val at: Long) {
    companion object {
        val UNAVAILABLE = LocationFix(Double.NaN, Double.NaN, null, 0L)
        fun from(info: FusedLocationInfo) = LocationFix(info.latitude, info.longitude, info.accuracy, System.currentTimeMillis())
    }
}
```

**AutoLog에서의 사용 규칙**

| 시점 | 동작 |
|---|---|
| 세션 시작(RECORDING 진입) | `latest.value` → `originLat/Lng` 저장. null이면 NaviHelper 현재위치, 그것도 없으면 직전 운행 도착지 |
| 세션 종료(FINALIZING) | `latest.value` → `destinationLat/Lng` 저장. NaviHelper 목적지 좌표와 함께 서버로 전송 |
| 주행 중 | `StateFlow`가 최신값만 들고 있고 **저장하지 않는다**. 경로 polyline은 만들지 않는다(개인정보 원칙) |
| 서버 측 | 좌표 → `places.radius_m`(기본 150 m) 안의 등록 장소에 매칭 → SmartPurpose 입력 |

정확도 값(accuracy)이 SDK 응답에 포함되면 함께 저장해, 반경 매칭 시 `radius_m + accuracyM`으로 판정한다.

### 2.7 Gleo AI SDK — SpeechToText (2차 통합)

정차 상태의 ReviewTrip 화면에서만 사용한다. 주행 중에는 마이크 버튼을 노출하지 않는다.

```kotlin
// speech/SpeechGateway.kt
class SpeechGateway(context: Context) {
    // 클래스·메서드명은 Gleo AI SDK SpeechToText 레퍼런스에서 확인 [검증 필요]
    private val stt = SpeechToText(context.applicationContext)

    fun start() = stt.initialize()
    fun stop()  = stt.release()

    /** "판교 A고객사 미팅" → 인식 텍스트 */
    suspend fun listenOnce(): String = sdkCall { ok, fail ->
        stt.startRecognition(object : SpeechToTextListener {
            override fun onResult(text: String) = ok(text)
            override fun onError(e: Exception)  = fail(e)
        })
    }
}
```

인식 텍스트 처리는 단순 규칙으로 한다: 등록 장소·거래처명과 문자열 유사도 매칭 → 목적 카테고리 후보 제시, 원문은 `purposeNote`에 저장. 사용자는 인식 결과를 화면에서 보고 저장을 확정한다. LLM SDK로 비고 요약하는 것은 로드맵.

TextToSpeech는 선택 기능으로, 세션 종료 시 "운행이 끝났습니다. 23.4킬로미터, 거래처 방문으로 기록할까요?"를 읽어주는 데만 쓴다.

### 2.8 운행 세션 상태기계 (TripSessionManager)

```
IDLE
 │  startSession()  [사용자 탭 or 자동 트리거]
 ▼
RECORDING ──── 진입 시 origin 좌표 스냅샷(Fused Location) + TripRecordingService 시작. totalDistanceFlow로 distance 갱신(10초 체크포인트), navi events로 destination 갱신
 │  stopCandidate(reason)  [정차 감지: Gear=PARK(→ 즉시 Odometer 재조회·저장) / RouteState=도착 / Odometer 불변 10초 / 사용자 탭·알림 확인]
 ▼
STOP_CANDIDATE ── 이유별 대기 → deadlineAt (세션이 유일한 시계, 화면·알림은 deadlineAt만 본다)
 │                 GEAR_P = T_GEAR_P_SEC 5초 · NO_MOVEMENT·NAVI_ARRIVED = T_STOP_SEC 180초 · 시동 OFF = 대기 없이 FINALIZING
 │  ├─ 대기 중 재출발(MOVING) 또는 [계속 기록] → RECORDING (Stop 레코드만 남김)
 │  └─ deadlineAt 경과 or [기록 종료] → FINALIZING
 ▼
FINALIZING ── end_odometer 재조회, dest 좌표 스냅샷(Fused Location), distance = end − start, 검증
 │  ├─ 검증 OK  → PENDING_REVIEW (목적 확인 UI 노출)
 │  └─ 누락/이상 → NEEDS_RECOVERY (사용자 수동 보정 안내)
 ▼
CONFIRMED ── Room 저장 후 SyncWorker 큐 등록
```

```kotlin
// session/TripSessionManager.kt (골격)
class TripSessionManager(
    private val vehicle: VehicleGateway,
    private val navi: NaviGateway,
    private val location: LocationGateway,
    private val dao: TripDao,
    private val scope: CoroutineScope,          // SupervisorJob + Dispatchers.Default
    private val stopTimeout: Duration = 180.seconds,
) {
    sealed interface State {
        data object Idle : State
        data class Recording(val trip: TripEntity, val liveKm: Double) : State
        data class StopCandidate(val trip: TripEntity, val since: Long) : State
        data class PendingReview(val trip: TripEntity) : State
        data class NeedsRecovery(val trip: TripEntity, val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val mutex = Mutex()                 // 상태 전이는 한 번에 하나만
    private var stopTimer: Job? = null

    suspend fun startSession(vehicleId: String, userId: String?) = mutex.withLock {
        val odo = vehicle.readTotalDistance()
        val fix = location.latest.value
        val trip = TripEntity.start(vehicleId, userId, odo, fix)
        dao.upsert(trip)
        _state.value = State.Recording(trip, 0.0)
        observeWhileRecording(trip)
    }

    private fun observeWhileRecording(trip: TripEntity) {
        scope.launch {
            vehicle.totalDistanceFlow()
                .map { it - trip.startOdometerKm!! }
                .collect { km -> (state.value as? State.Recording)?.let { _state.value = it.copy(liveKm = km) } }
        }
        scope.launch {
            navi.events.filterIsInstance<NaviGateway.NaviEvent.Destination>()
                .collect { dao.updateDestination(trip.tripId, it.info) }
        }
        scope.launch {                          // 10초 체크포인트 (Odometer 콜백 스로틀)
            vehicle.totalDistanceFlow().sample(10.seconds)
                .collect { km -> dao.checkpoint(trip.tripId, km, System.currentTimeMillis()) }
        }
        scope.launch {                          // 기어 P → 시동 OFF 직전일 가능성. 즉시 저장
            vehicle.gear().filter { it == Gear.PARK }
                .collect { vehicle.readTotalDistance()?.let { dao.checkpoint(trip.tripId, it.km, it.at) }; stopCandidate(StopReason.GEAR_P) }
        }
    }

    suspend fun stopCandidate() = mutex.withLock {
        val rec = state.value as? State.Recording ?: return
        _state.value = State.StopCandidate(rec.trip, System.currentTimeMillis())
        stopTimer = scope.launch { delay(stopTimeout); finalize() }
    }

    suspend fun resumeFromStop() = mutex.withLock {
        val sc = state.value as? State.StopCandidate ?: return
        stopTimer?.cancel()
        dao.insertStop(StopEntity.from(sc))
        _state.value = State.Recording(sc.trip, 0.0)
    }

    suspend fun finalize() = mutex.withLock {
        val trip = (state.value as? State.StopCandidate)?.trip ?: (state.value as? State.Recording)?.trip ?: return
        val end = vehicle.readTotalDistance()            // 실패하면 null
        val fix = location.latest.value
        val finalized = trip.finalizeWith(end, fix)
        dao.upsert(finalized)
        _state.value = when {
            end == null                      -> State.NeedsRecovery(finalized, "odometer unavailable")
            !finalized.passesSanityChecks()  -> State.NeedsRecovery(finalized, "distance out of range")
            else                             -> State.PendingReview(finalized)
        }
    }
}
```

**자동 트리거 전략(2단계)**

| 단계 | 시작 조건 | 종료 조건 | 전제 |
|---|---|---|---|
| 기본(MVP 필수) | 앱 실행 후 "운행 시작" 탭 또는 앱 포그라운드 진입 시 자동 제안 | "운행 종료" 탭 또는 내비 도착 콜백 | 없음 |
| 자동(지원 시) | `totalDistance()` 증가 감지 / `gear()` D 전환 | Gear=PARK 5초 / 거리 불변 180초 / 시동 OFF | 백그라운드 서비스 허용 여부 [검증 필요] |

**검증 규칙(FINALIZING)** [가정]

- `end_odometer ≥ start_odometer`, 차이 ≤ 1,500 km (1회 운행 상한)
- 세션 시간 ≥ 60초 또는 거리 ≥ 0.3 km (아니면 '이동 없음'으로 폐기 제안)
- odometer 조회 실패 시 `distanceSource = UNAVAILABLE`, `distanceKm = null` — **추정값으로 채우지 않는다**

### 2.9 로컬 저장 (Room)

```kotlin
// data/TripEntity.kt
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val tripId: String,                // UUID v7
    val vehicleId: String,
    val userId: String?,
    val startTime: Long,
    val endTime: Long? = null,
    val startOdometerKm: Double?,
    val endOdometerKm: Double? = null,
    val lastOdometerKm: Double? = null,           // 10초 체크포인트
    val lastSeenAt: Long? = null,
    val driverSource: DriverSource? = null,       // DRIVER_SELECTED | SCHEDULE | ASSIGNMENT | PREVIOUS_TRIP | SELF_CLAIMED | MANAGER_ASSIGNED
    val distanceKm: Double? = null,
    val distanceSource: DistanceSource = DistanceSource.VEHICLE_ODOMETER,
    val destinationLabel: String? = null, val destinationLat: Double? = null, val destinationLng: Double? = null,
    val destinationSource: PlaceSource? = null,     // NAVI_HELPER | FUSED_LOCATION | USER_SELECTED
    val originLabel: String? = null,      val originLat: Double? = null,      val originLng: Double? = null,
    val originSource: PlaceSource? = null,          // FUSED_LOCATION | NAVI_CURRENT | PREVIOUS_TRIP_END | USER_SELECTED
    val locationAccuracyM: Double? = null,
    val purposeCategory: String? = null, val purposeDetail: String? = null,
    val purposeNote: String? = null,                // STT 원문
    val purposeInputMode: InputMode? = null,        // TAP | VOICE
    val purposeSource: PurposeSource = PurposeSource.UNSET,   // RULE_SUGGESTED | USER_CONFIRMED | UNSET
    val status: TripStatus = TripStatus.RECORDING,  // RECORDING | PENDING_REVIEW | CONFIRMED | NEEDS_RECOVERY
    val syncState: SyncState = SyncState.LOCAL,     // LOCAL | QUEUED | SYNCED | CONFLICT
    val rawSnapshotJson: String? = null,            // SDK 원본 응답 보존(감사용)
)

@Entity(tableName = "stops")      data class StopEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val tripId: String, val startTime: Long, val endTime: Long?, val odometerKm: Double?, val label: String?)
@Entity(tableName = "sync_queue") data class SyncQueueEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val entityType: String, val entityId: String, val attempts: Int = 0, val lastError: String? = null)

// data/TripDao.kt
@Dao
interface TripDao {
    @Upsert suspend fun upsert(trip: TripEntity)
    @Insert suspend fun insertStop(stop: StopEntity)
    @Query("SELECT * FROM trips WHERE status = 'RECORDING' LIMIT 1") suspend fun findRecording(): TripEntity?
    @Query("SELECT * FROM trips WHERE syncState IN ('LOCAL','QUEUED')") suspend fun findUnsynced(): List<TripEntity>
    @Query("SELECT * FROM trips WHERE startTime >= :from ORDER BY startTime DESC") fun observeSince(from: Long): Flow<List<TripEntity>>
    @Query("UPDATE trips SET lastOdometerKm = :km, lastSeenAt = :at WHERE tripId = :id") suspend fun checkpoint(id: String, km: Double, at: Long)
}

@Entity(tableName = "vehicle_state") data class VehicleStateEntity(@PrimaryKey val id: Int = 0, val lastKnownOdometerKm: Double?, val lastKnownAt: Long?, val ignoredOdometerKm: Double?)
@Entity(tableName = "recent_users")  data class RecentUserEntity(@PrimaryKey val userId: String, val name: String, val dept: String?, val lastUsedAt: Long)

@Database(entities = [TripEntity::class, StopEntity::class, SyncQueueEntity::class, VehicleStateEntity::class, RecentUserEntity::class], version = 1)
@TypeConverters(EnumConverters::class)
abstract class AutoLogDb : RoomDatabase() { abstract fun tripDao(): TripDao }
```

- DAO는 전부 `suspend` / `Flow`라 스레드 관리가 필요 없다(Room이 IO 디스패처로 보낸다).
- 10초마다(Odometer 콜백 스로틀) RECORDING 세션의 체크포인트(`lastOdometerKm`, `lastSeenAt`)를 Room에 갱신한다. 앱이 죽어도 `findRecording()`으로 마지막 체크포인트를 복구한다.
- `vehicle_state` 테이블(1행): `lastKnownOdometerKm`, `lastKnownAt`, `ignoredOdometerKm` — 놓친 운행 판정 기준값. 운행 종료·앱 시작·체크포인트마다 갱신.
- 운행은 서버로 올리지 않는다. `syncState`·`SyncQueueEntity`는 로드맵(`POST /trips`)용으로 남겨두되 MVP에서는 사용하지 않는다. 서버→차 수신은 3.2 `InboxSyncWorker`.
- 상태 전이는 `Mutex`로 직렬화하고, UI는 `state: StateFlow`를 `collectAsStateWithLifecycle()`로 구독만 한다.

### 2.10 차량 앱 화면·주행 중 제약

Compose 화면 5개 + `TripSessionManager.state`를 구독하는 `HomeViewModel` 하나로 구성한다.

| 화면 | 상태 | 허용 조작 |
|---|---|---|
| ProfilePicker | 정차만 | 최근 사용자 카드 3 + [+] 검색·직원 목록. 배차된 운전자 첫 카드 강조. "나중에 선택" |
| Home | 주행 중 | 상태 표시만(거리·추천 목적). 터치 입력 없음 |
| Home | 정차 | 운행 시작/종료, 목적 확인, 놓친 운행 배너 [추가][무시], 상단 사용자명 탭 → ProfilePicker |
| ReviewTrip | 정차만 | 목적 4지선다 또는 마이크 버튼(STT), 출발지/도착지 선택 |
| History | 정차만 | 이번 달 운행 목록(읽기) |
| Settings | 정차만 | 차량·계정 연결, 동기화 상태 |

주행 판정은 `TripSessionApi.motion`(`PARKED / STOPPED / MOVING`, 판정 불가 `null`) 하나로 한다.

- 판정 우선순위: Gear(Pleos) → 기어 신호가 없으면 `PARKING_BRAKE_ON`·`IGNITION_STATE`(AAOS) → 그래도 없으면 거리 불변 60초를 주차로 본다.
- `null`이면 **주행 중으로 간주**한다(보수적).
- 화면은 이 값으로 사용 3단계(주차=전체 앱, 정차=Peek, 주행=화면 없음)와 입력 차단을 정한다.

### 2.11 TripRecordingService — 백그라운드 기록 (Foreground Service)

운행 중 운전자는 내비를 본다. `TripSessionManager`는 Activity가 아니라 이 서비스가 소유해 화면이 닫혀도 기록이 이어진다.

```kotlin
class TripRecordingService : LifecycleService() {
    @Inject lateinit var session: TripSessionManager      // 싱글턴(Hilt/수동 DI)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification(session.state.value))
        lifecycleScope.launch {
            session.state.collect { st ->
                notificationManager.notify(NOTIF_ID, buildNotification(st))
                if (st is State.Idle || st is State.PendingReview) stopSelfIfConfirmed(st)
            }
        }
        return START_STICKY
    }

    private fun buildNotification(st: State): Notification = when (st) {
        is State.Recording      -> notif("기록 중 · ${"%.1f".format(st.liveKm)} km · ${st.trip.destinationLabel ?: "목적지 미설정"}")
        is State.StopCandidate  -> notif("운행 종료?", actions = listOf("확인" to ACTION_FINALIZE, "계속" to ACTION_RESUME))
        is State.PendingReview  -> notif("운행 종료 · ${st.trip.distanceKm} km · 목적을 확인하세요", tapOpens = ReviewTripActivity::class)
        else                    -> notif("AutoLog")
    }
}
```

- `AndroidManifest.xml`: `<service android:name=".session.TripRecordingService" android:foregroundServiceType="location" />`, `FOREGROUND_SERVICE`·`FOREGROUND_SERVICE_LOCATION`·`POST_NOTIFICATIONS` 권한.
- SDK 게이트웨이(Vehicle/Navi/Location)는 서비스 생명주기에 묶는다. 서비스가 살아 있는 동안만 콜백을 구독한다.
- `onDestroy`·`ACTION_SHUTDOWN` 수신 시 마지막 체크포인트를 시도한다(보장되지 않으므로 보너스).
- **검증 항목:** 내비 앱으로 전환한 뒤에도 Odometer 콜백이 오는지, 10분 뒤에도 서비스가 살아 있는지, 절전(시동 OFF) 후 프로세스 생존 여부.

### 2.12 복구·놓친 운행 (앱 시작 시)

```kotlin
suspend fun onAppStart() {
    val now = vehicle.readTotalDistance()                       // 실패 시 복구 흐름 생략, 다음 시작에 재시도
    val recording = dao.findRecording()
    when {
        recording != null -> {
            val last = recording.lastOdometerKm ?: recording.startOdometerKm
            if (last != null && now.km - last < 0.1) {
                session.finalizeWith(now)                        // 체크포인트 = 현재 → 자동 종료 확정 (거리 정확)
            } else {
                ui.showRecoveryDialog(recording, extraKm = now.km - (last ?: now.km))   // [기록하기] [계속 기록] [삭제]
            }
        }
        else -> {
            val st = dao.vehicleState()
            val gap = now.km - (st.lastKnownOdometerKm ?: now.km)
            if (gap >= 1.0 && st.ignoredOdometerKm != now.km) ui.showMissedTripBanner(gap)   // [추가] [무시]
        }
    }
    dao.updateVehicleState(lastKnownOdometerKm = now.km, lastKnownAt = now.at)
}
```

- [추가] → `TripEntity(startOdometerKm = lastKnown, endOdometerKm = now, startTime = lastKnownAt, endTime = now.at, distanceSource = VEHICLE_ODOMETER, purposeNote = "앱 미실행 중 운행", status = PENDING_REVIEW)` → ReviewTrip.
- [무시] → `ignoredOdometerKm = now.km`. 같은 값으로는 다시 묻지 않는다.
- 시각은 추정이지만 **거리는 계기판 차이 그대로**다. 운행기록부 비고에 "앱 미실행 중 운행(시각 추정)"을 남긴다.

### 2.13 운전자 선택 (ProfilePicker)

| 항목 | 내용 |
|---|---|
| 데이터 | `recent_users`(로컬, 최근 3명) + 조직 직원 목록(앱 시작 시 `GET /users` 갱신, 캐시) + 배차(`GET /schedules?vehicle=&around=now`) |
| 기본 강조 | 배차 운전자 → 직전 운행 운전자 → 최근 순 |
| 결과 | `TripEntity.userId`, `driverSource = DRIVER_SELECTED`. "나중에 선택"이면 `userId = null` → 서버 `DriverResolver`(3.4)가 역추론 |
| 제약 | 정차 시에만. 운행 중 전환 불가 |

---

## 3. 차량 앱 — 데이터·출력 모듈 (SDK 외)

### 3.1 Room 스키마 (차량이 원본)

```kotlin
@Entity(tableName="users")      data class UserEntity(@PrimaryKey val userId: String, val name: String, val dept: String?, val email: String?, val origin: Origin)   // SERVER | LOCAL
@Entity(tableName="places")     data class PlaceEntity(@PrimaryKey val placeId: String, val label: String, val kind: PlaceKind, val lat: Double, val lng: Double, val radiusM: Int = 150, val defaultPurpose: String?, val origin: Origin, val ownerUserId: String? = null)
@Entity(tableName="schedules")  data class ScheduleEntity(@PrimaryKey val scheduleId: String, val userId: String, val startsAt: Long, val endsAt: Long, val destPlaceId: String?, val purposeCategory: String?, val purposeDetail: String?)
@Entity(tableName="recipients") data class RecipientEntity(@PrimaryKey val email: String, val name: String?, val origin: Origin)
@Entity(tableName="expenses")   data class ExpenseEntity(@PrimaryKey val expenseId: String, val tripId: String?, val stopId: Long?, val kind: ExpenseKind, val occurredAt: Long, val amountWon: Int, val liters: Double?, val unitPrice: Int?, val discountWon: Int = 0, val fuelType: String?, val merchant: String?, val cardLast4: String?, val cardAlias: String?, val paymentKind: String?, val source: ExpenseSource /* VOICE | TAP | OCR */, val receiptSha256: String?, val thumbUrl: String?, val ocrConfidenceJson: String?, val status: String)
@Entity(tableName="reports")    data class ReportEntity(@PrimaryKey val reportId: String, val yearMonth: String, val filePath: String, val createdAt: Long, val dataHash: String, val sentTo: String?, val sentAt: Long?, val sendState: String /* PENDING | SENT | FAILED */)
@Entity(tableName="inbox_seen") data class InboxSeenEntity(@PrimaryKey val inboxId: String, val seenAt: Long)
// trips / stops / vehicle_state / recent_users 는 2.9 참조
```

- 서버 배포분(`origin=SERVER`)은 inbox 수신 시 **전체 교체**, 차 안 등록분(`origin=LOCAL`)은 유지. 같은 라벨이 양쪽에 있으면 SERVER 우선.
- 운행·비용 데이터는 어떤 테이블도 서버로 나가지 않는다.

### 3.2 InboxSyncWorker (서버 → 차, 단방향)

```kotlin
class InboxSyncWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val items = runCatching { api.getInbox() }.getOrElse { return Result.retry() }
        for (it in items) {
            when (it.kind) {
                RECEIPT    -> receiptQueue.enqueue(it.payload as ReceiptPayload)      // F-19 화면이 소비
                USERS      -> dao.replaceServerUsers(it.payload as List<UserDto>)
                PLACES     -> dao.replaceServerPlaces(it.payload as List<PlaceDto>)
                SCHEDULES  -> dao.replaceSchedules(it.payload as List<ScheduleDto>)
                RECIPIENTS -> dao.replaceServerRecipients(it.payload as List<RecipientDto>)
            }
            api.ack(it.id)
        }
        return Result.success()
    }
}
```

트리거: 앱 시작 · 정차 전환 · QR 화면 열린 동안 10초 주기 · 설정 [지금 동기화] · 15분 주기(네트워크 제약). 실패는 조용히 재시도. **서버 불가가 로컬 기능을 막지 않는다.**

### 3.3 목적 추천 · 운전자 확정 (차 안)

서버 버전의 `PurposeService`·`DriverResolver`를 그대로 차 안으로 옮긴다. 입력이 전부 Room에 있기 때문이다.

```
목적 추천 (첫 매치):
  a. schedules(이 차, 시각 ±2h, dest 일치)  → 배차 목적 0.97
  b. HOME→OFFICE 평일 05–11 → 출근 0.95 · c. OFFICE→HOME 16–23 → 퇴근 0.95
  d. dest.kind=CLIENT 0.90 · e. BRANCH 0.90 · f. 같은 dest 과거 CONFIRMED ≥3 → 0.80
  g. 주말·미등록 0.30 · h. 그 외 0.50
운전자 확정 (첫 매치):
  ① ProfilePicker 선택 1.0  ② schedules(이 차 ±1h) 1명 0.95  ③ 직전 trip 2h 내 0.60(후보만)  ④ null → 다음 ProfilePicker에서 질문 → 검토 DRIVER_UNSET
```

`fun interface PurposeRule { fun evaluate(ctx: TripContext): Suggestion? }` + `List<PurposeRule>.firstNotNullOfOrNull`. 규칙당 단위 테스트 1개.

### 3.4 ReviewCalculator (검토 열)

엑셀 생성 직전·일 1회 실행. 결과는 `trips.reviewFlags`·`expenses.reviewFlags`(비트 또는 JSON 배열)에 쓰고 엑셀 "검토" 열·검토 시트로 출력한다.

| 조건 | 표시 |
|---|---|
| PENDING_REVIEW 7일 경과 | 목적 미확정 |
| `userId == null` | 운전자 미확정 |
| `distanceSource == UNAVAILABLE` | 거리 미확정 |
| schedule ±3h에 trip 없음 | 일정–운행 불일치 |
| 주유소 Place Stop에 expense 없음 | 증빙 없음 |
| `receiptSha256` 중복 | 중복 영수증 |
| 월 liters/km > 차량 평균 역수 × 2 | 연료 이상치 |

### 3.5 ReportBuilder (XLSX, Apache POI on Android)

```kotlin
// vehicle-core/build.gradle.kts
implementation("org.apache.poi:poi-ooxml:5.3.0") { exclude(group = "org.apache.xmlbeans") ... }  // 안드로이드 호환 설정은 vehicle-core/docs/poi-android.md
```

- 템플릿 `vehicle-core/src/main/assets/driving_log_template.xlsx`(국세청 고시 양식, 서식·병합 유지)를 열어 **값만** 채운다. 셀 수식 없음.
- 시트: ① 운행기록부(+검토 열) ② 비용(결제수단은 별칭·뒷4) ③ 요약 ④ 검토(해소 방법 포함).
- CONFIRMED만 업무거리 집계. 목적·운전자 미확정은 업무거리 0, 비고 표기.
- 생성 파일은 `filesDir/reports/운행기록부_{등록번호}_{YYYYMM}.xlsx`, `reports` 테이블에 `dataHash`.
- POI가 무겁거나(APK +10 MB) 느리면 `poi-ooxml-lite` 또는 템플릿 XML 직접 치환으로 대체. **1단계 검증 항목.**

### 3.6 Mailer (SMTP, 차가 직접)

```kotlin
implementation("com.sun.mail:android-mail:1.6.7"); implementation("com.sun.mail:android-activation:1.6.7")

suspend fun send(report: ReportEntity, to: List<String>) = withContext(Dispatchers.IO) {
    val s = settings.smtp()                                   // host, port(587), user, appPassword(EncryptedSharedPreferences)
    val session = Session.getInstance(Properties().apply {
        put("mail.smtp.host", s.host); put("mail.smtp.port", s.port); put("mail.smtp.auth", "true"); put("mail.smtp.starttls.enable", "true")
    }, object : Authenticator() { override fun getPasswordAuthentication() = PasswordAuthentication(s.user, s.appPassword) })
    val msg = MimeMessage(session).apply {
        setFrom(InternetAddress(s.user)); setRecipients(Message.RecipientType.TO, to.map(::InternetAddress).toTypedArray())
        subject = "[AutoLog] ${report.yearMonth} 운행기록부 — ${vehicle.plateNo}"
        setContent(MimeMultipart().apply {
            addBodyPart(MimeBodyPart().apply { setText(summaryText(report)) })
            addBodyPart(MimeBodyPart().apply { attachFile(File(report.filePath)) })
        })
    }
    Transport.send(msg)
}
```

- 화면: 기간 ◀▶ · 수신자 체크 · [보내기]. 본문·주소 타이핑 없음. 정차 시에만.
- 오프라인·실패 → `reports.sendState=PENDING`, WorkManager 재시도. 차량 LTE가 587을 막으면 HTTPS 메일 API(SendGrid `POST /v3/mail/send`)로 폴백 — 1단계 검증.
- 계정 비밀번호는 APK·로그에 절대 없음. 설정 화면에서만 입력.

### 3.7 영수증 입력 두 경로

| 경로 | 흐름 | 저장 |
|---|---|---|
| 음성·탭 (즉시) | Fuel SDK 연료량 ≥ 10%p 증가 + 주유소 Place 정차 → 알림 "주유하셨나요?" → "6만8천원 40리터" → 파싱 | `expenses(source=VOICE)` → 현재 Stop |
| 영수증 OCR (사후) | [영수증 저장] → QR → 폰 촬영 → 서버 Gemini → inbox → F-19 확인 화면 | `expenses(source=OCR, receiptSha256, thumbUrl)` |

같은 시각(±10분)에 두 건이 생기면 병합 제안(OCR 값 우선, 음성 건은 삭제).

---

## 4. 중계 서버 (Relay)

### 4.1 역할과 비역할

| 한다 | 하지 않는다 |
|---|---|
| 기기 등록(연결 코드 → 토큰) | 운행·비용·엑셀 저장 |
| 폰 카메라 페이지 제공, 영수증 수신, Gemini OCR, 결과를 차 inbox에 | 목적 추천·운전자 확정·검토 계산 |
| 운영 웹의 직원·장소·배차·수신자를 조직 차량 inbox로 배포 | 메일 발송 |
| inbox 조회·ack·만료 삭제 | 차 상태 조회(차가 올리지 않으므로) |

### 4.2 기술 스택

| 구성 | 선택 |
|---|---|
| 런타임 | Kotlin 2.0 / JVM 17, Spring Boot 3.4 (Web, Data JPA, Security, Validation, Actuator) |
| DB | PostgreSQL 16 + Flyway |
| 스토리지 | MinIO(S3 호환) — 영수증 원본·썸네일, 30일 수명주기 |
| OCR | **Gemini API**(`gemini-2.5-flash`) — Google AI Kotlin SDK 또는 REST. 키는 환경변수 |
| 인증 | 기기 토큰(차) · 회사 관리자 세션 토큰(운영 웹, 12시간) · 운영자 토큰(/ops) · QR 1회 토큰(폰, 10분) |
| 배포 | Docker Compose, nginx TLS |

`start.spring.io`에서 생성해 `relay/` 모듈로 편입(모노레포). 비동기는 `@Async` 스레드풀.

### 4.3 REST API

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | `/v1/devices` | 없음(코드) | `{code}` → `{device_token, vehicle_id, org_id}` |
| GET | `/v1/inbox` | 기기 토큰 | 내 차 앞으로 온 편지 목록 `[{id, kind, payload, created_at}]` |
| POST | `/v1/inbox/{id}/ack` | 기기 토큰 | 수신 확인 → 삭제 |
| POST | `/v1/qr-tokens` | 기기 토큰 | 영수증용 1회 토큰 발급(10분) → 차가 QR에 넣음 |
| GET | `/v1/geo/reverse?lat=&lng=` | 기기 토큰 | 운행 출발·도착 좌표 → 주소(카카오 coord2address). 좌표는 저장하지 않음. 키 없음 503·카카오 실패 502 → 차가 내비 주소로 대신 (F-06·F-07) |
| GET | `/r/{vehicleCode}?t=` | QR 토큰 | 카메라 페이지(정적 HTML 1장) |
| POST | `/r/{vehicleCode}/receipt?t=` | QR 토큰 | multipart 사진 → 저장 → Gemini → inbox(RECEIPT) |
| POST | `/admin/auth/signup` | — | 회사 가입 `{orgName, email, password}` → 회사 + 첫 관리자 생성 후 로그인 응답. 승인·메일 인증 없음(바로 사용), 이름·이메일 중복 409, 서버 전체 시간당 30건 상한 |
| POST | `/admin/auth/login` | — | `{email, password}` → `{token, expiresAt, email, orgName}`. 5번 틀리면 10분 잠금(429) |
| GET/POST | `/admin/auth/me`, `/admin/auth/logout`, `/admin/auth/password` | 세션 | 내 정보, 로그아웃, 비밀번호 변경(다른 세션 끊김) |
| GET/POST/PATCH/DELETE | `/admin/vehicles`, `/admin/vehicles/{id}/connect-code`, `/admin/vehicles/{id}/device` | 세션 | 차량, 연결 코드 발급, 토큰 회수 |
| GET/POST/PATCH/DELETE | `/admin/users`, `/admin/places`, `/admin/schedules`, `/admin/recipients` (+ `/import` CSV) | 세션 | 변경 시 조직 차량 inbox에 전체 목록 적재(배차는 해당 차량만) |
| GET/POST | `/ops/orgs`, `/ops/orgs/{id}/admins`, `/ops/admins/{id}/password` | 운영자 토큰 | 회사·회사 관리자 만들기, 비밀번호 재설정. nginx 밖으로 안 나감(`scripts/ops.sh`) |

관리자 인증(2026-11-13): 서버 하나에 회사가 여럿이다. 회사가 운영 웹에서 직접 가입하면(가입한 사람 = 첫 관리자) 바로 쓸 수 있고, 운영자 /ops는 비밀번호 재설정 같은 예외용이다. 관리자는 이메일·비밀번호로 로그인해 받은 세션 토큰을 `X-Admin-Token` 헤더로 보낸다. 세션은 sha256 해시로만 저장(`admin_sessions`), 비밀번호는 PBKDF2-HMAC-SHA256. 모든 /admin API는 세션의 `org_id`로만 조회·변경한다. JWT 대신 서버 저장 세션을 쓴 이유: 로그아웃·비밀번호 변경 때 바로 끊을 수 있고 서버가 하나라 상태 저장이 부담이 아니다.

**inbox payload (`shared` 모듈)**

```kotlin
@Serializable sealed interface InboxPayload
@Serializable data class ReceiptPayload(val receiptId: String, val sha256: String, val thumbUrl: String, val takenAt: String?, val fields: ReceiptFields, val confidence: Map<String, Double>, val arithOk: Boolean?, val ocrStatus: String) : InboxPayload
@Serializable data class ReceiptFields(val merchant: String?, val datetime: String?, val fuelType: String?, val liters: Double?, val unitPrice: Int?, val discountWon: Int?, val amountWon: Int?, val cardLast4: String?)
@Serializable data class UsersPayload(val users: List<UserDto>) : InboxPayload
@Serializable data class PlacesPayload(val places: List<PlaceDto>) : InboxPayload
@Serializable data class SchedulesPayload(val schedules: List<ScheduleDto>) : InboxPayload
@Serializable data class RecipientsPayload(val recipients: List<RecipientDto>) : InboxPayload
```

### 4.4 스키마

```sql
CREATE TABLE organizations (org_id UUID PRIMARY KEY, name TEXT NOT NULL, created_at TIMESTAMPTZ DEFAULT now());
CREATE TABLE admins (admin_id UUID PRIMARY KEY, org_id UUID REFERENCES organizations, email TEXT UNIQUE, password_hash TEXT);
CREATE TABLE vehicles (vehicle_id UUID PRIMARY KEY, org_id UUID, plate_no TEXT, model TEXT, vehicle_code TEXT UNIQUE,   -- QR용 공개 코드
  device_token_hash TEXT, registered_at TIMESTAMPTZ, last_inbox_at TIMESTAMPTZ);
CREATE TABLE connect_codes (code CHAR(6) PRIMARY KEY, vehicle_id UUID, expires_at TIMESTAMPTZ, used_at TIMESTAMPTZ);
CREATE TABLE qr_tokens (token TEXT PRIMARY KEY, vehicle_id UUID, expires_at TIMESTAMPTZ, used_at TIMESTAMPTZ);
CREATE TABLE users (user_id UUID PRIMARY KEY, org_id UUID, name TEXT, dept TEXT, email TEXT);
CREATE TABLE places (place_id UUID PRIMARY KEY, org_id UUID, label TEXT, kind TEXT, lat DOUBLE PRECISION, lng DOUBLE PRECISION, radius_m INT DEFAULT 150, default_purpose TEXT);
CREATE TABLE schedules (schedule_id UUID PRIMARY KEY, org_id UUID, vehicle_id UUID, user_id UUID, starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, dest_place_id UUID, purpose_category TEXT, purpose_detail TEXT);
CREATE TABLE recipients (id BIGSERIAL PRIMARY KEY, org_id UUID, email TEXT, name TEXT);
CREATE TABLE receipts (receipt_id UUID PRIMARY KEY, vehicle_id UUID, sha256 CHAR(64), storage_key TEXT, thumb_key TEXT, taken_at TIMESTAMPTZ,
  ocr_status TEXT, ocr_fields JSONB, ocr_confidence JSONB, arith_ok BOOLEAN, created_at TIMESTAMPTZ DEFAULT now(), expires_at TIMESTAMPTZ);
CREATE UNIQUE INDEX ON receipts (vehicle_id, sha256);
CREATE TABLE inbox (id BIGSERIAL PRIMARY KEY, vehicle_id UUID, kind TEXT, payload JSONB, created_at TIMESTAMPTZ DEFAULT now(), expires_at TIMESTAMPTZ);
CREATE INDEX ON inbox (vehicle_id, created_at);
```

운행·비용 테이블은 **없다.** `receipts`는 OCR 결과의 서버 측 사본이며 30일 후 삭제.

### 4.5 OCR 파이프라인 (Gemini)

```
POST /r/{code}/receipt
  1. QR 토큰 검증(1회·10분) → vehicle_id
  2. sha256 → 같은 차 중복이면 409
  3. 원본 MinIO 저장, 1600px 썸네일 생성
  4. Gemini 호출: 이미지 + 프롬프트 → JSON(fields, confidence)
     프롬프트: "주유 영수증. 상호, 거래일시(ISO 8601 +09:00), 유종, 수량(L), 단가, 할인, 결제금액, 카드 끝 4자리를 JSON으로. 없으면 null. 각 필드 확신도 0~1. 카드번호는 끝 4자리 외 출력 금지"
  5. 검산 |amount − liters×unit + discount| ≤ max(100, amount×0.5%) → arith_ok
  6. 카드번호 13~19자리 패턴이 남아 있으면 제거(이중 방어). 썸네일은 EXIF 회전만 반영하고 가리지 않는다(국내 카드 영수증은 번호 일부가 가려져 인쇄됨)
  7. inbox(vehicle_id, RECEIPT, ReceiptPayload) 적재
  실패: Gemini 3회 재시도 → ocrStatus=FAILED, 썸네일만 inbox → 차에서 수동 입력
```

### 4.6 보안

| 항목 | 조치 |
|---|---|
| 기기 토큰 | 해시 저장, 운영 웹에서 회수. 요청마다 vehicle_id 스코프 |
| QR 토큰 | 1회·10분. 카메라 페이지는 로그인 없음 |
| 관리자 | 회사별 계정(이메일·비밀번호, PBKDF2) + 서버 저장 세션(해시, 12시간), org 스코프. 운영자 API는 별도 토큰·내부망만 |
| 영수증 | 30일 후 자동 삭제. 카드 뒷 4자리만 |
| Gemini 키 | 서버 환경변수. APK·웹에 없음 |
| 운행 데이터 | 서버에 없음 — 유출 면적 최소 |

---

## 5. 서버 사양 · 운영 웹

### 5.1 시연·개발 환경

| 항목 | 사양 |
|---|---|
| 인스턴스 | 2 vCPU / 4 GB / 40 GB (NCP g2 또는 AWS t3.medium) |
| 컨테이너 | relay(Spring Boot) · postgres:16 · minio · nginx(TLS, 운영 웹·카메라 페이지 정적) |
| 외부 | Gemini API(무료 구간), 회사 SMTP 또는 SendGrid(메일은 차가 보내므로 서버 무관) |

```yaml
services:
  relay:    { build: ./relay, environment: [SPRING_PROFILES_ACTIVE=demo, GEMINI_API_KEY=${GEMINI_API_KEY}], depends_on: [postgres, minio] }
  postgres: { image: postgres:16, volumes: [pgdata:/var/lib/postgresql/data] }
  minio:    { image: minio/minio, command: server /data --console-address ":9001", volumes: [minio:/data] }
  nginx:    { image: nginx:1.27, ports: ["80:80","443:443"], volumes: [./nginx:/etc/nginx/conf.d, ./web/dist:/usr/share/nginx/html] }
```

### 5.2 운영 환경(1,000대 가정)

영수증 일 500건 × Gemini 3초 → 단일 인스턴스로 충분. inbox 조회는 차량당 하루 수십 회. **2 vCPU / 4 GB 1대 + 관리형 PostgreSQL 소형**이면 된다. 운행 데이터가 없어 저장 증가분은 영수증 썸네일뿐(월 4 GB, 30일 롤링).

### 5.3 운영 웹(경량) · 카메라 페이지

| 항목 | 값 |
|---|---|
| 프레임워크 | React 18 + TS + Vite, Tailwind. `web/admin`(운영 웹), `web/camera`(정적 HTML 1장) |
| 운영 웹 5화면 | 차량(연결 코드·토큰 회수·마지막 inbox 시각) · 직원 · 장소(주소→좌표 지오코딩) · 배차(주간 캘린더) · 수신자 |
| 카메라 페이지 | `<input type="file" accept="image/*" capture="environment">` → `POST /r/{code}/receipt` → "차량으로 전송됨". 로그인 없음, QR 토큰만 |
| 없음 | 대시보드·운행 목록·비용 화면 (운행 데이터가 서버에 없음) |

---

## 6. 개발·검증 순서 (SDK 검증 우선)

| 순서 | 작업 | 산출물 | 통과 기준 |
|---|---|---|---|
| 1 | Pleos Playground 가입, 프로젝트 생성, 에뮬레이터 설치 | 빌드되는 빈 앱 | Vehicle Service 연결 성공 로그 |
| 2 | Odometer capability·get·listener | 거리 로그 | 에뮬레이터 값 변경 시 콜백 수신, 단위·소수점 확인 |
| 3 | NaviHelper destination·routeState·location | 목적지 로그 | 에뮬레이터 내비 목적지 설정 시 콜백 수신 |
| 4 | Fused Location 콜백 | 좌표 로그 | 에뮬레이터 GPS 경로 재생 시 콜백 수신, 응답 필드(accuracy 유무) 기록 |
| 4a | **백그라운드 콜백** | 로그 | Foreground Service 시작 → 내비 앱으로 전환 → Odometer 변경 → 서비스가 콜백 수신. 10분 유지 |
| 4b | **절전 생존** | 로그 | 에뮬레이터 전원 OFF→ON 후 프로세스·Room 체크포인트 상태 |
| 5 | CarInfo·Gear 제공 범위 조사 | 지원 표 | VIN 제공 여부, Gear P/D 전환 콜백 수신 여부 기록 |
| 6 | TripSessionManager + Room | 로컬 운행 기록 | 앱 강제 종료 후 복구 |
| 7 | 차 안 POI XLSX + SMTP 발송 | 엑셀·메일 | 에뮬레이터에서 생성 < 5초, SMTP 587 전송 성공(막히면 HTTPS API) |
| 8 | SpeechToText 목적 입력 | ReviewTrip 음성 입력 | 에뮬레이터 마이크 입력으로 인식 텍스트 수신 |
| 9 | 중계 서버: 기기 등록·QR·Gemini OCR·inbox | 영수증 100장 측정 | 금액 95%, 주유량 90%. QR→차 카드 15초 |
| 10 | 운영 웹 5화면 → inbox 배포 → 차 반영 | 시연 가능 | 직원 추가 1분 내 ProfilePicker 반영 |

1~4단계는 11.05까지, 8단계는 11.15 전에 끝낸다. 결과가 "지원 안 됨"이면 다음 대체를 적용한다.

| 미지원·미달 | 대체 |
|---|---|
| Odometer 소수점 미제공 | 정수 km 기록, 오차 ±1 km 명시 |
| Fused Location 콜백 없음 | NaviHelper `getCurrentLocationInfo()` → 직전 도착지 → 사용자 선택 순으로 폴백 |
| Fused Location accuracy 필드 없음 | 반경 매칭에 고정 여유 50 m 적용 |
| Gear 콜백 미수신 | AAOS `PARKING_BRAKE_ON`·`IGNITION_STATE` → 그래도 없으면 거리 불변 60초 = 주차, 정차 = Odometer 불변 10초 |
| STT 인식률 저조(에뮬레이터) | 탭 입력만 시연, 음성은 영상으로 대체 |
| 백그라운드 콜백 없음(4a) | 지속 알림은 정적 표시, 앱 복귀 시 Odometer 재조회로 거리 복구(2.12) |
| 절전 후 프로세스 종료(4b) | 체크포인트 기반 복구(2.12). 이미 설계된 경로 |
| 부팅·앱 미실행 자동 시작 불가 | 완전 자동 시작은 P2 제외, 놓친 운행 복구로 대체 |

---

## 7. 저장소 구조 (모노레포)

차량 쪽은 **로직(`vehicle-core/`)과 화면(`vehicle-app/`)을 별도 Gradle 모듈**로 나눈다(2026-11-11 AutoLog-Frontend와 한 리포로 합치며 예전 `app/`을 `vehicle-app/`이 대체). 로직 담당(앱 A·B)과 프론트가 서로의 폴더를 건드리지 않게 하고, 로직을 화면·에뮬레이터 없이 단위 테스트하기 위해서다. 패키지 이름은 두 모듈 모두 `kr.autolog.vehicle.*`이다.

```
AutoLog-app/                      # 2026-11-11 AutoLog-Backend + AutoLog-Frontend 통합
├── settings.gradle.kts           include(":shared", ":vehicle-core", ":relay", ":vehicle-app")
├── build.gradle.kts · gradle/libs.versions.toml · gradle.properties
├── README.md · CONVENTIONS.md(분업·브랜치·커밋 규칙) · CLAUDE.md · .editorconfig
├── .github/workflows/            # CI: ktlintCheck · 모듈별 test · 화면 스크린샷 기록 · 빌드
│
├── shared/                       # inbox payload DTO (Kotlin JVM, kotlinx.serialization만)   [B, A 리뷰]
│   └── src/{main,test}/kotlin/kr/autolog/shared/{InboxPayload.kt, Json.kt}
│
├── vehicle-core/                 # 차량 로직 — Android library, 화면 없음
│   ├── build.gradle.kts          namespace kr.autolog.vehicle.core
│   ├── src/main/kotlin/kr/autolog/vehicle/
│   │   ├── core/                 # Contracts.kt(앱 A·B·프론트 경계, 8.2), SdkCall.kt        [A·B 공동]
│   │   ├── fake/                 # Contracts.kt의 Fake 구현 (프론트·상대방 테스트용)       [A·B 공동]
│   │   ├── vehicle/              # VehicleGateway                                           [A]
│   │   ├── session/              # TripSessionManager, TripRecordingService·알림, Recovery, DrivingJudge [A]
│   │   ├── data/                 # Room 엔티티·DAO·AutoLogDb, InboxSyncWorker, RelayApi      [A]
│   │   ├── report/               # ReportBuilder(XLSX), ReviewCalculator                    [A]
│   │   ├── navi/ · location/     # NaviGateway, LocationGateway                             [B]
│   │   ├── speech/               # STT·TTS, PurposeVoiceParser, FuelVoiceParser             [B]
│   │   ├── domain/               # PurposeRules, DriverResolver, UserOrdering, PlaceSuggester, ReceiptMatcher [B]
│   │   └── mail/                 # Mailer(SMTP), SmtpCredentialStore                        [B]
│   ├── src/main/assets/          # driving_log_template.xlsx
│   ├── src/test/kotlin/...       # 단위 테스트 (화면·에뮬레이터 없이)
│   └── docs/sdk-findings.md      # 1단계 SDK 검증 결과
│
├── vehicle-app/                  # 차량 화면 — Compose (application). vehicle-core 의 core·fake 만 import   [프론트]
│   └── src/{main,demo,vehicle,test}/  # 구조는 프론트 스펙 5.1·6.2 (예전 app/ 뼈대를 대체)
│
├── relay/                        # 중계 서버 — Kotlin, Spring Boot                          [B]
│   ├── src/main/kotlin/kr/autolog/relay/{device, inbox, receipt(Gemini), admin, common}/
│   └── src/main/resources/{application.yml, db/migration/V1__init.sql}
│
├── web/                          # [프론트, API 계약은 B]
│   ├── admin/                    # 운영 웹 (React + TS + Vite)
│   └── camera/index.html         # 폰 영수증 카메라 페이지
├── ops/                          # docker-compose.yml, nginx/, .env.example
└── docs/                         # 기획서·기능명세·기술명세·DB 스키마 + 화면 설계(design-language, superpowers/, integration/)
```

**의존 방향**

```
vehicle-app (화면) ──► vehicle-core (로직) ──► shared ◄── relay (서버)
```

- `vehicle-app`은 `vehicle-core`의 `core`·`fake` 패키지만 import한다. `session`·`data` 같은 내부 패키지를 직접 쓰지 않는다.
- 앱 A·B도 상대방 패키지를 직접 import하지 않고 `core/Contracts.kt`를 거친다.
- `shared`에는 kotlinx-serialization 외 의존성을 넣지 않는다(APK에 들어간다). `vehicle-app`·`vehicle-core`는 `relay`에 의존하지 않는다.

| 모듈 | 무엇 | 실행 위치 | 차량 APK 포함 |
|---|---|---|---|
| `shared` | inbox payload DTO | — | ✅ (vehicle-core 경유) |
| `vehicle-core` | SDK·운행 기록·Room·inbox·엑셀·메일 | Pleos 에뮬레이터·헤드유닛 | ✅ (vehicle-app 경유) |
| `vehicle-app` | 차량 화면 | Pleos 에뮬레이터·헤드유닛 | ✅ |
| `relay` | 중계 서버 | 서버 VM | ❌ |
| `web/admin`, `web/camera` | 운영 웹, 카메라 페이지 | nginx 정적 | ❌ |

```bash
./gradlew :vehicle-core:test      # 차량 로직 단위 테스트 (화면·에뮬레이터 없이)
./gradlew :vehicle-app:assembleVehicleDebug   # 차량 APK (vehicle-core·shared 포함, 실구현) — 데모는 assembleDemoDebug
./gradlew :relay:bootJar          # 서버 jar
```

---

## 8. 분업 — 로직 2인(앱 A·B) + 프론트

**모든 화면은 프론트 담당이 따로 만든다**(차량 앱 Compose, 운영 웹, 카메라 페이지). 앱 A·B는 화면 없는 로직(`vehicle-core/` 모듈)과 서버(`relay/`)를 맡고, 화면이 부를 인터페이스 `vehicle-core/.../core/Contracts.kt`까지만 책임진다. 화면 코드는 `vehicle-app/`·`web/`에 둔다(7장).

로직은 SDK 기준으로 나눈다. 차량 데이터(Vehicle SDK)와 맥락(NaviHelper·Fused Location·Gleo AI)은 서로 의존이 없다.

### 8.1 분담

| | **앱 A — 차량 데이터·기록·출력 코어** | **앱 B — 맥락·판단·중계** |
|---|---|---|
| 한 줄 | 계기판을 읽어 운행을 만들고, 잃지 않고, 엑셀로 내보낸다 | 운행에 "어디로·왜·누가"를 붙이고, 폰·관리자 입력을 차로 잇고, 메일로 보낸다 |
| 담당 SDK | **Vehicle SDK** — Odometer, Gear, CarInfo, Fuel | **NaviHelper, Fused Location, Gleo AI(STT·TTS)** |
| 기능명세 | F-01–05, 10–14, 16, 21, 23 | F-06–09, 15, 17–20, 22, 24, 30, 40–43, 50–54(API) |
| `vehicle-core/` 패키지 | `vehicle/`(VehicleGateway) · `session/`(TripSessionManager, TripRecordingService·알림, Recovery, DrivingJudge) · `data/`(Room 엔티티·DAO·AutoLogDb, InboxSyncWorker, RelayApi) · `report/`(ReportBuilder·POI, ReviewCalculator) | `navi/` · `location/` · `speech/`(STT·TTS, PurposeVoiceParser, FuelVoiceParser) · `domain/`(PurposeRules, DriverResolver, UserOrdering, PlaceSuggester, ReceiptMatcher) · `mail/`(Mailer·SMTP, SmtpCredentialStore) |
| 공동 | `core/`(Contracts.kt, SdkCall.kt) · `fake/` — A·B 공동 | 〃 |
| 서버 | — | `relay/` 전체 + `shared`(inbox payload) 소유 |
| 화면 | 없음 | 없음 |
| 리스크 | Odometer 정밀도·백그라운드 콜백·절전 생존·POI 빌드 → **검증 1순위** | SDK 3종 + 서버로 범위가 넓음 → STT 버퍼, admin API 2차, SMTP 587 차단 시 HTTPS 메일 API |

| 프론트 | `app/`(MainActivity, `ui/` — ProfilePicker, Home 3상태, ReviewTrip, Receipts, Report·보내기, History, Settings, 놓친 운행 배너) + ViewModel · `web/admin`(운영 웹 5화면) · `web/camera` |
|---|---|

### 8.2 `vehicle-core/.../core/Contracts.kt` — 세 사람의 경계

화면(프론트)은 아래 인터페이스만 부른다. A·B는 서로의 인터페이스도 이 파일로만 쓴다.

```kotlin
// ===== 앱 A 제공 =====
interface TripSessionApi {                       // 프론트: Home·ReviewTrip / 앱 B: 추천 결과 주입
    val state: StateFlow<State>                  // Idle / Recording(trip, liveKm) / StopCandidate / PendingReview(trip) / NeedsRecovery(trip, reason)
    val isDriving: StateFlow<Boolean>            // 프론트가 모든 입력 enabled에 묶음 (F-13)
    val missedTrip: StateFlow<MissedTrip?>       // 놓친 운행 배너 (F-10)
    suspend fun startSession(userId: String?)
    suspend fun finalize()
    suspend fun confirmPurpose(tripId: String, category: String, detail: String?, note: String?, inputMode: InputMode)
    suspend fun assignDriver(tripId: String, userId: String, source: DriverSource)
    suspend fun acceptMissedTrip(); suspend fun ignoreMissedTrip()
}
interface LocalStore {                           // Room 창구. 프론트: 이력·설정 / 앱 B: 추천·매칭 입력
    fun trips(month: YearMonth): Flow<List<TripEntity>>
    fun users(): Flow<List<UserEntity>>; fun places(): Flow<List<PlaceEntity>>
    fun schedulesAround(vehicleId: String, at: Long): Flow<List<ScheduleEntity>>
    fun recipients(): Flow<List<RecipientEntity>>
    val receiptInbox: Flow<List<ReceiptPayload>> // InboxSyncWorker가 채움
    suspend fun saveExpense(e: ExpenseEntity); suspend fun savePlace(p: PlaceEntity); suspend fun ackReceipt(id: String)
}
interface ReportApi { suspend fun build(month: YearMonth): ReportEntity }   // POI + ReviewCalculator
interface DeviceApi { val connection: StateFlow<ConnectionState>; suspend fun register(code: String) }   // F-01

// ===== 앱 B 제공 =====
interface ContextApi {                           // 앱 A: Trip에 목적지·좌표 저장 / 프론트: 메인 표시
    val destination: StateFlow<DestinationInfo?>
    val latestFix: StateFlow<LocationFix?>
    fun recentDestinations(): Flow<List<DestinationInfo>>
}
interface PurposeEngine {                        // 앱 A가 FINALIZING에서 호출, 결과를 PendingReview에 담음
    fun suggest(trip: TripEntity): Suggestion
    fun driverCandidate(trip: TripEntity): DriverCandidate?
    fun orderedUsers(vehicleId: String, now: Long): Flow<List<UserCard>>     // 프론트: ProfilePicker
    fun shouldProposePlace(trip: TripEntity, category: String): Boolean      // F-17
}
interface VoiceApi {                             // 프론트: 마이크 버튼
    suspend fun listenPurpose(): VoicePurpose    // 텍스트 + 매칭된 목적
    suspend fun listenFuel(): VoiceFuel          // 금액·리터
}
interface ReceiptApi {                           // 프론트: 영수증 확인 화면
    suspend fun issueQr(): QrInfo                // /qr-tokens → URL
    fun candidates(r: ReceiptPayload): List<TripCandidate>   // ReceiptMatcher
    suspend fun confirm(r: ReceiptPayload, tripId: String?, edits: ReceiptFields)   // → LocalStore.saveExpense
}
interface MailApi {                              // 프론트: 보내기·설정 화면
    suspend fun send(report: ReportEntity, to: List<String>)
    suspend fun saveSmtp(c: SmtpConfig); fun smtpConfigured(): Boolean
}

// ===== 앱 B(서버) 제공, 앱 A(InboxSyncWorker) 소비 — shared 모듈 =====
// POST /v1/devices · GET /v1/inbox · POST /v1/inbox/{id}/ack · POST /v1/qr-tokens · GET /v1/geo/reverse   (4.3절)
```

- **첫날 A·B가 이 파일과 `fake/` 패키지의 `Fake*` 구현을 커밋**한다. 프론트는 `FakeTripSession`(23.4 km를 뿜음)·`FakeReceiptApi`로 화면 전부를 만들 수 있다. A는 `FakeContext`·`FakeRelayApi`(로컬 JSON)로, B는 `FakeLocalStore`로 각자 테스트한다.
- A·B의 완료 기준은 화면이 아니라 **단위 테스트 + 에뮬레이터 logcat**이다.
- 시그니처 변경은 PR로만, 관련 두 사람 리뷰. 프론트에 영향 있으면 프론트도 리뷰.

### 8.3 일정

| 기간 | 앱 A | 앱 B | 프론트 / 함께 |
|---|---|---|---|
| **11.08–09** | Odometer 콜백 → 내비 전환 후 콜백 → Gear P/D → 절전 생존 → POI 빌드 | NaviHelper 목적지 → Fused Location → STT 아티팩트 → SMTP 587 → `relay/` 골격 | **함께:** 모노레포·`Contracts.kt`·`fake/`·`shared`. 결과 `vehicle-core/docs/sdk-findings.md` · 프론트: fake로 화면 착수 |
| 11.10–12 | VehicleGateway, TripSessionManager, Room·10초 체크포인트, DrivingJudge, DeviceApi | 서버 `/devices`·`/qr-tokens`·`/r/{code}/receipt`(Gemini)·`/inbox` · Navi·LocationGateway · PurposeRules + 단위 테스트 | 프론트: ProfilePicker·Home·ReviewTrip(fake) |
| **11.13–15** | TripRecordingService·알림, Recovery, InboxSyncWorker, ReportBuilder(시트 1) | ReceiptMatcher·ReceiptApi, Mailer·SMTP 저장, UserOrdering | 프론트: 영수증 확인·보내기·설정·카메라 페이지·운영 웹 차량 화면 · **11.14 셋 통합 → 영상·APK, 11.15 제출** |
| 11.16–23 | 놓친 운행, P 즉시 저장, ReportBuilder 4시트, ReviewCalculator | admin API(직원·장소·배차·수신자) + 배포, STT 목적·주유 파싱, DriverResolver, PlaceSuggester | 프론트: 운영 웹 4화면, 이력, 놓친 운행 배너 |
| 11.24–30 | 강제 종료·절전·오프라인·서버 OFF 테스트, 거리 오차, 엑셀 양식 대조 | 영수증 100장 OCR 측정, 시연 VM 배포, 메일 도착 테스트 | 시연 리허설·APK 안정화 |

### 8.4 균형 규칙

- 앱 B가 SDK 3종 + 서버로 가장 넓다. **admin API는 11.15 전 차량·연결 코드만**, 나머지는 2차. 급하면 직원·장소·배차는 CSV 업로드 하나로.
- **STT는 B의 버퍼** — 11.20까지 안 되면 버리고 탭만.
- 앱 B가 11.13까지 서버를 못 띄우면 11.15 시연은 `FakeRelayApi`(로컬 JSON 주입)로 대체하고 영상에 명시.
- 앱 A가 밀리면 ReviewCalculator를 2차로(엑셀은 검토 열 공란으로도 성립).
- 프론트가 밀리면 A·B는 합치기 전까지 fake 화면 없이 logcat 데모로 SDK 검증을 영상에 넣는다.

---

### 심사 Q&A 대비 — 기술 질문 예상과 답

| 질문 | 답 |
|---|---|
| 실차에서 검증했나? | 에뮬레이터에서 Odometer·NaviHelper 콜백 수신까지 검증했고, 실차 검증 여부는 화면에 명시한다. 거리 정밀도·갱신 주기는 측정값으로 제시한다. |
| 백그라운드에서 돌아가나? | 지원 여부를 검증 항목으로 두고, 미지원 시 포그라운드 자동 + 수동 시작/종료로 동작한다. 핵심 가치(계기판 거리 기반 기록)는 유지된다. |
| 왜 GPS 대신 Odometer인가? | 세무 증빙은 계기판 기준이다. GPS는 음영·샘플링 오차가 있고, 폰이 없으면 기록이 끊긴다. 차량 OS 앱만이 계기판을 직접 읽는다. |
| OCR 틀리면? | 신뢰도 정책으로 자동 등록을 제한하고, 원본과 추출값을 나란히 보여준다. 검산 실패는 장부에 들어가지 않는다. |
| Pleos Fleet API와의 관계는? | MVP는 차량 내 SDK만 쓴다. Fleet API는 계약 후 다차량 원격 수집으로 확장하며, `distance_source`가 출처 분리를 지원한다. |
| 개인정보는? | Fused Location 콜백은 계속 받지만 저장은 출발·도착 2점뿐이다. 경로 polyline을 만들지 않는다. 조직 스코프 강제, 열람 감사 로그, 영수증 카드번호 마스킹. |
| 공용차에서 운전자는 어떻게 아나? | 앱 진입 시 넷플릭스식 사용자 선택(폰·비콘 불필요) + 배차 예약·배정으로 서버가 역추론. 확신이 없으면 null로 두고 운전자가 폰에서 가져가거나 관리자가 배정한다. 운전자 미확정 운행은 업무거리 0으로 집계한다. |
| 시동 끄면 기록이 사라지지 않나? | 10초 체크포인트 + 기어 P 즉시 저장. 시동 OFF 콜백은 못 받는다고 가정하고, 다음 시동 때 계기판 값으로 종료를 확정한다. 앱을 안 열고 운행해도 계기판 차이로 거리를 복구한다(폰 GPS는 불가). |
| 관리자 화면이 없는데 관리는? | 차가 월말에 운행기록부를 만들어 회계 담당자에게 메일로 보낸다. "검토" 열에 확인 필요 항목이 표시되고 운전자가 차 안에서 해소한다. 운영 웹은 차량·직원·장소·배차·수신자 등록만 한다. |
| 운행 데이터는 서버에 있나? | 없다. 원본은 차량 Room이고 서버는 영수증 OCR과 기준 정보 배포만 중계한다. 다차량 합산이 필요해지면 `POST /trips` 하나를 추가해 사본을 올린다(로드맵). |
| 왜 OCR만 서버인가? | 폰 사진이 차로 갈 경로가 서버뿐이다. Gemini 키도 APK 대신 서버에 두는 게 안전하다. |
| SDK를 몇 개 쓰나? | Vehicle(Odometer·Gear·Fuel·CarInfo), NaviHelper, Fused Location, Gleo AI(STT, 선택 TTS). 핵심 흐름은 Odometer + NaviHelper만으로 완성되고, 나머지는 품질을 올리되 빠져도 앱이 멈추지 않는다. |
