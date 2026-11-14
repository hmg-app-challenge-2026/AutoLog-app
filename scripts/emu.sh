#!/usr/bin/env bash
# Pleos Connect 에뮬레이터에서 AutoLog를 손으로 시험하는 도우미.
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
#   scripts/emu.sh state            콘솔의 현재 기어·차속·계기판
#   scripts/emu.sh log              AutoLog 로그 보기 (Ctrl+C로 끝)
set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG="kr.autolog.vehicle"
CONSOLE="http://localhost:8088"

heartbeat() {
  "$ADB" forward tcp:8088 tcp:8088 >/dev/null
  curl -s -m 2 -X POST "$CONSOLE/browser/session?client=autolog-emu-sh" >/dev/null
}

odo_now() {
  heartbeat
  curl -s -m 3 "$CONSOLE/vehicle/state" | python3 -c 'import json,sys; print(json.load(sys.stdin)["odometer"].get("traveled_distance_km") or 0)'
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
  state)
    heartbeat
    curl -s -m 3 "$CONSOLE/vehicle/state" | python3 -c '
import json, sys
d = json.load(sys.stdin)
print("gear  :", (d["gear"].get("actual") or {}).get("value"))
print("speed :", d["controls"].get("speed_kph"), "km/h")
print("odo   :", d["odometer"].get("traveled_distance_km"), "km")'
    ;;
  log) "$ADB" logcat -s AutoLog/core AutoLog/session AutoLog/vehicle ;;
  *) sed -n '2,15p' "$0"; exit 1 ;;
esac
