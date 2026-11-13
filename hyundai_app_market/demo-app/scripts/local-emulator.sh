#!/usr/bin/env bash
# 플레오스 가드 데모: 로컬 Pleos 에뮬레이터 보조 명령 (macOS·Linux)
# 클라우드에서 실행해 보지 못했습니다. 처음에는 한 줄씩 실행해 결과를 확인하세요.
# 사용: scripts/local-emulator.sh <install|console|drive-on|drive-off|record|record-stop|shot>
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
stamp="$(date +%Y%m%d-%H%M%S)"
case "${1:-}" in
  install)     (cd "$root" && ./gradlew :app:installDebug) ;;
  console)     adb forward tcp:8088 tcp:8088 && echo "브라우저에서 http://127.0.0.1:8088/ 를 여세요" ;;
  drive-on)    adb shell cmd hmg_car_service --service drivingstate driving_regulation on ;;
  drive-off)   adb shell cmd hmg_car_service --service drivingstate driving_regulation off ;;
  record)      adb emu screenrecord start --time-limit 180 "$PWD/demo-$stamp.webm" ;;
  record-stop) adb emu screenrecord stop ;;
  shot)        adb exec-out screencap -p > "$PWD/shot-$stamp.png" ;;
  *) echo "사용: $0 <install|console|drive-on|drive-off|record|record-stop|shot>"; exit 1 ;;
esac
