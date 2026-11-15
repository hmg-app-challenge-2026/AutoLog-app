#!/usr/bin/env bash
# Pleos Connect 에뮬레이터에서 HTaxLog를 손으로 시험하는 도우미.
# 가상 콘솔(에뮬레이터 안 8088)은 "브라우저 세션"이 살아 있을 때만 값을 받으므로, 명령마다 세션 신호를 함께 보낸다.
#
#   scripts/emu.sh setup            포트 연결 + 권한(위치·알림·차속) 부여 + 앱 실행
#   scripts/emu.sh gear Drive       기어: Park | Reverse | Neutral | Drive
#   scripts/emu.sh speed 40         차속(km/h). 움직임 판단에만 쓰인다
#   scripts/emu.sh odo 23.4         계기판 누적거리(km). 거리는 이 값의 차이로만 계산된다(줄일 수 없음)
#   scripts/emu.sh drive 40 5       운행 한 번: D → 40km/h로 달리며 계기판을 5km 올림 → 0km/h → P
#                                   (콘솔은 차속을 올려도 계기판이 저절로 늘지 않아서 대신 올려 준다)
#   scripts/emu.sh drive 40 5 30    위와 같은데 30초 동안 달린다(기본 15초)
#   scripts/emu.sh follow           콘솔에서 직접 운전하는 동안 차속만큼 계기판을 계속 올린다 (Ctrl+C로 끝)
#   scripts/emu.sh at seocho        차 위치(GPS)를 옮긴다: seocho(서초역) | hyundai(현대자동차 본사) | <위도> <경도>
#   scripts/emu.sh demo-commute     시연용 출근: 서초역에서 출발 → 약 30초 동안 속도를 올렸다 내리며 이동 → 현대자동차 본사 도착 → P
#                                   (실제로는 15분 넘게 걸리지만 영상 길이 때문에 압축한다. follow를 끄고 돌린다)
#   scripts/emu.sh demo-commute-fuel [초|enter]
#                                   위 출근 시연 + 가는 길에 주유하기: 서초역 → 중간에 P(앱을 띄우고 [주유 기록 추가]를 누른다)
#                                   → 차 화면에서 주유 기록을 저장하면 저절로 다시 출발 → 현대자동차 본사.
#                                   숫자를 주면 그 초만큼만 서 있고, enter면 Enter를 누를 때까지 선다.
#                                   먼저 내비 목적지를 "현대자동차 본사"로 찍어 둔다 — 목적지에서 먼 P는 기록을 끊지 않는다(300m 규칙).
#                                   앱에 "주유소" 종류 장소가 있으면 그곳에 서고, 없으면 두 곳의 중간에 선다
#   scripts/emu.sh state            콘솔의 현재 기어·차속·계기판
#   scripts/emu.sh log              HTaxLog 로그 보기 (Ctrl+C로 끝)
set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG="kr.htaxlog.vehicle"
CONSOLE="http://localhost:8088"

heartbeat() {
  "$ADB" forward tcp:8088 tcp:8088 >/dev/null
  curl -s -m 2 -X POST "$CONSOLE/browser/session?client=htaxlog-emu-sh" >/dev/null
}

odo_now() {
  heartbeat
  curl -s -m 3 "$CONSOLE/vehicle/state" | python3 -c 'import json,sys; print(round(json.load(sys.stdin)["odometer"].get("traveled_distance_km") or 0, 2))'
}

# 시연 장소 좌표(위도 경도). 차 설정 [목적 → 장소 추가 → 지금 위치]로 등록하면 이 좌표와 정확히 맞는다
SEOCHO="37.49186 127.00766"   # 서초역
HYUNDAI="37.464363 127.042663"  # 현대자동차 본사 양재사옥(카카오 장소 좌표)

# 한 구간을 달린다: 시작·끝 좌표, 거리(km), 차속 순서(1초에 하나). 계기판·위치를 차속 비율로 나눠 올린다
segment() {
  local lat0=$1 lng0=$2 lat1=$3 lng1=$4 km=$5; shift 5
  local speeds=("$@") total=0 sum=0 v odo lat lng start
  for v in "${speeds[@]}"; do total=$((total + v)); done
  start="$(odo_now)"
  put "/vehicle/gear?gear=Drive"
  sleep 1
  for v in "${speeds[@]}"; do
    sum=$((sum + v))
    read -r odo lat lng <<<"$(python3 -c "f=$sum/$total; print(round($start+$km*f,2), round($lat0+($lat1-$lat0)*f,6), round($lng0+($lng1-$lng0)*f,6))")"
    heartbeat || true
    curl -s -m 2 -X PUT "$CONSOLE/vehicle/controls?speed_kph=$v" >/dev/null || true
    curl -s -m 2 -X PUT "$CONSOLE/vehicle/odometer?traveled_distance_km=$odo" >/dev/null || true
    geo "$lat" "$lng"
    printf '\r  %3s km/h · 계기판 %s km   ' "$v" "$odo"
    sleep 1
  done
  echo
  geo "$lat1" "$lng1"
  put "/vehicle/controls?speed_kph=0"
  sleep 2
  put "/vehicle/gear?gear=Park"
}

