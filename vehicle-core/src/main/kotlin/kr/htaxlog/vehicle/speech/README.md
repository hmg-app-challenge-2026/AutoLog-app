# `kr.htaxlog.vehicle.speech`

[앱 B] 음성 입력 (F-09 목적, F-18 주유). 정차 화면에서만 쓴다.

| 파일 | 역할 |
|---|---|
| `SpeechGateway` | Gleo AI SpeechToText SDK 래퍼. `start()` 실패 시 `available=false` |
| `VoiceService` | `VoiceApi` 구현. 듣기(≤ 8초) → 파서 → Recognized / NotUnderstood / Failed |
| `PurposeVoiceParser` | 출퇴근·개인·출장 키워드 → 등록 장소 이름 → 업무 키워드 순. 원문은 note |
| `FuelVoiceParser`, `KoreanNumber` | "6만8천원 40리터", "칠만 원 사십 리터", "리터당 1,689원" |

TTS(운행 종료 안내 읽어 주기)는 명세상 선택 기능이라 아직 없다.
