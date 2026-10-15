[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ApiJar,
    [Parameter(Mandatory)][string]$ServerJar,
    [Parameter(Mandatory)][string]$LibrariesDir,
    [string]$JavaHome = 'C:\Program Files\Zulu\zulu-25',
    [string]$OutputJar = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
if (-not $OutputJar) { $OutputJar = Join-Path $root 'build\BrainCountRegression-1.0.0-local.jar' }
$api = (Resolve-Path $ApiJar).Path
$server = (Resolve-Path $ServerJar).Path
$libraries = (Resolve-Path $LibrariesDir).Path
$javac = Join-Path $JavaHome 'bin\javac.exe'
$jar = Join-Path $JavaHome 'bin\jar.exe'
if (-not (Test-Path $javac -PathType Leaf) -or -not (Test-Path $jar -PathType Leaf)) {
    throw 'JavaHome must contain a Java 25 JDK (javac.exe and jar.exe)'
}
# A unique classes directory prevents stale nested classes from entering repeat builds.
$classes = Join-Path $root ('build\classes-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = @(Get-ChildItem (Join-Path $root 'src\main\java') -Filter *.java -File -Recurse | ForEach-Object { $_.FullName })
$libraryJars = @(Get-ChildItem $libraries -Filter *.jar -File -Recurse | ForEach-Object { $_.FullName })
if (-not $sources) { throw 'No harness sources found' }
if (-not $libraryJars) { throw 'LibrariesDir contains no jars' }
$classpath = (@($api, $server) + $libraryJars) -join ';'
# javac's argfile also avoids Windows command-line length limits for a full server library tree.
$arguments = @('--release', '25', '-encoding', 'UTF-8', '-classpath', $classpath, '-d', $classes) + $sources
$argfile = Join-Path $classes 'javac.args'
$arguments | ForEach-Object { '"' + $_.Replace('\', '/').Replace('"', '\"') + '"' } |
    Set-Content -Path $argfile -Encoding utf8
& $javac "@$argfile"
if ($LASTEXITCODE -ne 0) { throw "javac failed: $LASTEXITCODE" }
# Keep compiler arguments out of the plugin artifact.
Copy-Item (Join-Path $root 'src\main\resources\plugin.yml') (Join-Path $classes 'plugin.yml')
$OutputJar = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputJar)
New-Item -ItemType Directory -Force (Split-Path $OutputJar -Parent) | Out-Null
& $jar --create --file $OutputJar -C $classes local -C $classes plugin.yml
if ($LASTEXITCODE -ne 0) { throw "jar failed: $LASTEXITCODE" }
Write-Host 'Build inputs (record with server launch log):'
Get-FileHash -Algorithm SHA256 $api, $server, $OutputJar