# 앱에 등록된 첫 주유소(종류 GAS_STATION)의 "위도 경도 이름"
gas_station() {
  local tmp; tmp="$(mktemp -d)"
  for f in htaxlog.db htaxlog.db-wal htaxlog.db-shm; do
    "$ADB" exec-out run-as "$PKG" --user 10 cat "databases/$f" >"$tmp/$f" 2>/dev/null || true
  done
  python3 -I - "$tmp/htaxlog.db" <<'PY'
import sqlite3, sys
r = sqlite3.connect(sys.argv[1]).execute("select lat, lng, label from places where kind = 'GAS_STATION' limit 1").fetchone()
print(f"{r[0]} {r[1]} {r[2]}" if r else "")
PY
  rm -rf "$tmp"
}

# 차에 저장된 주유·비용 기록 수(expenses)
expense_count() {
  local tmp; tmp="$(mktemp -d)"
  for f in htaxlog.db htaxlog.db-wal htaxlog.db-shm; do
    "$ADB" exec-out run-as "$PKG" --user 10 cat "databases/$f" >"$tmp/$f" 2>/dev/null || true
  done
  # 읽지 못하면 아무것도 출력하지 않는다(0으로 착각해 "저장됐다"고 판단하지 않게)
  python3 -I -c 'import sqlite3,sys; print(sqlite3.connect(sys.argv[1]).execute("select count(*) from expenses").fetchone()[0])' "$tmp/htaxlog.db" 2>/dev/null || true
  rm -rf "$tmp"
}

# 앱 화면에서 그 글자(정확히 일치)를 찾아 누른다
tap_text() {
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local xy
  xy="$("$ADB" shell cat /sdcard/ui.xml | python3 -I -c '
import re, sys
x = sys.stdin.read(); t = sys.argv[1]
for n in re.findall(r"<node [^>]*>", x):
    if re.search(r"text=\"%s\"" % re.escape(t), n):
        b = list(map(int, re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n).groups()))
        print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2); break
' "$1")"
  [ -z "$xy" ] && { echo "화면에서 [$1]을 찾지 못했어요" >&2; return 1; }
  "$ADB" shell input tap $xy
}

# 에뮬레이터 GPS를 옮긴다. Pleos Fused Location과 내비가 이 값을 따라온다 (adb emu geo fix는 경도 위도 순)
geo() {
  "$ADB" emu geo fix "$2" "$1" >/dev/null
}

place() {
  case "$1" in
    seocho) echo "$SEOCHO" ;;
    hyundai) echo "$HYUNDAI" ;;
    *) echo "$1 $2" ;;
  esac
}

put() {
  heartbeat
  curl -s -m 3 -X PUT "$CONSOLE$1" >/dev/null && echo "ok: $1"
}

