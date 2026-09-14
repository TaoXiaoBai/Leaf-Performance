[CmdletBinding()]
param(
    [ValidateRange(0, [int]::MaxValue)] [int]$BuildNumber = 0,
    [string]$JavaHome = 'C:\Program Files\Zulu\zulu-25'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradle = Join-Path $repo 'gradlew.bat'
$java = Join-Path $JavaHome 'bin\java.exe'

if (-not (Test-Path -LiteralPath $java)) {
    throw "Java executable not found: $java"
}

if ($BuildNumber -eq 0) {
    $count = (& git -C $repo rev-list --count HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $count -notmatch '^\d+$') {
        throw 'Could not derive a numeric build number from the Git commit count.'
    }
    $BuildNumber = [int]$count
}

$previousBuildNumber = [Environment]::GetEnvironmentVariable('BUILD_NUMBER', 'Process')
$previousJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')

try {
    $env:BUILD_NUMBER = $BuildNumber.ToString([Globalization.CultureInfo]::InvariantCulture)
    $env:JAVA_HOME = $JavaHome

    & $gradle 'leaf-server:createPaperclipJar' '--stacktrace' '--no-daemon'
    if ($LASTEXITCODE -ne 0) {
        throw "Paperclip build failed with exit code $LASTEXITCODE."
    }

    $artifact = Join-Path $repo "leaf-server\build\libs\leaf-paperclip-26.2.build.$BuildNumber-alpha.jar"
    if (-not (Test-Path -LiteralPath $artifact)) {
        throw "Expected Paperclip artifact was not created: $artifact"
    }

    Get-Item -LiteralPath $artifact
}
finally {
    if ($null -eq $previousBuildNumber) {
        Remove-Item Env:BUILD_NUMBER -ErrorAction SilentlyContinue
    } else {
        $env:BUILD_NUMBER = $previousBuildNumber
    }

    if ($null -eq $previousJavaHome) {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_HOME = $previousJavaHome
    }
}
