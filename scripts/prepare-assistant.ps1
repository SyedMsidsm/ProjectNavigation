param([string]$ModelPath = (Join-Path $PSScriptRoot '../.tools/models/gemma3-1b-it-int4.litertlm'))
$ErrorActionPreference = 'Stop'
$rainRoot = Split-Path $PSScriptRoot -Parent
$rainAdb = Join-Path $rainRoot '.tools/android-sdk/platform-tools/adb.exe'
$rainModel = (Resolve-Path -LiteralPath $ModelPath).Path
# SHA256 of the user-downloaded, device-tested demo artifacts.
$rainExpected = (Get-FileHash -LiteralPath $rainModel -Algorithm SHA256).Hash.ToLowerInvariant()
$rainKnown = @(
    '1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be',
    '181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c'
)
if ($rainExpected -notin $rainKnown) {
    throw 'Model is not a device-tested demo artifact. Use the in-app import flow for other compatible models.'
}
& $rainAdb shell am force-stop in.mite.rain
& $rainAdb push $rainModel '/data/local/tmp/rain-destination.litertlm'
if ($LASTEXITCODE -ne 0) { throw 'Model transfer failed. Check the USB connection.' }
# The installed college debug build permits run-as. Write only RAIN's private model file.
& $rainAdb shell run-as in.mite.rain mkdir -p files
& $rainAdb shell run-as in.mite.rain cp /data/local/tmp/rain-destination.litertlm files/destination-model.litertlm
if ($LASTEXITCODE -ne 0) { throw 'Model could not be installed into RAIN. Use Local assistant > Import local model.' }
$rainDeviceHash = & $rainAdb shell run-as in.mite.rain sha256sum files/destination-model.litertlm
if ($LASTEXITCODE -ne 0 -or $rainDeviceHash -notmatch $rainExpected) { throw 'Phone model checksum did not match.' }
& $rainAdb shell rm /data/local/tmp/rain-destination.litertlm
Write-Output 'Local assistant model prepared and checksum verified. Run the device inference test before claiming successful AI operation.'
