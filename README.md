# H-TaxLog

> **차가 스스로 쓰는 운행·지출 장부.** 차량의 계기판 주행거리와 내비게이션 정보를 통해 운행 기록을 자동화하고, 업무 일정 연동, 영수증 스캔을 통한 간편한 주유 기록 입력 등 편의 기능으로 운전자의 기록 부담과 관리자의 정산 업무를 줄이는 기업용 차량 운행 관리 앱입니다.

2026 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

## APK 위치

- [AutoLog_H-TaxLog_release.apk](apk/AutoLog_H-TaxLog_release.apk)
- [APK 설치 및 서버 설정 상세 안내](apk/README.md)

## 실행 방법

1. 아래 테스트 환경에 맞춰 **Pleos Connect v2.1.0 에뮬레이터를 Cold Boot**로 시작합니다. 일반 휴대전화나 기본 Android Automotive 에뮬레이터에는 앱이 사용하는 Pleos 차량 서비스가 없습니다.
2. 저장소 루트에서 제출 APK를 설치하고 앱을 실행합니다. `adb`가 PATH에 있어야 합니다.

   ```bash
   adb install apk/AutoLog_H-TaxLog_release.apk
   scripts/emu.sh setup
   ```

3. 첫 실행에서 차량 데이터·위치·알림 권한을 허용합니다. 내비 등 다른 앱 위에 기록 중 알약을 표시하려면 Android의 **다른 앱 위에 표시** 특수 권한도 허용합니다.
4. 운행을 시험하려면 `scripts/emu.sh demo-commute`를 실행하거나 Pleos Virtual Console에서 기어·속도·계기판 거리를 조작합니다.

같은 패키지의 디버그 서명 앱이 이미 설치돼 있으면 서명 키가 달라 APK 업데이트가 거부됩니다. 기존 앱을 제거한 뒤 설치할 수 있지만, 제거하면 그 앱의 로컬 기록도 삭제됩니다.

## 테스트 환경 및 화면 규격

| 항목 | 개발·검증 환경 |
|---|---|
| 실행 기기 | Apple Silicon Mac의 **Pleos Connect v2.1.0** 에뮬레이터 |
| 시스템 이미지 | Android API 34, Pleos Connect v2.1.0 APIs |
| 에뮬레이터 설정 | Cold Boot, RAM 4 GB, 기본 언어 한국어 |
| 기본 디스플레이 | **2560 × 1440 px**, 160 dpi (밀도 1.0) |
| 앱 표시 영역 | 시스템 상·하단 영역을 제외해 약 **2560 × 1268 dp**. 창 분할 시 크기는 달라집니다. |
| 서버·빌드 도구 | Docker, JDK 21, Android SDK 35, Node.js 20 이상 |

에뮬레이터 설치 절차는 [개발 환경 안내](docs/dev-setup.md)를 참고하세요.

## 로컬 서버 및 필요한 설정

APK의 중계 서버 주소는 `http://10.0.2.2:8080`으로 설정되어 있습니다. Android 에뮬레이터에서 `10.0.2.2`는 **에뮬레이터를 실행하는 컴퓨터의 localhost**이므로, 서버도 같은 컴퓨터에서 실행합니다.

```bash
./gradlew :relay:bootJar
cd web/admin && npm install && npm run build && cd ../..
test -e ops/.env || cp ops/.env.example ops/.env
# ops/.env에 필요한 값을 입력합니다.
cd ops && docker compose up -d --build
curl http://localhost:8080/actuator/health
```

서버 없이도 차량 운행 기록은 앱에 로컬로 저장됩니다. 차량 등록·배차 및 사용자 동기화·영수증 OCR·주소 변환에는 서버가 필요합니다. OCR용 `GEMINI_API_KEY`와 주소 변환용 `KAKAO_REST_KEY`는 각자 `ops/.env`에 설정하며 APK나 저장소에 넣지 않습니다. 휴대전화로 영수증 QR을 열려면 `PUBLIC_BASE_URL`을 휴대전화에서 접근 가능한 컴퓨터 주소로 설정해야 합니다. 운행기록부 메일 발송에는 회사 메일 계정과 서버의 `MAIL_SECRET_KEY` 설정이 필요합니다. 자세한 내용은 [APK 실행 안내](apk/README.md)에 있습니다.

## 프로젝트 문서

[기획서](docs/H-TaxLog_기획서.md) · [기능명세](docs/H-TaxLog_기능명세.md) · [기술명세](docs/H-TaxLog_기술명세.md)
