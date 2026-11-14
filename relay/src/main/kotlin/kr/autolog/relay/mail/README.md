# `kr.autolog.relay.mail`

회사 발신 메일 계정 (F-24, F-54). 회사마다 하나, 차의 메일 계정도 하나다 — 운영 웹 [메일 설정]에서 정하면 회사의 모든 차에 inbox MAIL_ACCOUNT로 간다. 메일은 차가 직접 보낸다(엑셀은 서버를 거치지 않는다).

| API | 인증 | 설명 |
|---|---|---|
| `GET /admin/mail-account` | 관리자 세션 | 계정(비밀번호 없이). 없으면 204 |
| `PUT /admin/mail-account` | 관리자 세션 | SMTP 로그인 확인(`SmtpVerifier`) 후 저장 → 배포. 비밀번호를 비우면 저장된 것을 그대로 쓴다 |
| `DELETE /admin/mail-account` | 관리자 세션 | 지우고 차에도 지움 편지 |

- 비밀번호는 `MailSecret`(AES-256-GCM, 키 = 환경변수 `MAIL_SECRET_KEY`의 SHA-256)으로 암호화해 `org_mail_accounts.password_enc`에 둔다. 키가 없으면 503 `MAIL_SECRET_MISSING`.
- inbox 표에는 비밀번호 없이 넣고, 차가 가져갈 때 `MailAccountInboxRefresher`가 그 순간의 계정으로 채운다.
- 로그에는 서버·포트만 남긴다(주소·비밀번호 X).
