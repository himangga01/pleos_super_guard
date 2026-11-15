# Pleos Super Care phone helper: phone emulator, install, real-phone adb reverse (Windows PowerShell 5.1, ASCII only).
# The car emulator is emulator-5554. This script only talks to emulator-5556 (the phone) or to the -Serial you give,
# and it never starts, stops or changes emulator-5554 or the Pleos AVD.
#
# Usage: .\scripts\phone-emulator.ps1 <command> [options]
#   create      Create AVD Pleos_SuperCare_Phone from system-images;android-30;google_apis;x86_64 in ANDROID_AVD_HOME
#               (default <sdk>\avd) and set camera/mic/sensor options in its config.ini. Safe to run again.
#   start       Start the phone AVD on console port 5556 (adb serial emulator-5556): back camera = virtual scene,
#               host microphone allowed (-NoHostAudio to keep the PC microphone off), optional -Poster <png> on the scene wall.
#   wait        Wait until emulator-5556 has finished booting.
#   install     Build demo-app/phone-app (Gradle assembleDebug, phone project only) and adb install -r on emulator-5556 only.
#   reverse     Real phone over USB/Wi-Fi adb: adb -s <Serial> reverse tcp:8766 tcp:8766.
#               Then set the server in the phone app to http://127.0.0.1:8766.
#   server      Start the demo server in a new window (python demo-app/server/care_server.py, 127.0.0.1:8766).
#   vibrate     Test input only: stream a sine wave into the phone emulator accelerometer through the emulator console
#               (-Hz 14 -Amp 0.3 -Seconds 20; m/s2 on the z axis, phone lying flat). Not a real vibration.
#   poster      Show a picture on the virtual scene TV that the back camera sees (-Image <file>, ASCII path);
#               no -Image restores the default. Use it to photograph a test warning-light picture.
#   push-image  Copy a picture into the phone emulator gallery (-Image <file>), e.g. a test warning-light picture.
#   shot        Save a screenshot of the phone emulator (-Out <folder>, -Name <file.png>).
#
# Environment: ANDROID_HOME or ANDROID_SDK_ROOT (default C:\Android), ANDROID_AVD_HOME (default <sdk>\avd),
#              JAVA_HOME (default <sdk>\tools\jdk-21.0.12.1+1 when it exists).
param(
  [Parameter(Mandatory = $true)][string]$cmd,
  [string]$Serial = "",
  [string]$Poster = "",
  [switch]$NoHostAudio,
  [double]$Hz = 14,
  [double]$Amp = 0.3,
  [int]$Seconds = 20,
  [string]$Image = "",
  [string]$Out = "",
  [string]$Name = ""
)
$ErrorActionPreference = "Stop"
$demo = Split-Path -Parent $PSScriptRoot
$sdk = "C:\Android"
if ($env:ANDROID_HOME) { $sdk = $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $sdk = $env:ANDROID_SDK_ROOT }
$env:ANDROID_SDK_ROOT = $sdk
if (-not $env:ANDROID_AVD_HOME) { $env:ANDROID_AVD_HOME = Join-Path $sdk "avd" }
if (-not $env:JAVA_HOME) {
  $jdk = Join-Path $sdk "tools\jdk-21.0.12.1+1"
  if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }
}
$avd = "Pleos_SuperCare_Phone"
$port = 5556
$phone = "emulator-$port"
$sysImage = "system-images;android-30;google_apis;x86_64"
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulatorExe = Join-Path $sdk "emulator\emulator.exe"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"

function Find-AvdManager {
  $hit = Get-ChildItem (Join-Path $sdk "cmdline-tools") -Recurse -Filter "avdmanager.bat" -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($hit) { return $hit.FullName }
  return $null
}

function Set-IniValues([string]$path, [hashtable]$values) {
  $lines = @(Get-Content -LiteralPath $path)
  foreach ($k in $values.Keys) {
    $found = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
      if ($lines[$i].StartsWith("$k=")) { $lines[$i] = "$k=$($values[$k])"; $found = $true }
    }
    if (-not $found) { $lines += "$k=$($values[$k])" }
  }
  Set-Content -LiteralPath $path -Value $lines -Encoding ASCII
}

function Assert-NotCar([string]$s) {
  if ($s -eq "emulator-5554") { throw "emulator-5554 is the Pleos car emulator. Use the phone (emulator-5556) or a real phone serial." }
}

