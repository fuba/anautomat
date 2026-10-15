$ErrorActionPreference='Stop'
$goRoot=Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot/environment.ps1"
& $python "$PSScriptRoot/ci-security.py" download --name go-sdk
if($LASTEXITCODE -ne 0){throw 'Verified Go SDK download failed'}
& $python "$PSScriptRoot/go-security.py" download
if($LASTEXITCODE -ne 0){throw 'Verified Go module mirror failed'}
$goPin=(Get-Content "$goRoot/security/dependencies.lock.json" -Raw | ConvertFrom-Json).downloads | Where-Object name -eq 'go-sdk'
$go=Join-Path $goRoot ("build/tools/go-sdk-"+$goPin.version+'-'+$goPin.sha256.Substring(0,16)+'/go/bin/go.exe')
& $python "$PSScriptRoot/go-security.py" patch-sdk (Split-Path (Split-Path $go))
if($LASTEXITCODE -ne 0){throw 'Pinned Go runtime patch failed'}
$env:GOTOOLCHAIN='local'
$env:GOPROXY=([Uri](Join-Path $goRoot 'build/go-proxy')).AbsoluteUri
$env:GOSUMDB='off'
$env:GONOPROXY='none'
$env:GONOSUMDB='none'
$env:GOPRIVATE=''
$env:GOVCS='*:off'
$env:GOENV='off'
$env:GOTELEMETRY='off'
$env:GOPATH=Join-Path $goRoot 'build/gopath'
$env:GOMODCACHE=Join-Path $goRoot 'build/gomodcache'
$env:GOCACHE=Join-Path $goRoot 'build/go-cache'
$env:GOFLAGS='-mod=readonly'
$env:PATH="$(Split-Path $go);$env:PATH"
if((& $go version) -notmatch '^go version go1\.27\.2 windows/amd64$'){throw 'Unexpected Go toolchain'}
