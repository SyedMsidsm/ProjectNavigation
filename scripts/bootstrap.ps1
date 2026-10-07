param([switch]$SkipAndroid, [switch]$AcceptSdkLicenses)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$rainRoot = Split-Path $PSScriptRoot -Parent
$rainTools = Join-Path $rainRoot '.tools'
New-Item -ItemType Directory -Force $rainTools | Out-Null
function Fetch-Verified($Url, $Path, $Checksum, $Algorithm = 'SHA256') {
    if ($Checksum -is [byte[]]) { $Checksum = [Text.Encoding]::UTF8.GetString($Checksum) }
    if (!(Test-Path -LiteralPath $Path)) { Invoke-WebRequest -Uri $Url -OutFile $Path }
    if ((Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash.ToLowerInvariant() -ne $Checksum.Trim().ToLowerInvariant()) {
        throw "Checksum mismatch for $Path. Remove that archive and retry."
    }
}
$rainJdk = Get-ChildItem -LiteralPath $rainTools -Directory -Filter 'jdk-*' | Select-Object -First 1
if (!$rainJdk) {
    Write-Host 'Downloading a verified Eclipse Temurin JDK 17...'
    $release = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = $release[0].binary.package
    $archive = Join-Path $rainTools 'jdk.zip'
    Fetch-Verified $package.link $archive $package.checksum
    Expand-Archive -LiteralPath $archive -DestinationPath $rainTools -Force
    $rainJdk = Get-ChildItem -LiteralPath $rainTools -Directory -Filter 'jdk-*' | Select-Object -First 1
}
$env:JAVA_HOME = $rainJdk.FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = Join-Path $rainTools 'gradle-cache'
if (!(Test-Path "$rainTools/gradle-8.13/bin/gradle.bat")) {
    Write-Host 'Downloading verified Gradle 8.13...'
    $checksum = (Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256').Content
    Fetch-Verified 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' "$rainTools/gradle.zip" $checksum
    Expand-Archive -LiteralPath "$rainTools/gradle.zip" -DestinationPath $rainTools -Force
}
if (!$SkipAndroid) {
    $env:ANDROID_HOME = Join-Path $rainTools 'android-sdk'
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    New-Item -ItemType Directory -Force $env:ANDROID_HOME | Out-Null
    if (!(Test-Path "$env:ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager.bat")) {
        Write-Host 'Downloading verified Android command-line tools...'
        [xml]$manifest = (Invoke-WebRequest 'https://dl.google.com/android/repository/repository2-1.xml').Content
        $entry = $manifest.SelectNodes("//*[local-name()='remotePackage']") | Where-Object { $_.path -eq 'cmdline-tools;19.0' } | Select-Object -First 1
        $archive = $entry.archives.archive | Where-Object { $_.'host-os' -eq 'windows' } | Select-Object -First 1
        if (!$archive) { throw 'Official Android tools archive not found in repository manifest.' }
        Fetch-Verified ("https://dl.google.com/android/repository/" + $archive.complete.url) "$rainTools/android-commandline.zip" ([string]$archive.complete.checksum) 'SHA1'
        $unpack = Join-Path $rainTools 'commandline-unpack'
        Expand-Archive -LiteralPath "$rainTools/android-commandline.zip" -DestinationPath $unpack -Force
        New-Item -ItemType Directory -Force "$env:ANDROID_HOME/cmdline-tools/19.0" | Out-Null
        Copy-Item -Path "$unpack/cmdline-tools/*" -Destination "$env:ANDROID_HOME/cmdline-tools/19.0" -Recurse -Force
    }
    $sdkmanager = "$env:ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager.bat"
    Write-Host 'Android SDK setup: review/accept the SDK licences when prompted.'
    if ($AcceptSdkLicenses) { 1..30 | ForEach-Object { 'y' } | & $sdkmanager "--sdk_root=$env:ANDROID_HOME" --licenses }
    else { & $sdkmanager "--sdk_root=$env:ANDROID_HOME" --licenses }
    if ($LASTEXITCODE -ne 0) { throw 'SDK license setup failed.' }
    & $sdkmanager "--sdk_root=$env:ANDROID_HOME" 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'
    if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed.' }
    $sdkPath = $env:ANDROID_HOME.Replace('\', '/').Replace(':', '\:')
    [IO.File]::WriteAllText("$rainRoot/android/local.properties", "sdk.dir=$sdkPath`n", [Text.UTF8Encoding]::new($false))
}
Write-Host 'Toolchain ready. Run scripts/build.ps1 next.'
