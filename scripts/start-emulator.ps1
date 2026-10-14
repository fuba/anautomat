$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$sdk="$env:LOCALAPPDATA\Android\Sdk"
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
if (!(Test-Path "$root\build\avd\config.ini")) {
 'no' | & "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n AndroidUse_Test -k 'system-images;android-36.1;google_apis_playstore;x86_64' -p "$root\build\avd"
 if ($LASTEXITCODE -ne 0) { throw 'AVD creation failed' }
 $config=Get-Content "$root\build\avd\config.ini"
 $config=$config -replace 'hw.lcd.width=320','hw.lcd.width=900' -replace 'hw.lcd.height=640','hw.lcd.height=1600' -replace 'hw.lcd.density=160','hw.lcd.density=320' -replace 'disk.dataPartition.size=6G','disk.dataPartition.size=10G'
 Set-Content "$root\build\avd\config.ini" $config
}
Start-Process "$sdk\emulator\emulator.exe" -ArgumentList '-avd AndroidUse_Test -port 5556 -no-snapshot -gpu auto' -WindowStyle Hidden
Write-Output 'Started the dedicated emulator-5556. Run install.ps1 after it has booted.'
