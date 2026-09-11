[CmdletBinding()]
param(
 [Parameter(Mandatory)][string]$ApiJar,
 [Parameter(Mandatory)][string]$ServerJar,
 [Parameter(Mandatory)][string]$LibrariesDir,
 [string]$JavaHome='C:\Program Files\Zulu\zulu-25',
 [string]$OutputJar=''
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
if(-not $OutputJar){$OutputJar=Join-Path $root 'build\TickCollector-1.0.0-local.jar'}
$classes=Join-Path $root 'build\classes'
New-Item -ItemType Directory -Force $classes|Out-Null
$sources=Get-ChildItem (Join-Path $root 'src\main\java') -Filter *.java -Recurse|ForEach-Object{$_.FullName}
$libraryJars=Get-ChildItem (Resolve-Path $LibrariesDir).Path -Filter *.jar -File -Recurse | ForEach-Object { $_.FullName }
if(-not $libraryJars){throw 'LibrariesDir contains no jars'}
$classpath=(@((Resolve-Path $ApiJar).Path,(Resolve-Path $ServerJar).Path)+$libraryJars)-join';'
& (Join-Path $JavaHome 'bin\javac.exe') --release 21 -encoding UTF-8 -classpath $classpath -d $classes $sources
if($LASTEXITCODE-ne 0){exit $LASTEXITCODE}
$resource=Join-Path $root 'src\main\resources\plugin.yml'
Copy-Item $resource (Join-Path $classes 'plugin.yml') -Force
New-Item -ItemType Directory -Force (Split-Path $OutputJar)|Out-Null
if(Test-Path $OutputJar){Remove-Item $OutputJar -Force}
& (Join-Path $JavaHome 'bin\jar.exe') --create --file $OutputJar -C $classes .
if($LASTEXITCODE-ne 0){exit $LASTEXITCODE}
Get-FileHash -Algorithm SHA256 $OutputJar
