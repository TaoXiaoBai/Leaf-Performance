[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateSet('light','villager')] [string]$Scenario,
    [Parameter(Mandatory)] [string]$RunId,
    [switch]$Profile,
    [string]$JavaHome = 'C:\Program Files\Zulu\zulu-25',
    [string]$JarPath = '',
    [int]$WarmupSeconds = 120,
    [int]$MeasureSeconds = 60,
    [int]$SprintTicks = 1200
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $JarPath) { $JarPath = Join-Path $repo 'leaf-server\build\libs\leaf-paperclip-26.2.local-SNAPSHOT.jar' }
$JarPath = (Resolve-Path $JarPath).Path
$template = Join-Path $repo "run\perf\templates\$Scenario"
$runDir = Join-Path $repo "run\perf\runs\$RunId"
if (-not (Test-Path $template)) { throw "Missing untracked world template: $template (see docs/performance/round-1-baseline.md)" }
if (Test-Path $runDir) { throw "Refusing to overwrite $runDir" }
New-Item -ItemType Directory -Force (Split-Path $runDir) | Out-Null
Copy-Item $template $runDir -Recurse
Remove-Item (Join-Path $runDir 'logs') -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item $JarPath (Join-Path $runDir 'server.jar') -Force

$psi = [Diagnostics.ProcessStartInfo]::new()
$psi.FileName = Join-Path $JavaHome 'bin\java.exe'
foreach ($arg in @('-Xms2G','-Xmx2G','-XX:+AlwaysPreTouch','-jar','server.jar','nogui')) { $psi.ArgumentList.Add($arg) }
$psi.WorkingDirectory = $runDir
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$process = [Diagnostics.Process]::Start($psi)
$log = Join-Path $runDir 'logs\latest.log'
try {
    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
        if ((Test-Path $log) -and (Select-String $log -Pattern 'Done \(' -Quiet)) { break }
        Start-Sleep 2
    }
    if ($process.HasExited -or -not (Select-String $log -Pattern 'Done \(' -Quiet)) { throw 'Server did not reach ready state' }
    Start-Sleep $WarmupSeconds

    if ($Profile) {
        $jfr = (Join-Path $runDir 'profile.jfr').Replace('\','/')
        & (Join-Path $JavaHome 'bin\jcmd.exe') $process.Id JFR.start settings=profile duration="${MeasureSeconds}s" filename=$jfr |
            Set-Content -Encoding UTF8 (Join-Path $runDir 'jfr-start.txt')
        if ($LASTEXITCODE -ne 0) { throw "JFR.start failed: $LASTEXITCODE" }
    }

    $process.Refresh(); $cpuStart = $process.TotalProcessorTime.TotalSeconds; $wallStart = Get-Date
    $hostCpu = @(); $workingSet = @()
    $measureDeadline = $wallStart.AddSeconds($MeasureSeconds)
    $nextSample = $wallStart.AddSeconds(5)
    while ((Get-Date) -lt $measureDeadline) {
        $delay = $nextSample - (Get-Date)
        if ($delay.TotalMilliseconds -gt 0) { Start-Sleep -Milliseconds ([int]$delay.TotalMilliseconds) }
        $process.Refresh(); $workingSet += $process.WorkingSet64
        $counter = Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor -Filter "Name='_Total'"
        $hostCpu += [double]$counter.PercentProcessorTime
        $process.StandardInput.WriteLine('tick query')
        $nextSample = $nextSample.AddSeconds(5)
    }
    $process.Refresh(); $cpuEnd = $process.TotalProcessorTime.TotalSeconds; $wallEnd = Get-Date
    Start-Sleep 1 # allow the final query response to reach latest.log; outside the measured window

    $process.StandardInput.WriteLine("tick sprint $SprintTicks")
    $sprintDeadline = (Get-Date).AddSeconds(30); $sprintLine = $null
    do {
        Start-Sleep 1
        $sprintLine = Select-String $log -Pattern 'Sprint completed (with|in)' | Select-Object -Last 1
    } while (-not $sprintLine -and (Get-Date) -lt $sprintDeadline -and -not $process.HasExited)
    $process.StandardInput.WriteLine('stop')
    if (-not $process.WaitForExit(60000)) { throw 'Server stop timeout' }

    $elapsed = ($wallEnd - $wallStart).TotalSeconds
    [ordered]@{
        runId=$RunId; scenario=$Scenario; profile=[bool]$Profile
        commit=(git -C $repo rev-parse HEAD).Trim(); artifactSha256=(Get-FileHash $JarPath).Hash
        warmupSeconds=$WarmupSeconds; measureSeconds=$elapsed
        processCpuSeconds=$cpuEnd-$cpuStart; processCpuOneCorePercent=100*($cpuEnd-$cpuStart)/$elapsed
        hostCpuPercentSamples=$hostCpu; workingSetBytesSamples=$workingSet
        sprintTicks=$SprintTicks; sprintCompletionLog=if($sprintLine){$sprintLine.Line}else{$null}; exitCode=$process.ExitCode
    } | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $runDir 'summary.json')
}
finally {
    if (-not $process.HasExited) {
        try { $process.StandardInput.WriteLine('stop') } catch {}
        if (-not $process.WaitForExit(30000)) { $process.Kill($true) }
    }
}
