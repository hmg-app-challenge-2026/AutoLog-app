-- 회사 메일 발신 계정 (F-24, F-54). 회사마다 하나. 관리자 웹에서 정하면 회사의 모든 차에 inbox로 간다.
-- 비밀번호는 AES-GCM으로 암호화해서 둔다(키는 서버 환경변수 MAIL_SECRET_KEY에만).
CREATE TABLE org_mail_accounts (
    org_id UUID PRIMARY KEY REFERENCES organizations ON DELETE CASCADE,
    host TEXT NOT NULL,
    port INT NOT NULL,
    username TEXT NOT NULL,
    password_enc TEXT NOT NULL,
    from_name TEXT,
    start_tls BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
