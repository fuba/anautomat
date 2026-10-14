$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
[xml]$svg=Get-Content "$root\design\android-use-mark.svg"
$paths=$svg.SelectNodes('//*[local-name()="path"]')
$vector=@('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">')
foreach($path in $paths){$fillType=if($path.GetAttribute('fill-rule') -eq 'evenodd'){'evenOdd'}else{'nonZero'};$vector+="  <path android:fillColor=`"#FFFFFF`" android:fillType=`"$fillType`" android:pathData=`"$($path.GetAttribute('d'))`"/>"}
$vector+='</vector>'
New-Item -ItemType Directory -Force "$root\app\res\drawable" | Out-Null
Set-Content "$root\app\res\drawable\ic_agent_mark.xml" $vector -Encoding utf8
$standalone=Get-Content "$root\design\android-use-mark.svg" -Raw
$standalone=$standalone.Replace('anautomat — screen and action','anautomat app icon').Replace('  <g fill=', '  <rect width="108" height="108" rx="24" fill="#161616"/>' + "`n" + '  <g fill=')
Set-Content "$root\design\android-use-icon.svg" $standalone -Encoding utf8
