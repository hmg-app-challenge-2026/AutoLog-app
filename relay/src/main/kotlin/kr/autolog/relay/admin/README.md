# `kr.autolog.relay.admin`

F-43/F-50~54 운영 웹 API: /admin/* → 조직 차량 inbox 배포

- 인증(2026-11-13): 회사 관리자가 `/admin/auth/login`으로 로그인 → 세션 토큰을 `X-Admin-Token`에. `AdminAuth.authorize`가 그 관리자의 `org_id`를 준다.
- 장소 검색 `GET /admin/places/search?q=`(운영 웹 [목적] 규칙의 [+ 새 장소], `../geo/PlaceSearch`).
- 회사 메일 계정은 `../mail/`(`/admin/mail-account`), 목적·목적 규칙은 `../purpose/`(`/admin/purposes`, `/admin/purpose-rules`)에 있다.
- 회사는 운영 웹에서 직접 가입한다(`/admin/auth/signup`, 회사 + 첫 관리자). 예외 처리(비밀번호 재설정 등)는 운영자 API: `../ops/OpsController`(`X-Ops-Token` = 환경변수 OPS_TOKEN), 도우미 `scripts/ops.sh`.
