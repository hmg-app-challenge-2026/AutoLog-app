# H-TaxLog 제출 APK 실행 안내

`AutoLog_H-TaxLog_release.apk`는 `vehicleRelease` 빌드입니다. 데모용 가짜 데이터 빌드가 아니라 Pleos 차량 데이터와 로컬 중계 서버를 사용하는 앱입니다. Android 9(API 28) 이상을 대상으로 서명되어 있으며, 차량 기능 시연은 **Pleos Connect 에뮬레이터**에서 진행해야 합니다. 일반 휴대전화나 기본 Android Automotive 에뮬레이터에는 필요한 Pleos 차량 서비스가 없습니다.

## Pleos 에뮬레이터에서 실행

1. [개발 환경 안내](../docs/dev-setup.md)에 따라 Pleos Connect 에뮬레이터를 준비하고 시작합니다.
2. 이 저장소의 루트에서 앱을 설치합니다.

   ```bash
   adb install apk/AutoLog_H-TaxLog_release.apk
   scripts/emu.sh setup
   ```

   이미 `kr.htaxlog.vehicle`의 **디버그 서명** 버전을 설치했다면 서명 키가 달라 업데이트가 거부됩니다. 그 경우 기존 앱을 제거한 후 설치합니다. 제거하면 기존 앱의 로컬 운행 기록도 삭제됩니다.

3. 첫 실행에서 차량 데이터, 위치, 알림 권한을 허용합니다. 다른 앱 위에 기록 알약을 띄우려면 Android의 **다른 앱 위에 표시** 특수 권한도 허용합니다. 권한 화면을 놓쳤다면 앱 설정의 차량 정보에서 다시 열 수 있습니다. 운행 시험은 `scripts/emu.sh demo-commute` 또는 Pleos 가상 콘솔로 진행할 수 있습니다.

## 자신의 컴퓨터에서 서버 실행

이 APK의 서버 주소는 `http://10.0.2.2:8080`으로 고정되어 있습니다. `10.0.2.2`는 **Android 에뮬레이터가 실행 중인 컴퓨터의 localhost**를 가리킵니다. 앱과 서버를 같은 컴퓨터에서 실행해야 합니다.

```bash
./gradlew :relay:bootJar
cd web/admin && npm install && npm run build && cd ../..
cp ops/.env.example ops/.env
# ops/.env에서 필요한 설정을 채웁니다.
cd ops && docker compose up -d --build
curl http://localhost:8080/actuator/health
```

서버 없이도 차량의 운행 기록은 로컬에 저장됩니다. 차량 등록, 배차 및 사용자 동기화, 영수증 OCR, 카카오 주소 변환은 서버 설정이 필요합니다. OCR은 `GEMINI_API_KEY`, 주소 변환은 `KAKAO_REST_KEY`가 필요하며 키는 각자 `ops/.env`에만 입력합니다. 운행기록부 메일 내보내기는 회사 메일 계정 설정이 필요합니다.

휴대전화로 차량 화면의 영수증 QR을 스캔하려면 `PUBLIC_BASE_URL`을 휴대전화에서도 접속 가능한 컴퓨터의 주소로 설정해야 합니다. 영수증 썸네일을 차량에서 보려면 `MINIO_PUBLIC_ENDPOINT`도 에뮬레이터에서 접속 가능한 주소로 설정합니다. `localhost`는 휴대전화와 에뮬레이터에서 각각 다른 기기를 가리킵니다.
