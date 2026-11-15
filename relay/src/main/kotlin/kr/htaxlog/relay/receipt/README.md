# `kr.htaxlog.relay.receipt`

F-41 영수증: GET /r/{code}, POST /r/{code}/receipt → Gemini OCR → inbox

- QR 토큰은 업로드가 끝까지 성공했을 때만 소모한다(토큰 소모·receipts·inbox 한 트랜잭션). 중간 실패 뒤 같은 QR로 다시 보낼 수 있다.
- 같은 QR로 이미 들어간 뒤 다시 보내면 409 `RECEIPT_DUPLICATE`(응답을 놓친 재전송). 같은 사진이라도 새 QR이면 받는다(V6 `receipts.qr_token` 유니크).
- Gemini: 기본 `gemini-3.5-flash-lite`(`GEMINI_MODEL`), 호출당 25초·재시도 포함 45초 예산. 넘으면 `ocrStatus=FAILED`로 사진만 보낸다. nginx 업로드 `proxy_read_timeout 90s`.
