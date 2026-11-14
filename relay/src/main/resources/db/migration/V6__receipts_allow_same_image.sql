-- 같은 영수증 사진을 여러 번 보낼 수 있게 한다(2026-11-15 사용자 결정). 막는 기준을 사진(sha256)에서 QR 토큰으로 바꾼다:
-- QR 하나로는 한 장만 들어간다. 응답을 못 받고 같은 QR로 다시 보내면 이 토큰으로 이미 들어간 영수증을 찾아 409로 알려 준다.
DROP INDEX IF EXISTS receipts_vehicle_id_sha256_idx;
CREATE INDEX receipts_vehicle_sha_idx ON receipts (vehicle_id, sha256);
ALTER TABLE receipts ADD COLUMN qr_token CHAR(64);
CREATE UNIQUE INDEX receipts_qr_token_idx ON receipts (qr_token);
