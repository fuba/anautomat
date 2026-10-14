$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot/environment.ps1"
Set-Location $root
New-Item -ItemType Directory -Force build\test-deps,build\test-classes | Out-Null
& $python scripts/ci-security.py download --name json
if ($LASTEXITCODE -ne 0) { throw 'Verified test dependency download failed' }
Copy-Item build/tools/json.jar build/test-deps/json.jar -Force
& "$jdk\bin\javac.exe" -encoding UTF-8 -cp build\test-deps\json.jar -d build\test-classes app\src\dev\androiduse\Protocol.java app\src\dev\androiduse\UsageMeter.java app\src\dev\androiduse\StepBudget.java app\src\dev\androiduse\LogFormat.java tests\ProtocolTest.java
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$jdk\bin\java.exe" -cp 'build/test-classes;build/test-deps/json.jar' dev.androiduse.ProtocolTest
if ($LASTEXITCODE -ne 0) { throw 'Protocol tests failed' }
