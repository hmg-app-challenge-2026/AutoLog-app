-- 회사별 관리자 계정(2026-11-13). 서버는 하나, 회사(organizations)마다 관리자가 이메일·비밀번호로 로그인한다.
-- 로그인하면 세션 토큰을 주고 원문은 저장하지 않는다(sha256만). 세션의 org_id가 그 관리자가 볼 수 있는 범위다.
ALTER TABLE admins ADD COLUMN created_at TIMESTAMPTZ DEFAULT now();
UPDATE admins SET email = lower(trim(email)) WHERE email IS NOT NULL;
ALTER TABLE admins ALTER COLUMN org_id SET NOT NULL;
ALTER TABLE admins ALTER COLUMN email SET NOT NULL;
ALTER TABLE admins ALTER COLUMN password_hash SET NOT NULL;
CREATE UNIQUE INDEX organizations_name ON organizations (name);

CREATE TABLE admin_sessions (
  token_hash CHAR(64) PRIMARY KEY,
  admin_id UUID NOT NULL REFERENCES admins ON DELETE CASCADE,
  org_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX admin_sessions_admin ON admin_sessions (admin_id);
