-- 회사 목적과 출발→도착 목적 규칙 (F-08). 관리자 웹에서 정하면 회사의 모든 차에 inbox(PURPOSES)로 간다.
CREATE TABLE org_purposes (
    id BIGSERIAL PRIMARY KEY,
    org_id UUID NOT NULL REFERENCES organizations ON DELETE CASCADE,
    label TEXT NOT NULL,
    category TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX org_purposes_label ON org_purposes (org_id, label);

-- purpose: 차의 PurposeDetail.name — 기본 목적 이름 또는 custom:<구분>:<이름>. origin_place_id NULL = 출발지 어디서든.
CREATE TABLE org_purpose_rules (
    rule_id UUID PRIMARY KEY,
    org_id UUID NOT NULL REFERENCES organizations ON DELETE CASCADE,
    origin_place_id UUID REFERENCES places ON DELETE CASCADE,
    dest_place_id UUID NOT NULL REFERENCES places ON DELETE CASCADE,
    purpose TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX org_purpose_rules_route
    ON org_purpose_rules (org_id, COALESCE(origin_place_id, '00000000-0000-0000-0000-000000000000'::uuid), dest_place_id);
