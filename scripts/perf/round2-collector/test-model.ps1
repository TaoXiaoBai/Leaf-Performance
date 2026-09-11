[CmdletBinding()]
param([string]$JavaHome='C:\Program Files\Zulu\zulu-25')
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$out=Join-Path $root 'build\model-test'
New-Item -ItemType Directory -Force $out|Out-Null
$state=Join-Path $root 'src\main\java\local\leafperf\tickcollector\TickWindowState.java'
$test=Join-Path $root 'src\test\java\local\leafperf\tickcollector\TickWindowStateTest.java'
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -d $out $state $test
if($LASTEXITCODE-ne 0){exit $LASTEXITCODE}
& (Join-Path $JavaHome 'bin\java.exe') -ea -cp $out local.leafperf.tickcollector.TickWindowStateTest
exit $LASTEXITCODE
