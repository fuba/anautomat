param([ValidateSet('','arm64-v8a','armeabi-v7a','x86_64','x86')][string]$OnlyAbi='')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot/go-environment.ps1"
$ndk=Join-Path $sdk 'ndk/29.0.14206865'
$clang=Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin'
if(!(Test-Path "$clang/clang.exe")){throw 'Install Android NDK 29.0.14206865. The build never downloads SDK or NDK packages.'}
$manifest=Get-Content "$ndk/source.properties" -Raw
if($manifest -notmatch 'Pkg.Revision\s*=\s*29\.0\.14206865'){throw 'Unexpected Android NDK revision'}
$inputs=@(Get-ChildItem "$root/sandbox" -Recurse -File | Where-Object {$_.Extension -in '.go','.c','.mod','.sum'} | Sort-Object FullName)
$fingerprint=($inputs | ForEach-Object {($_.FullName.Substring($root.Length))+':'+(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}) -join "`n"
$fingerprint+="`n"+(Get-FileHash "$PSScriptRoot/build-native.ps1").Hash+':'+(Get-FileHash "$root/security/dependencies.lock.json").Hash+':'+$manifest
$key=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($fingerprint))).ToLower()
$output=Join-Path $root "build/native/$key"
New-Item -ItemType Directory -Force $output | Out-Null
$old=Get-Location
$saved=@{};foreach($name in 'GOOS','GOARCH','GOARM','CGO_ENABLED','CC','CGO_CFLAGS','CGO_LDFLAGS'){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try {
 Set-Location "$root/sandbox"
 $env:GOOS='android';$env:CGO_ENABLED='1';$env:GOARM='7'
 $env:CGO_CFLAGS='-O2 -Wall -Wextra -Werror -fstack-protector-strong -D_FORTIFY_SOURCE=2'
 $env:CGO_LDFLAGS='-Wl,-z,relro,-z,now,-z,noexecstack,-z,max-page-size=16384'
 foreach($target in @(@('arm64','aarch64-linux-android','arm64-v8a'),@('amd64','x86_64-linux-android','x86_64'),@('arm','armv7a-linux-androideabi','armeabi-v7a'),@('386','i686-linux-android','x86'))){
  if($OnlyAbi -and $target[2] -ne $OnlyAbi){continue}
  $env:GOARCH=$target[0];$env:CC=Join-Path $clang ($target[1]+'30-clang.cmd')
  $folder=Join-Path $output ('lib/'+$target[2]);New-Item -ItemType Directory -Force $folder | Out-Null
  $library=Join-Path $folder 'libstarlark.so';$sumFile=$library+'.sha256'
  if(!((Test-Path $library) -and (Test-Path $sumFile) -and (Get-FileHash $library -Algorithm SHA256).Hash -eq (Get-Content $sumFile -Raw).Trim())){
   & $go build -trimpath -buildvcs=false -ldflags='-s -w' -buildmode=c-shared -o $library ./jni
   if($LASTEXITCODE -ne 0){throw "Starlark native build failed for $($target[2])"}
   (Get-FileHash $library -Algorithm SHA256).Hash | Set-Content $sumFile
  }
  & $python "$PSScriptRoot/check-native.py" $library $target[2]
  if($LASTEXITCODE -ne 0){throw 'Native ELF hardening check failed'}
 }
 $output | Set-Content "$root/build/current-native.txt"
} finally {
 Set-Location $old
 foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
Write-Output "Starlark native libraries: $output"
