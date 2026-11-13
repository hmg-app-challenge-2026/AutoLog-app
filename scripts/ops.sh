#!/usr/bin/env bash
# 운영자 도우미(relay /ops API). 회사는 보통 운영 웹 [회사 가입]으로 직접 가입한다 —
# 이 스크립트는 비밀번호 재설정, 기존 회사에 관리자 추가 같은 예외 처리용.
# 운영자 토큰은 ops/.env 의 OPS_TOKEN(없으면 예전 이름 ADMIN_TOKEN)에서 읽고 화면에 찍지 않는다.
# /ops 는 nginx 밖으로 나가지 않으므로 서버(relay 8080)가 도는 기계에서 실행한다.
#
#   scripts/ops.sh orgs                         회사 목록(관리자 수·차량 수)
#   scripts/ops.sh add-org "가나다물류"           회사 만들기 → orgId 출력
#   scripts/ops.sh admins <orgId>               그 회사 관리자 목록(adminId)
#   scripts/ops.sh add-admin <orgId> <이메일>    그 회사 관리자 만들기(비밀번호는 물어본다, 8자 이상)
#   scripts/ops.sh reset-password <adminId>     관리자 비밀번호 새로 정하기(그 관리자 로그인은 모두 끊김)
#
# 다른 주소의 relay: RELAY=http://서버:8080 scripts/ops.sh orgs
set -euo pipefail

cd "$(dirname "$0")/.."
RELAY="${RELAY:-http://localhost:8080}"

token() {
  local v=""
  if [[ -f ops/.env ]]; then
    v="$(grep -E '^OPS_TOKEN=' ops/.env | tail -1 | cut -d= -f2- || true)"
    [[ -z "$v" ]] && v="$(grep -E '^ADMIN_TOKEN=' ops/.env | tail -1 | cut -d= -f2- || true)"
  fi
  v="${OPS_TOKEN:-$v}"
  if [[ -z "$v" ]]; then
    echo "운영자 토큰이 없습니다. ops/.env 에 OPS_TOKEN=길고랜덤한값 을 넣고 relay 를 다시 띄우세요." >&2
    exit 1
  fi
  printf '%s' "$v"
}

json() { python3 -c 'import json,sys; print(json.dumps(dict(zip(sys.argv[1::2], sys.argv[2::2])), ensure_ascii=False))' "$@"; }

call() {
  local method="$1" path="$2" body="${3:-}"
  local out status
  out="$(curl -sS -w '\n%{http_code}' -X "$method" "$RELAY$path" \
    -H "X-Ops-Token: $(token)" -H 'Content-Type: application/json' ${body:+--data "$body"})"
  status="${out##*$'\n'}"
  out="${out%$'\n'*}"
  if [[ "$status" -ge 400 ]]; then
    echo "실패 (HTTP $status): $out" >&2
    exit 1
  fi
  [[ -n "$out" ]] && printf '%s\n' "$out" | python3 -m json.tool --no-ensure-ascii || echo "ok"
}

ask_password() {
  local p1 p2
  read -r -s -p "비밀번호(8자 이상): " p1; echo >&2
  read -r -s -p "한 번 더: " p2; echo >&2
  if [[ "$p1" != "$p2" ]]; then echo "두 비밀번호가 다릅니다" >&2; exit 1; fi
  printf '%s' "$p1"
}

case "${1:-}" in
  orgs) call GET /ops/orgs ;;
  add-org)
    [[ -n "${2:-}" ]] || { echo "사용법: scripts/ops.sh add-org \"회사 이름\"" >&2; exit 1; }
    call POST /ops/orgs "$(json name "$2")" ;;
  admins)
    [[ -n "${2:-}" ]] || { echo "사용법: scripts/ops.sh admins <orgId>" >&2; exit 1; }
    call GET "/ops/orgs/$2/admins" ;;
  add-admin)
    [[ -n "${2:-}" && -n "${3:-}" ]] || { echo "사용법: scripts/ops.sh add-admin <orgId> <이메일>" >&2; exit 1; }
    pw="$(ask_password)"
    call POST "/ops/orgs/$2/admins" "$(json email "$3" password "$pw")" ;;
  reset-password)
    [[ -n "${2:-}" ]] || { echo "사용법: scripts/ops.sh reset-password <adminId>" >&2; exit 1; }
    pw="$(ask_password)"
    call POST "/ops/admins/$2/password" "$(json password "$pw")" ;;
  *) sed -n '2,13p' "$0"; exit 1 ;;
esac
