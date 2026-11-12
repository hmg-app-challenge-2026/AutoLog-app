# 개발 환경

## WSL (빌드·테스트·스크린샷)

1. JDK 17·Android SDK 설치: `docs/superpowers/plans/2026-11-09-vehicle-app-foundation.md` Task 1 Step 1~2
   - 셸 설정 파일(`~/.bashrc`)을 고치지 않고 쓰려면 매번 아래 환경변수를 지정한다:
     ```bash
     export JAVA_HOME=$HOME/opt/jdk17 ANDROID_HOME=$HOME/Android/Sdk
     export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
     ```
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
