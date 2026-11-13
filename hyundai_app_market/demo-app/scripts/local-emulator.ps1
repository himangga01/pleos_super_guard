# 플레오스 가드 데모: 로컬 Pleos 에뮬레이터 보조 명령 (Windows PowerShell)
# 2026-11-13 로컬 PC에서 확인한 방법으로 고쳤습니다. 휴대폰이 adb에 함께 붙어 있을 수 있으므로 늘 에뮬레이터를 지정합니다.
# 사용: .\scripts\local-emulator.ps1 <명령> [시나리오 ID]
#   start         AVD 실행(Cold boot). 환경 변수 PLEOS_AVD(기본 Pleos_Connect_2_1_0), ANDROID_AVD_HOME 사용
#   install       데모 앱 빌드·설치(에뮬레이터에만)
#   console       Virtual Console 연결(브라우저에서 http://127.0.0.1:8088/)
#   drive-on/off  주행 제한 켜기/끄기: Virtual Console 기어를 D / P로 바꿉니다(hmg_car_service 명령은 이 이미지에 없음)
#   scenario <ID> 급발진 시나리오 재생(scripts\vc_scenario.py, Virtual Console 페이지는 닫고)
#   record        에뮬레이터 화면 녹화 시작(최대 180초, demo-YYYYMMDD-HHMMSS.webm)
#   record-stop   녹화 멈춤
#   shot          화면 캡처 한 장(shot-*.png)
param([Parameter(Mandatory = $true)][string]$cmd, [string]$arg)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $env:ANDROID_SERIAL) { $env:ANDROID_SERIAL = "emulator-5554" }
$avd = if ($env:PLEOS_AVD) { $env:PLEOS_AVD } else { "Pleos_Connect_2_1_0" }
switch ($cmd) {
  "start"       { $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }; Start-Process (Join-Path $sdk "emulator\emulator.exe") -ArgumentList "-avd", $avd, "-no-snapshot", "-gpu", "host" }
  "install"     { Push-Location $root; .\gradlew.bat :app:assembleDebug; adb -s $env:ANDROID_SERIAL install -r -g app\build\outputs\apk\debug\app-debug.apk; Pop-Location }
  "console"     { adb -s $env:ANDROID_SERIAL forward tcp:8088 tcp:8088; Start-Process "http://127.0.0.1:8088/" }
  "drive-on"    { adb -s $env:ANDROID_SERIAL forward tcp:8088 tcp:8088; Invoke-RestMethod -Method Post "http://127.0.0.1:8088/browser/session?client=ps-helper" | Out-Null; Invoke-RestMethod -Method Put "http://127.0.0.1:8088/vehicle/gear?gear=Drive" | Out-Null }
  "drive-off"   { adb -s $env:ANDROID_SERIAL forward tcp:8088 tcp:8088; Invoke-RestMethod -Method Post "http://127.0.0.1:8088/browser/session?client=ps-helper" | Out-Null; Invoke-RestMethod -Method Put "http://127.0.0.1:8088/vehicle/gear?gear=Park" | Out-Null }
  "scenario"    { adb -s $env:ANDROID_SERIAL forward tcp:8088 tcp:8088; Push-Location $root; python scripts\vc_scenario.py $arg; Pop-Location }
  "record"      { adb -s $env:ANDROID_SERIAL emu screenrecord start --time-limit 180 (Join-Path (Get-Location) "demo-$stamp.webm") }
  "record-stop" { adb -s $env:ANDROID_SERIAL emu screenrecord stop }
  # PowerShell 5.1의 > 는 바이너리를 글자로 바꿔 PNG를 깨뜨리므로 기기에 저장한 뒤 가져옵니다.
  "shot"        { $f = "shot-$stamp.png"; adb -s $env:ANDROID_SERIAL shell screencap -p "/sdcard/$f"; adb -s $env:ANDROID_SERIAL pull "/sdcard/$f" (Join-Path (Get-Location) $f); adb -s $env:ANDROID_SERIAL shell rm "/sdcard/$f" }
  default       { Write-Host "알 수 없는 명령: $cmd" }
}