case "${1:-}" in
  setup)
    "$ADB" forward tcp:8088 tcp:8088 >/dev/null
    # Pleos(AAOS)는 앱을 사용자 10으로 실행한다
    for p in pleos.car.permission.FUSED_LOCATION android.permission.POST_NOTIFICATIONS android.car.permission.CAR_SPEED; do
      "$ADB" shell pm grant --user 10 "$PKG" "$p" && echo "granted $p"
    done
    "$ADB" shell am start --user 10 -n "$PKG/.MainActivity" >/dev/null && echo "app started"
    ;;
  gear) put "/vehicle/gear?gear=${2:?Park|Reverse|Neutral|Drive}" ;;
  speed) put "/vehicle/controls?speed_kph=${2:?km/h}" ;;
  odo) put "/vehicle/odometer?traveled_distance_km=${2:?km}" ;;
  drive)
    speed="${2:?사용법: scripts/emu.sh drive <km/h> <km> [초]}"
    km="${3:?사용법: scripts/emu.sh drive <km/h> <km> [초]}"
    secs="${4:-15}"
    start="$(odo_now)"
    steps="$secs"
    echo "출발: 계기판 ${start} km, ${speed} km/h로 ${km} km (${secs}초)"
    put "/vehicle/gear?gear=Drive"
    put "/vehicle/controls?speed_kph=$speed"
    # 앱이 차속을 보고 기록을 시작할 시간을 준다
    sleep 3
    for i in $(seq 1 "$steps"); do
      sleep 1
      odo="$(python3 -c "print(round($start + $km * $i / $steps, 1))")"
      heartbeat
      curl -s -m 3 -X PUT "$CONSOLE/vehicle/odometer?traveled_distance_km=$odo" >/dev/null
      printf '\r  계기판 %s km  ' "$odo"
    done
    echo
    put "/vehicle/controls?speed_kph=0"
    sleep 2
    put "/vehicle/gear?gear=Park"
    echo "도착: 계기판 $(odo_now) km — 차 화면에 운행 결과가 떠야 한다"
    ;;
  follow)
    # 콘솔(페달·내비 모의 주행)은 차속만 바꾸고 계기판은 그대로 둔다 — 실제 차처럼 차속 × 시간만큼 더해 준다
    echo "콘솔에서 운전하세요. 차속만큼 계기판을 올립니다 (Ctrl+C로 끝)"
    odo="$(odo_now)"
    while true; do
      sleep 1
      # 콘솔이 잠깐 늦게 답해도(curl 시간 초과) 끝내지 않고 다음 초에 다시 한다
      heartbeat || true
      speed="$(curl -s -m 2 "$CONSOLE/vehicle/state" | python3 -c 'import json,sys; print(json.load(sys.stdin)["controls"].get("speed_kph") or 0)' || echo 0)"
      odo="$(python3 -c "print(round($odo + $speed / 3600, 3))")"
      curl -s -m 2 -X PUT "$CONSOLE/vehicle/odometer?traveled_distance_km=$odo" >/dev/null || true
      printf '\r  차속 %s km/h · 계기판 %.1f km   ' "$speed" "$odo"
    done
    ;;
  at)
    read -r lat lng <<<"$(place "${2:?seocho | hyundai | <위도> <경도>}" "${3:-}")"
    geo "$lat" "$lng" && echo "위치: $lat, $lng"
    ;;
  demo-commute)
    if pgrep -f "emu.sh follow" >/dev/null; then
      echo "follow가 돌고 있어요. 계기판을 둘이 같이 올리지 않게 follow를 먼저 꺼 주세요." >&2
      exit 1
    fi
    read -r lat0 lng0 <<<"$SEOCHO"
    read -r lat1 lng1 <<<"$HYUNDAI"
    km=5.2                                      # 서초역 → 현대자동차 본사 도로 거리(대략)
    speeds=(10 20 30 40 50 60 70 80 90 100 100 100 100 100 100 100 100 100 100 100 90 80 70 60 50 40 30 20 10 5)   # 30초 동안의 차속(km/h): 10초 가속 → 10초 정속 → 10초 감속
    total=0; for v in "${speeds[@]}"; do total=$((total + v)); done
    geo "$lat0" "$lng0"
    put "/vehicle/gear?gear=Park"
    put "/vehicle/controls?speed_kph=0"
    echo "서초역에서 대기… (3초)"
    sleep 3
    start="$(odo_now)"
    echo "출발: 계기판 ${start} km"
    put "/vehicle/gear?gear=Drive"
    sleep 1
    sum=0
    for v in "${speeds[@]}"; do
      sum=$((sum + v))
      read -r odo lat lng <<<"$(python3 -c "f=$sum/$total; print(round($start+$km*f,2), round($lat0+($lat1-$lat0)*f,6), round($lng0+($lng1-$lng0)*f,6))")"
      heartbeat
      curl -s -m 2 -X PUT "$CONSOLE/vehicle/controls?speed_kph=$v" >/dev/null || true
      curl -s -m 2 -X PUT "$CONSOLE/vehicle/odometer?traveled_distance_km=$odo" >/dev/null || true
      geo "$lat" "$lng"
      printf '\r  %3s km/h · 계기판 %s km   ' "$v" "$odo"
      sleep 1
    done
    echo
    geo "$lat1" "$lng1"
    put "/vehicle/controls?speed_kph=0"
    sleep 2
    put "/vehicle/gear?gear=Park"
    echo "도착: 현대자동차 본사 · 계기판 $(odo_now) km — 차 화면에 운행 결과가 떠야 한다"
    sleep 2
    "$ADB" logcat -d -s HTaxLog/session | grep -E "gear P|목적지까지|STOP|stop" | tail -2 || true
    ;;
  demo-commute-fuel)
    if pgrep -f "emu.sh follow" >/dev/null; then
      echo "follow가 돌고 있어요. 계기판을 둘이 같이 올리지 않게 follow를 먼저 꺼 주세요." >&2
      exit 1
    fi
    wait="${2:-save}"
    read -r lat0 lng0 <<<"$SEOCHO"
    read -r lat1 lng1 <<<"$HYUNDAI"
    read -r glat glng gname <<<"$(gas_station)"
    if [ -z "${glat:-}" ]; then
      # 등록된 주유소가 없으면 두 곳의 중간에 선다
      read -r glat glng <<<"$(python3 -c "print(round(($lat0+$lat1)/2,6), round(($lng0+$lng1)/2,6))")"
      gname="주유소"
    fi
    echo "※ 내비 목적지를 '현대자동차 본사'로 찍어 두었는지 확인하세요 — 없으면 중간 P에서 운행이 끝나요"
    # 전체 5.2 km를 직선거리 비율로 두 구간에 나눈다
    read -r km1 km2 <<<"$(python3 -c "
