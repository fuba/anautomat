param([string]$Serial='emulator-5556',[switch]$AutomationOnly,[switch]$PrepareGoal,[switch]$RestoreGoal)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot/environment.ps1"
$bt="$sdk\build-tools\36.0.0"
$env:JAVA_HOME=$jdk
$env:PATH="$jdk\bin;$env:PATH"
function Run([string]$tool,[string[]]$arguments){& $tool @arguments;if($LASTEXITCODE -ne 0){throw "$tool failed"}}
Set-Location $root
$appClasses=(Get-Content build/current-classes.txt -Raw).Trim()
New-Item -ItemType Directory -Force build\device-tests\classes,build\device-tests\dex | Out-Null
$assetArgs=@();if(Test-Path build/update-test-assets){$assetArgs=@('-A','build/update-test-assets')}
Run "$bt\aapt2.exe" (@('link','-o','build/device-tests/base.apk','--manifest','tests/device/AndroidManifest.xml','-I',"$sdk/platforms/android-36/android.jar")+$assetArgs)
Run "$jdk\bin\javac.exe" @('-encoding','UTF-8','-source','11','-target','11','-cp',"$sdk/platforms/android-36/android.jar;$appClasses",'-d','build/device-tests/classes','tests/device/src/dev/androiduse/DeviceTests.java','tests/device/src/dev/androiduse/AutomationDeviceTests.java','tests/device/src/dev/androiduse/AutomationFixture.java','tests/device/src/dev/androiduse/AutomationGoalSetup.java')
Run "$jdk\bin\jar.exe" @('cf','build/device-tests/classes.jar','-C','build/device-tests/classes','.')
Run "$bt\d8.bat" @('--lib',"$sdk/platforms/android-36/android.jar",'--min-api','30','--output','build/device-tests/dex','build/device-tests/classes.jar')
Run "$jdk\bin\jar.exe" @('uf','build/device-tests/base.apk','-C','build/device-tests/dex','classes.dex')
Run "$bt\zipalign.exe" @('-f','4','build/device-tests/base.apk','build/device-tests/aligned.apk')
Run "$bt\apksigner.bat" @('sign','--ks','build/debug.keystore','--ks-pass','pass:android','--out','build/device-tests/tests.apk','build/device-tests/aligned.apk')
Run "$sdk\platform-tools\adb.exe" @('-s',$Serial,'install','-r','build/device-tests/tests.apk')
if($PrepareGoal -or $RestoreGoal){
 & "$sdk\platform-tools\adb.exe" -s $Serial shell am instrument -w -e restore $RestoreGoal.ToString().ToLower() dev.androiduse.tests/dev.androiduse.AutomationGoalSetup
 if($LASTEXITCODE -ne 0){throw 'Goal setup failed'}
 return
}
if(!$AutomationOnly){
 $testOutput=& "$sdk\platform-tools\adb.exe" -s $Serial shell am instrument -w dev.androiduse.tests/dev.androiduse.DeviceTests
 $testOutput
 if ($LASTEXITCODE -ne 0 -or ($testOutput -join "`n") -notmatch 'PASS: Android Keystore') { throw 'Device tests failed' }
}
$automationOutput=& "$sdk\platform-tools\adb.exe" -s $Serial shell am instrument -w dev.androiduse.tests/dev.androiduse.AutomationDeviceTests
$automationOutput
if ($LASTEXITCODE -ne 0 -or ($automationOutput -join "`n") -notmatch 'PASS: isolated UID') { throw 'Automation device tests failed' }
