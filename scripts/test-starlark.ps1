$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot/go-environment.ps1"
$old=Get-Location
$saved=@{};foreach($name in 'CGO_ENABLED','GOOS','GOARCH'){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try {
 Set-Location "$root/sandbox"
 $env:CGO_ENABLED='0';$env:GOOS='windows';$env:GOARCH='amd64'
 & $go test -count=1 -timeout=60s .
 if($LASTEXITCODE -ne 0){throw 'Starlark tests failed'}
 & $go vet .
 if($LASTEXITCODE -ne 0){throw 'Go vet failed'}
 & $go test -run '^$' -fuzz '^FuzzJSONConversion$' -fuzztime=10s -parallel=2 -timeout=45s .
 if($LASTEXITCODE -ne 0){throw 'Starlark JSON fuzz checks failed'}
} finally {
 Set-Location $old
 foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
