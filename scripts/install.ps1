param([string]$Serial='emulator-5556')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s $Serial install -r "$root\build\anautomat.apk"
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed' }
& $adb -s $Serial shell am start -f 0x14000000 -n dev.androiduse/.MainActivity
