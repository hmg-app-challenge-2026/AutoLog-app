# 개발 환경

## 맥 (Apple Silicon) — 빌드·에뮬레이터·시연

1. Android Studio 최신, **JDK 21** (`brew install openjdk@21`)
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
   ```
2. `git clone https://github.com/hmg-app-challenge-2026/H-TaxLog` 후 루트에서 `echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties`
3. Pleos 에뮬레이터: 스펙 5.5 "맥 에뮬레이터 준비" 1~5 (SDK Update Site `crp-arm64-sys-img.xml`, API 34, **Cold boot**, RAM 4 GB)
4. 서버(relay·DB·MinIO·웹): `cp ops/.env.example ops/.env`로 값을 채운 뒤
   ```bash
   ./gradlew :relay:bootJar
   cd ops && docker compose -p htaxlog up -d --build relay
   ```
   관리자 웹을 고쳤다면 `cd web/admin && npm run build` 후 다시 띄운다.
5. 차 앱 설치: `./gradlew :vehicle-app:installVehicleDebug` (화면만 볼 때는 `installDemoDebug`)
6. 권한·시연 도우미: `scripts/emu.sh setup`(권한 허용) · `scripts/emu.sh demo-commute`(출근 시연)
7. 테스트:
   ```bash
   ./gradlew ktlintCheck
   ./gradlew :shared:test :relay:test :vehicle-core:testDebugUnitTest :vehicle-app:testDemoDebugUnitTest
   ```

## 리눅스·WSL (빌드·테스트·스크린샷만)

- 같은 JDK 21·Android SDK로 빌드·테스트할 수 있다. Pleos 에뮬레이터는 돌지 않는다(외장 GPU 필요).
- 화면 확인은 Roborazzi 스크린샷: `./gradlew :vehicle-app:recordRoborazziDemoDebug` → `vehicle-app/src/test/screenshots`(커밋하지 않음).
- WSL은 메모리가 모자라면 세션이 죽으므로 Gradle에 상한을 건다: `systemd-run --user --scope -q -p MemoryMax=10G ./gradlew …`
