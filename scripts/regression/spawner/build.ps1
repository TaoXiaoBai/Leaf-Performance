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
if (-not $OutputJar) { $OutputJar = Join-Path $root 'build\SpawnerQuery-1.0.0-local.jar' }
$OutputJar = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputJar)
$classes = Join-Path $root ('build\classes-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = @(Get-ChildItem (Join-Path $root 'src\main\java') -Filter *.java -File -Recurse |
    ForEach-Object { $_.FullName })
$libraryJars = @(Get-ChildItem (Resolve-Path $LibrariesDir).Path -Filter *.jar -File -Recurse |
    ForEach-Object { $_.FullName })
if (-not $libraryJars.Count) { throw 'LibrariesDir contains no jars' }
$classpath = (@((Resolve-Path $ApiJar).Path, (Resolve-Path $ServerJar).Path) + $libraryJars) -join ';'
# Keep the full runtime classpath below Windows process command-line limits.
$arguments = @('--release', '25', '-encoding', 'UTF-8', '-classpath', $classpath, '-d', $classes) + $sources
$argfile = Join-Path $classes 'javac.args'
$arguments | ForEach-Object { '"' + $_.Replace('\', '/').Replace('"', '\"') + '"' } |
    Set-Content -Path $argfile -Encoding utf8NoBOM
& (Join-Path $JavaHome 'bin\javac.exe') "@$argfile"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item (Join-Path $root 'src\main\resources\plugin.yml') (Join-Path $classes 'plugin.yml')
New-Item -ItemType Directory -Force (Split-Path $OutputJar) | Out-Null
& (Join-Path $JavaHome 'bin\jar.exe') --create --file $OutputJar -C $classes local -C $classes plugin.yml
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Get-FileHash -Algorithm SHA256 $OutputJar