switch ($cmd) {
  "create" {
    $ini = Join-Path $env:ANDROID_AVD_HOME "$avd.ini"
    $dir = Join-Path $env:ANDROID_AVD_HOME "$avd.avd"
    if (-not (Test-Path $ini)) {
      if (-not (Test-Path (Join-Path $sdk "system-images\android-30\google_apis\x86_64"))) { throw "Missing $sysImage under $sdk (install it first)." }
      $mgr = Find-AvdManager
      if (-not $mgr) { throw "avdmanager.bat not found under $sdk\cmdline-tools" }
      "no" | & $mgr create avd -n $avd -k $sysImage -d pixel_5 -p $dir
      if ($LASTEXITCODE -ne 0) { throw "avdmanager failed ($LASTEXITCODE)" }
    }
    # Phone hardware for the demo: back camera = virtual 3D scene, front = emulated, microphone + speaker,
    # accelerometer and gyroscope, 2 GB RAM (the car emulator already uses 8 GB).
    Set-IniValues (Join-Path $dir "config.ini") @{
      "hw.camera.back" = "virtualscene"; "hw.camera.front" = "emulated"; "hw.audioInput" = "yes"; "hw.audioOutput" = "yes";
      "hw.accelerometer" = "yes"; "hw.gyroscope" = "yes"; "hw.sensors.orientation" = "yes"; "hw.sensors.proximity" = "yes";
      "hw.ramSize" = "2048"; "hw.keyboard" = "yes"; "hw.gpu.enabled" = "yes"; "hw.gpu.mode" = "host";
      "disk.dataPartition.size" = "4G"; "showDeviceFrame" = "no"; "PlayStore.enabled" = "no"
    }
    Write-Host "AVD ready: $dir"
  }
  "start" {
    $emuArgs = @("-avd", $avd, "-port", "$port", "-camera-back", "virtualscene", "-camera-front", "emulated", "-gpu", "host", "-no-boot-anim")
    if (-not $NoHostAudio) { $emuArgs += "-allow-host-audio" }
    if ($Poster) { $emuArgs += @("-virtualscene-poster", "wall=$((Resolve-Path $Poster).Path)") }
    Start-Process -FilePath $emulatorExe -ArgumentList $emuArgs
    Write-Host "Starting $avd as $phone. Run: .\scripts\phone-emulator.ps1 wait"
  }
  "wait" {
    & $adb -s $phone wait-for-device
    for ($i = 0; $i -lt 150; $i++) {
      $b = & $adb -s $phone shell getprop sys.boot_completed
      if ("$b".Trim() -eq "1") { Write-Host "$phone booted"; break }
      Start-Sleep -Seconds 2
    }
  }
  "install" {
    Push-Location (Join-Path $demo "phone-app")
    try {
      .\gradlew.bat :app:assembleDebug
      if ($LASTEXITCODE -ne 0) { throw "phone-app build failed" }
    } finally { Pop-Location }
    & $adb -s $phone install -r (Join-Path $demo "phone-app\app\build\outputs\apk\debug\app-debug.apk")
  }
  "reverse" {
    if (-not $Serial) { throw "Give the real phone serial: -Serial <serial> (see: adb devices)" }
    Assert-NotCar $Serial
    & $adb -s $Serial reverse tcp:8766 tcp:8766
    Write-Host "Done. In the phone app (Settings) use server http://127.0.0.1:8766"
  }
  "server" {
    Start-Process -FilePath "python" -ArgumentList @("-I", (Join-Path $demo "server\care_server.py"))
    Write-Host "Demo server window opened (http://127.0.0.1:8766, emulators use http://10.0.2.2:8766)"
  }
  "vibrate" {
    # Emulator console on 127.0.0.1:5556 (the phone only). "sensor set acceleration x:y:z" overrides the virtual sensor.
    $client = New-Object System.Net.Sockets.TcpClient("127.0.0.1", $port)
    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $writer.NewLine = "`n"
    $writer.AutoFlush = $true
    $buf = New-Object byte[] 65536
    $drain = { Start-Sleep -Milliseconds 30; while ($stream.DataAvailable) { [void]$stream.Read($buf, 0, $buf.Length) } }
    & $drain
    $tokenFile = Join-Path $env:USERPROFILE ".emulator_console_auth_token"
    if (Test-Path $tokenFile) { $writer.WriteLine("auth " + (Get-Content $tokenFile -Raw).Trim()); & $drain }
    $inv = [Globalization.CultureInfo]::InvariantCulture
    $g = 9.80665
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $n = 0
    $next = 0.0
    while ($sw.Elapsed.TotalSeconds -lt $Seconds) {
      $t = $sw.Elapsed.TotalSeconds
      $z = $g + $Amp * [Math]::Sin(2 * [Math]::PI * $Hz * $t)
      $writer.WriteLine("sensor set acceleration 0:0:" + $z.ToString("F4", $inv))
      $n++
      if (($n % 100) -eq 0) { while ($stream.DataAvailable) { [void]$stream.Read($buf, 0, $buf.Length) } }
      $next += 5.0
      while ($sw.Elapsed.TotalMilliseconds -lt $next) { }
    }
    $writer.WriteLine("sensor set acceleration 0:9.80665:0")
    & $drain
    $writer.WriteLine("quit")
    $client.Close()
    Write-Host ("Sent {0} accelerometer values ({1} Hz sine, {2} m/s2, {3} s) to {4}. Test input, not a real vibration." -f $n, $Hz, $Amp, $Seconds, $phone)
  }
  "poster" {
    # Show a picture on the virtual scene's wall screen (the TV the back camera sees first) without restarting.
    # Without -Image the default picture comes back. Use an ASCII-only file path.
    if ($Image) { & $adb -s $phone emu virtualscene-image wall ((Resolve-Path $Image).Path) }
    else { & $adb -s $phone emu virtualscene-image wall }
  }
  "push-image" {
    if (-not $Image) { throw "Give -Image <file>" }
    $leaf = Split-Path -Leaf $Image
    & $adb -s $phone push $Image "/sdcard/Pictures/$leaf"
    & $adb -s $phone shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Pictures/$leaf" | Out-Null
    Write-Host "Copied to /sdcard/Pictures/$leaf on $phone"
  }
  "shot" {
    $dir = (Get-Location).Path
    if ($Out) { $dir = $Out }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $f = "phone-$stamp.png"
    if ($Name) { $f = $Name }
    # Windows PowerShell 5.1 turns binary stdout into text, so save on the device and pull.
    & $adb -s $phone shell screencap -p "/sdcard/$f"
    & $adb -s $phone pull "/sdcard/$f" (Join-Path $dir $f) | Out-Null
    & $adb -s $phone shell rm "/sdcard/$f"
    Write-Host (Join-Path $dir $f)
  }
  default { Write-Host "Unknown command: $cmd (create, start, wait, install, reverse, server, vibrate, poster, push-image, shot)" }
}
