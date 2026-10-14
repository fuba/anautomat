$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$studioJdk = 'C:\Program Files\Android\Android Studio\jbr'
$jdk = if ($env:ANAUTOMAT_JAVA_HOME) { $env:ANAUTOMAT_JAVA_HOME } elseif (Test-Path "$studioJdk\bin\javac.exe") { $studioJdk } else { $env:JAVA_HOME }
if (!$jdk -or !(Test-Path "$jdk\bin\javac.exe")) { throw 'Install a JDK and set JAVA_HOME.' }
$python = if ($env:ANAUTOMAT_PYTHON) { $env:ANAUTOMAT_PYTHON } else { (Get-Command python -ErrorAction Stop).Source }
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"
