$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot/environment.ps1"
Set-Location $root
New-Item -ItemType Directory -Force build\test-deps,build\test-classes | Out-Null
& $python scripts/ci-security.py download --name json --name janino --name commons-compiler
if ($LASTEXITCODE -ne 0) { throw 'Verified test dependency download failed' }
Copy-Item build/tools/json.jar build/test-deps/json.jar -Force
& "$jdk\bin\javac.exe" -encoding UTF-8 -cp 'build/test-deps/json.jar;build/tools/janino.jar;build/tools/commons-compiler.jar' -d build\test-classes app\src\dev\androiduse\AutomationJson.java app\src\dev\androiduse\JavaBytecodePolicy.java app\src\dev\androiduse\SandboxApi.java app\src\dev\androiduse\SandboxEngine.java app\src\dev\androiduse\MacroPlan.java app\src\dev\androiduse\MacroExecution.java app\src\dev\androiduse\UpdatePolicy.java app\src\dev\androiduse\TickerState.java app\src\dev\androiduse\GoalControlState.java app\src\dev\androiduse\AuthReturnPage.java app\src\dev\androiduse\InferenceRetry.java app\src\dev\androiduse\OpenAiHttp.java app\src\dev\androiduse\Protocol.java app\src\dev\androiduse\UsageMeter.java app\src\dev\androiduse\StepBudget.java app\src\dev\androiduse\LogFormat.java tests\ProtocolTest.java tests\AutomationTest.java
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$jdk\bin\java.exe" -cp 'build/test-classes;build/test-deps/json.jar;build/tools/janino.jar;build/tools/commons-compiler.jar' dev.androiduse.ProtocolTest
if ($LASTEXITCODE -ne 0) { throw 'Protocol tests failed' }

& "$jdk\bin\java.exe" -Xmx128m -cp 'build/test-classes;build/test-deps/json.jar;build/tools/janino.jar;build/tools/commons-compiler.jar' dev.androiduse.AutomationTest
if ($LASTEXITCODE -ne 0) { throw 'Automation security tests failed' }
