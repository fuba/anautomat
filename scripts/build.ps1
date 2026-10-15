param([switch]$Release,[string]$Version="0.1.6",[int]$VersionCode=1006)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot/environment.ps1"
$bt = "$sdk\build-tools\36.0.0"
$android = "$sdk\platforms\android-36\android.jar"
if (!(Test-Path $android) -or !(Test-Path "$bt\aapt2.exe")) { throw "Install Android SDK platform 36 and build-tools 36.0.0. The build never downloads SDK packages." }
function Run([string]$tool, [string[]]$arguments) { & $tool @arguments; if ($LASTEXITCODE -ne 0) { throw "$tool failed: $LASTEXITCODE" } }
Set-Location $root
& "$PSScriptRoot\sync-icon.ps1"
New-Item -ItemType Directory -Force build,build\classes,build\dex | Out-Null
Run "$bt\aapt2.exe" @('compile','--dir','app/res','-o','build/resources.zip')
Run "$bt\aapt2.exe" @('link','-o','build/base.apk','--manifest','app/AndroidManifest.xml','-I',$android,'build/resources.zip','--version-name',$Version,'--version-code',"$VersionCode")
$sources = @(Get-ChildItem app\src -Recurse -Filter *.java | ForEach-Object FullName)
Run "$jdk\bin\javac.exe" (@('-encoding','UTF-8','-source','11','-target','11','-classpath',$android,'-d','build/classes') + $sources)
Run "$jdk\bin\jar.exe" @('cf','build/classes.jar','-C','build/classes','.')
Run "$bt\d8.bat" @('--lib',$android,'--min-api','30','--output','build/dex','build/classes.jar')
Copy-Item build\base.apk build\unsigned.apk -Force
Run "$jdk\bin\jar.exe" @('uf','build/unsigned.apk','-C','build/dex','classes.dex')
Run "$bt\zipalign.exe" @('-f','4','build/unsigned.apk','build/aligned.apk')
if ($Version -notmatch '^\d+\.\d+\.\d+$' -or $VersionCode -lt 1) { throw 'Invalid APK version.' }
if ($Release) {
 if (!$env:ANDROID_KEYSTORE_PATH -or !$env:ANDROID_KEYSTORE_PASSWORD -or !$env:ANDROID_KEY_ALIAS) { throw 'Release signing secrets are required. Debug signing is never used for releases.' }
 Run "$bt\apksigner.bat" @('sign','--ks',$env:ANDROID_KEYSTORE_PATH,'--ks-key-alias',$env:ANDROID_KEY_ALIAS,'--ks-pass','env:ANDROID_KEYSTORE_PASSWORD','--key-pass','env:ANDROID_KEYSTORE_PASSWORD','--out','build/anautomat.apk','build/aligned.apk')
} else {
 if (!(Test-Path build\debug.keystore)) { Run "$jdk\bin\keytool.exe" @('-genkeypair','-keystore','build/debug.keystore','-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=anautomat Debug','-keyalg','RSA','-validity','10000') }
 Run "$bt\apksigner.bat" @('sign','--ks','build/debug.keystore','--ks-pass','pass:android','--out','build/anautomat.apk','build/aligned.apk')
}
$badging = & "$bt\aapt2.exe" dump badging build/anautomat.apk
if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -notmatch ("versionCode='"+[regex]::Escape("$VersionCode")+"' versionName='"+[regex]::Escape($Version)+"'")) { throw 'Built APK version does not match requested release metadata.' }
Run "$bt\apksigner.bat" @('verify','--verbose','--print-certs','build/anautomat.apk')
(Get-FileHash build/anautomat.apk -Algorithm SHA256).Hash.ToLower()+'  anautomat.apk' | Set-Content build/SHA256SUMS -Encoding ascii
Write-Output "APK: $root\build\anautomat.apk"
