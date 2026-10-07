param([string]$DeviceSerial)
$ErrorActionPreference = 'Stop'
$rainRoot = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $rainRoot '.tools/android-sdk/platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $adb)) {
    if ($env:ANDROID_HOME) { $adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' }
    else { $adb = (Get-Command adb -ErrorAction Stop).Source }
}
$apk = Join-Path $rainRoot 'output/RAIN-demo.apk'
if (!(Test-Path -LiteralPath $apk)) { throw 'Run scripts/build.ps1 to create a verified APK first.' }
$deviceLines = & $adb devices
$ready = @($deviceLines | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if (!$DeviceSerial) {
    if ($ready.Count -eq 0) { throw 'No authorised Android phone found. Unlock the phone, enable USB debugging, connect a data-capable USB cable, and approve this computer on the phone.' }
    if ($ready.Count -gt 1) { throw 'More than one phone is connected. Pass -DeviceSerial with the device you want to use.' }
    $DeviceSerial = $ready[0]
}
if ($DeviceSerial -notin $ready) { throw 'The selected phone is not connected and authorised.' }
& $adb -s $DeviceSerial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed. Check the phone for an installation prompt and retry.' }
& $adb -s $DeviceSerial shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n 'in.mite.rain/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'App launch failed. Keep the phone connected for debugging.' }
Write-Host 'RAIN is installed and launched. Follow docs/DEMO_RUNBOOK.md for camera and motion checks.'
