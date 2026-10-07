param([switch]$Install, [switch]$CoreOnly, [switch]$PhoneTests)
$ErrorActionPreference = 'Stop'
$rainRoot = Split-Path $PSScriptRoot -Parent
$rainTools = Join-Path $rainRoot '.tools'
$rainJdk = Get-ChildItem -LiteralPath $rainTools -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($rainJdk) { $env:JAVA_HOME = $rainJdk.FullName }
if (!$env:JAVA_HOME) { throw 'Run scripts/bootstrap.ps1 first, or configure JAVA_HOME with JDK 17.' }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = Join-Path $rainTools 'gradle-cache'
if (Test-Path "$rainTools/android-sdk") { $env:ANDROID_HOME = "$rainTools/android-sdk" }
$gradle = "$rainTools/gradle-8.13/bin/gradle.bat"
if (!(Test-Path $gradle)) { $gradle = "$rainRoot/android/gradlew.bat" }
Push-Location "$rainRoot/android"
try {
    if ($CoreOnly) { & $gradle ':core:test' '--console=plain' }
    else { & $gradle ':core:test' ':app:assembleDebug' ':app:lintDebug' '--console=plain' }
    if ($LASTEXITCODE -ne 0) { throw 'Build or tests failed; see errors above.' }
    if ($PhoneTests) {
        & $gradle ':app:connectedDebugAndroidTest' '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw 'On-phone integration tests failed; inspect app/build/reports/androidTests/connected.' }
    }
    if (!$CoreOnly) {
        New-Item -ItemType Directory -Force "$rainRoot/output" | Out-Null
        Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination "$rainRoot/output/RAIN-demo.apk" -Force
        $apkHash = (Get-FileHash -LiteralPath "$rainRoot/output/RAIN-demo.apk" -Algorithm SHA256).Hash.ToLowerInvariant()
        "$apkHash  RAIN-demo.apk" | Set-Content -LiteralPath "$rainRoot/output/RAIN-demo.apk.sha256" -Encoding utf8
        Write-Host "Verified build copied to $rainRoot/output/RAIN-demo.apk"
    }
    if ($Install) {
        & "$env:ANDROID_HOME/platform-tools/adb.exe" install -r 'app/build/outputs/apk/debug/app-debug.apk'
        if ($LASTEXITCODE -ne 0) { throw 'Connect an Android phone with USB debugging enabled, then retry -Install.' }
        & "$env:ANDROID_HOME/platform-tools/adb.exe" shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n 'in.mite.rain/.MainActivity'
    }
} finally { Pop-Location }
