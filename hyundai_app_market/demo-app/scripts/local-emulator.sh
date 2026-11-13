#!/usr/bin/env bash
# 플레오스 가드 데모: 로컬 Pleos 에뮬레이터 보조 명령 (macOS·Linux)
# 2026-11-13 Windows 로컬 PC에서 확인한 방법으로 고쳤습니다(이 셸 판은 macOS·Linux에서 실행해 보지 않았습니다).
# 휴대폰이 adb에 함께 붙어 있을 수 있으므로 ANDROID_SERIAL(기본 emulator-5554)로 대상을 지정합니다.
# 사용: scripts/local-emulator.sh <install|console|drive-on|drive-off|scenario ID|record|record-stop|shot>
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
stamp="$(date +%Y%m%d-%H%M%S)"
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
vc="http://127.0.0.1:8088"
case "${1:-}" in
  install)     (cd "$root" && ./gradlew :app:assembleDebug && adb install -r -g app/build/outputs/apk/debug/app-debug.apk) ;;
  console)     adb forward tcp:8088 tcp:8088 && echo "브라우저에서 $vc/ 를 여세요" ;;
  # hmg_car_service 명령은 Pleos Connect v2.1.0 이미지에 없습니다. Virtual Console 기어로 주행 제한을 바꿉니다.
  drive-on)    adb forward tcp:8088 tcp:8088; curl -s -X POST "$vc/browser/session?client=sh-helper" >/dev/null; curl -s -X PUT "$vc/vehicle/gear?gear=Drive" >/dev/null ;;
  drive-off)   adb forward tcp:8088 tcp:8088; curl -s -X POST "$vc/browser/session?client=sh-helper" >/dev/null; curl -s -X PUT "$vc/vehicle/gear?gear=Park" >/dev/null ;;
  scenario)    adb forward tcp:8088 tcp:8088; (cd "$root" && python3 scripts/vc_scenario.py "${2:?시나리오 ID}") ;;
  record)      adb emu screenrecord start --time-limit 180 "$PWD/demo-$stamp.webm" ;;
  record-stop) adb emu screenrecord stop ;;
  shot)        adb exec-out screencap -p > "$PWD/shot-$stamp.png" ;;
  *) echo "사용: $0 <install|console|drive-on|drive-off|scenario ID|record|record-stop|shot>"; exit 1 ;;
esac
