$ErrorActionPreference='Stop'
# Trace the actual Android bridge as well as the interpreter integration.
& "$PSScriptRoot/build-native.ps1" -OnlyAbi arm64-v8a
