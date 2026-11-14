# `kr.autolog.vehicle.mail`

[앱 B] 운행기록부 메일 발송 (F-22)과 SMTP 계정 저장 (F-24).

**메일 계정은 하나**(2026-11-14). 회사가 운영 웹 [메일 설정]에서 정하면 inbox MAIL_ACCOUNT로 와서 `MailService.applyServerAccount`가 덮어쓴다(앱 A `InboxSync.onMailAccount`가 연결). 화면은 `MailApi.account`(연결된 주소)만 보여 준다.

| 파일 | 역할 |
|---|---|
| `MailService` | `MailApi` 구현. 설정 확인 → 메일 작성 → 발송, 결과를 `MailResult`로 |
| `SmtpMailer` | Jakarta Mail SMTP. 587 STARTTLS / 465 SSL, 오류를 AUTH·NETWORK·INVALID_RECIPIENT로 분류 |
| `MailComposer` | 제목·본문·첨부 이름 (순수 함수) |
| `SmtpCredentialStore` | 계정 저장. 실제 구현은 `EncryptedSmtpCredentialStore`(EncryptedSharedPreferences) |
| `PrefsAutoSendPreference`, `PrefsHiddenRecipients`, `PrefsPurposeBookStore` | `domain/`의 저장 인터페이스 Android 구현(SharedPreferences): 월말 자동 발송, 이 차에서 뺀 회사 지정 받는 사람, 목적·목적 규칙 JSON |

남은 일: 오프라인 "전송 대기" 큐(reports.sendState, 앱 A Room + WorkManager)와 연결, 587 차단 시 HTTPS 메일 API 폴백, 에뮬레이터·차량 LTE에서 587 실제 발송 검증.