from math import hypot
a=hypot($glat-$lat0,($glng-$lng0)*0.8); b=hypot($lat1-$glat,($lng1-$glng)*0.8); print(round(5.2*a/(a+b),2), round(5.2*b/(a+b),2))")"
    geo "$lat0" "$lng0"
    put "/vehicle/gear?gear=Park"
    put "/vehicle/controls?speed_kph=0"
    echo "서초역에서 대기… (3초)"
    sleep 3
    echo "① 서초역 → ${gname} (${km1} km)"
    segment "$lat0" "$lng0" "$glat" "$glng" "$km1" 20 40 60 80 80 80 60 40 20 10
    sleep 2
    "$ADB" logcat -d -s HTaxLog/session | grep "gear P" | tail -1 | sed 's/^.*HTaxLog\/session: /  /' || true
    before=""
    for _ in 1 2 3 4 5; do before="$(expense_count)"; [ -n "$before" ] && break; sleep 1; done
    before="${before:-0}"
    # 앱을 앞으로 띄우고 [주유 기록 추가]를 누른다(영수증 QR이 뜬다)
    "$ADB" shell am start --user 10 -n "$PKG/.MainActivity" >/dev/null
    sleep 3
    # 다른 화면에 있으면 뒤로 가기로 Home까지 돌아간 뒤 누른다
    for _ in 1 2 3 4; do
      if tap_text "주유 기록 추가" 2>/dev/null; then
        echo "  [주유 기록 추가]를 눌렀어요 — 폰으로 QR을 찍어 영수증을 보내세요"
        break
      fi
      "$ADB" shell input keyevent 4
      sleep 1
    done
    if [ "$wait" = "save" ]; then
      echo "⛽ ${gname}에 주차 — 차 화면에서 주유 기록을 저장하면 다시 출발해요 (최대 10분)"
      for _ in $(seq 1 300); do
        sleep 2
        now="$(expense_count)"
        [ -n "$now" ] && [ "$now" -gt "$before" ] && { echo "  주유 기록이 저장됐어요(${before} → ${now}건) — 3초 뒤 출발"; sleep 3; break; }
      done
    elif [ "$wait" = "enter" ]; then
      read -r -p "⛽ ${gname}에 주차했어요. 영수증을 다 등록하면 Enter를 눌러 다시 출발해요… " _
    else
      echo "⛽ ${gname}에 주차 — ${wait}초 동안 주유·영수증 등록"
      sleep "$wait"
    fi
    echo "② ${gname} → 현대자동차 본사 (${km2} km)"
    segment "$glat" "$glng" "$lat1" "$lng1" "$km2" 20 40 60 80 100 100 80 60 40 20
    echo "도착: 현대자동차 본사 · 계기판 $(odo_now) km — 차 화면에 운행 결과가 떠야 한다"
    ;;
  state)
    heartbeat
    curl -s -m 3 "$CONSOLE/vehicle/state" | python3 -c '
import json, sys
d = json.load(sys.stdin)
print("gear  :", (d["gear"].get("actual") or {}).get("value"))
print("speed :", d["controls"].get("speed_kph"), "km/h")
print("odo   :", d["odometer"].get("traveled_distance_km"), "km")'
    ;;
  log) "$ADB" logcat -s HTaxLog/core HTaxLog/session HTaxLog/vehicle ;;
  *) sed -n '2,15p' "$0"; exit 1 ;;
esac
