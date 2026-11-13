# 플레오스 가드 데모: 로컬 Pleos 에뮬레이터 보조 명령 (Windows PowerShell)
# 클라우드에서 실행해 보지 못했습니다. 처음에는 한 줄씩 실행해 결과를 확인하세요.
# 사용: .\scripts\local-emulator.ps1 <명령>
#   install       데모 앱 빌드·설치
#   console       Virtual Console 연결(브라우저에서 http://127.0.0.1:8088/)
#   drive-on/off  주행 제한(driving regulation) 켜기/끄기
#   record        에뮬레이터 화면 녹화 시작(최대 180초, demo-YYYYMMDD-HHMMSS.webm)
#   record-stop   녹화 멈춤
#   shot          화면 캡처 한 장(shot-*.png)
param([Parameter(Mandatory = $true)][string]$cmd)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
switch ($cmd) {
  "install"     { Push-Location $root; .\gradlew.bat :app:installDebug; Pop-Location }
  "console"     { adb forward tcp:8088 tcp:8088; Start-Process "http://127.0.0.1:8088/" }
  "drive-on"    { adb shell cmd hmg_car_service --service drivingstate driving_regulation on }
  "drive-off"   { adb shell cmd hmg_car_service --service drivingstate driving_regulation off }
  "record"      { adb emu screenrecord start --time-limit 180 (Join-Path (Get-Location) "demo-$stamp.webm") }
  "record-stop" { adb emu screenrecord stop }
  "shot"        { adb exec-out screencap -p > (Join-Path (Get-Location) "shot-$stamp.png") }
  default       { Write-Host "알 수 없는 명령: $cmd" }
}
