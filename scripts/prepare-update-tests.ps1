# Optional real-APK fixtures for test-device.ps1. No fixture is installed.
$ErrorActionPreference='Stop'
. "$PSScriptRoot/environment.ps1"
$env:JAVA_HOME=$jdk
& "$PSScriptRoot/build.ps1" -Version 1.2.1 -VersionCode 1002001
New-Item -ItemType Directory -Force build/update-test-assets | Out-Null
Copy-Item build/anautomat.apk build/update-test-assets/same-signer.apk -Force
if(!(Test-Path build/update-test-wrong.p12)){
 & "$jdk/bin/keytool.exe" -genkeypair -keystore build/update-test-wrong.p12 -storepass android -keypass android -alias test -keyalg RSA -keysize 2048 -validity 30 -dname 'CN=Updater rejection test'
 if($LASTEXITCODE -ne 0){throw 'Test signing key creation failed'}
}
& "$sdk/build-tools/36.0.0/apksigner.bat" sign --ks build/update-test-wrong.p12 --ks-pass pass:android --out build/update-test-assets/wrong-signer.apk build/update-test-assets/same-signer.apk
if($LASTEXITCODE -ne 0){throw 'Test fixture signing failed'}
& "$PSScriptRoot/build.ps1"
