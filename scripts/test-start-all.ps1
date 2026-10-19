<#
.SYNOPSIS
  Verification procedure for scripts\start-all.ps1 and scripts\stop-all.ps1.

.DESCRIPTION
  Runs the full lifecycle in one PowerShell process and asserts each step:

    1. start-all.ps1 exits 0 and MongoDB / backend / frontend all answer
    2. the backend really is GymKhata (not just some other service on :8080)
    3. re-running start-all.ps1 is safe and creates no duplicate processes
    4. stop-all.ps1 -WhatIf changes nothing
    5. stop-all.ps1 stops the tracked processes and only those
    6. unrelated processes are untouched
    7. the ports are free again afterwards

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\test-start-all.ps1
#>
[CmdletBinding()]
param(
    # Seconds to allow for the backend to come up.
    [int]$BackendTimeoutSec = 120,
    # Seconds to allow for the frontend to come up.
    [int]$FrontendTimeoutSec = 90
)

$ErrorActionPreference = 'Continue'

$repoRoot = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $repoRoot 'logs'
$statePath = Join-Path $logDir 'gymkhata-processes.json'
$startScript = Join-Path $PSScriptRoot 'start-all.ps1'
$stopScript = Join-Path $PSScriptRoot 'stop-all.ps1'

. (Join-Path $PSScriptRoot 'process-tracking.ps1')

$script:Failures = 0
$script:Checks = 0

function Assert-True {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    $script:Checks++
    if ($Condition) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
    } else {
        $script:Failures++
        Write-Host "  FAIL  $Name $Detail" -ForegroundColor Red
    }
}

function Invoke-ScriptFile {
    <#
      Runs a script to completion and returns its exit code.

      Start-Process is used instead of '& powershell ... *> $log' on purpose:
      '&' with a redirected stream waits for EOF on that handle, and the
      services started by start-all.ps1 inherit and hold it open, which would
      hang the test. Wait-Process waits for the named process only (not its
      descendants), so the call returns as soon as the script itself exits.
    #>
    param(
        [string]$ScriptPath,
        [string[]]$Arguments = @(),
        [int]$TimeoutSec = 300
    )
    $token = [guid]::NewGuid().ToString('N')
    $out = Join-Path $env:TEMP "gymkhata-$token.log"
    $err = Join-Path $env:TEMP "gymkhata-$token.err"

    $proc = Start-Process -FilePath 'powershell' `
        -ArgumentList (@('-ExecutionPolicy', 'Bypass', '-NoProfile', '-File', $ScriptPath) + $Arguments) `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $out -RedirectStandardError $err `
        -WindowStyle Hidden -PassThru

    # Touching .Handle caches it with query rights, without which .ExitCode
    # stays empty for a process started by Start-Process in Windows PowerShell.
    $null = $proc.Handle

    Wait-Process -Id $proc.Id -Timeout $TimeoutSec -ErrorAction SilentlyContinue
    $exitCode = -1
    try {
        if ($proc.HasExited) {
            $exitCode = $proc.ExitCode
        } else {
            # the script overran its budget - do not leave it behind
            Write-Host "  (script $ScriptPath did not finish within ${TimeoutSec}s - terminating it)" -ForegroundColor Yellow
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        }
    } catch { }

    return [pscustomobject]@{ ExitCode = $exitCode; LogPath = $out; ErrPath = $err }
}

function Invoke-StartupScript {
    param([int]$TimeoutSec = 300)
    return Invoke-ScriptFile -ScriptPath $startScript -TimeoutSec $TimeoutSec -Arguments @(
        '-BackendTimeoutSec', "$BackendTimeoutSec",
        '-FrontendTimeoutSec', "$FrontendTimeoutSec")
}

function Invoke-StopScript {
    param([switch]$WhatIf)
    if ($WhatIf) {
        return Invoke-ScriptFile -ScriptPath $stopScript -TimeoutSec 120 -Arguments @('-WhatIf')
    }
    return Invoke-ScriptFile -ScriptPath $stopScript -TimeoutSec 120
}

function Get-GymKhataProcessIds {
    $ids = @()
    $state = Read-GymKhataState -StatePath $statePath
    foreach ($svc in $state.Services) {
        if (Test-TrackedProcess -ProcessId ([int]$svc.ProcessId) -ExpectedStartTime ([string]$svc.StartTime)) {
            $ids += [int]$svc.ProcessId
        }
    }
    return $ids
}

# Every process that belongs to the GymKhata tree: the tracked pids plus all of
# their descendants. Anything outside this set must survive a stop.
function Get-GymKhataTreeIds {
    $ids = @(Get-GymKhataProcessIds)
    $tree = @($ids)
    foreach ($id in $ids) {
        $tree += @(Get-DescendantProcessIds -ParentProcessId $id)
    }
    return @($tree | Sort-Object -Unique)
}

Write-Host ''
Write-Host 'GymKhata startup script verification' -ForegroundColor Cyan
Write-Host '=====================================' -ForegroundColor Cyan

# ---------------------------------------------------------------- start ----
Write-Host ''
Write-Host '[1] start-all.ps1 (cold start)' -ForegroundColor Cyan
$result = Invoke-StartupScript
Assert-True 'start-all.ps1 exits 0' ($result.ExitCode -eq 0) "(exit code $($result.ExitCode))"
if ($result.ExitCode -ne 0) {
    Write-Host '--- startup log ---' -ForegroundColor DarkGray
    Get-Content $result.LogPath -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
}

# ------------------------------------------------------------- readiness ---
Write-Host ''
Write-Host '[2] Services are reachable' -ForegroundColor Cyan
$health = Get-HttpStatus -Url 'http://localhost:8080/actuator/health' -TimeoutSec 5
Assert-True 'backend /actuator/health returns UP' ($health.Ok -and $health.Body -match '"status"\s*:\s*"UP"') "(status $($health.StatusCode))"

$identity = Get-HttpStatus -Url 'http://localhost:8080/api/v1/config/public' -TimeoutSec 5
Assert-True 'backend on :8080 really is GymKhata' ($identity.Ok -and $identity.Body -match '"name"\s*:\s*"GymKhata"')

$frontend = Get-HttpStatus -Url 'http://localhost:5173/' -TimeoutSec 5
Assert-True 'frontend on :5173 serves the app shell' ($frontend.Ok -and $frontend.Body -match 'id="root"')

$mongo = Get-HttpStatus -Url 'http://localhost:8080/api/v1/config/public' -TimeoutSec 5
Assert-True 'backend can reach MongoDB (API answers)' $mongo.Ok

# ----------------------------------------------------------------- rerun ---
Write-Host ''
Write-Host '[3] Re-running start-all.ps1 is safe' -ForegroundColor Cyan
$trackedBefore = Get-GymKhataProcessIds
$rerun = Invoke-StartupScript
Assert-True 'second run exits 0' ($rerun.ExitCode -eq 0) "(exit code $($rerun.ExitCode))"
$trackedAfter = Get-GymKhataProcessIds
Assert-True 'no duplicate tracked processes were created' ($trackedBefore.Count -eq $trackedAfter.Count) "(before $($trackedBefore.Count), after $($trackedAfter.Count))"

$rerunText = (Get-Content $rerun.LogPath -Raw -ErrorAction SilentlyContinue)
Assert-True 'second run reused the running backend' ($rerunText -match 'already running and healthy')
Assert-True 'second run reused the running frontend' ($rerunText -match 'already running')

# --------------------------------------------------------------- whatif ----
Write-Host ''
Write-Host '[4] stop-all.ps1 -WhatIf changes nothing' -ForegroundColor Cyan
$before = Get-GymKhataProcessIds
$dry = Invoke-StopScript -WhatIf
Assert-True 'stop-all.ps1 -WhatIf exits 0' ($dry.ExitCode -eq 0)
$afterDry = Get-GymKhataProcessIds
Assert-True 'no process was stopped in dry-run mode' ($before.Count -eq $afterDry.Count) "(before $($before.Count), after $($afterDry.Count))"

# ------------------------------------------------------------------ stop ---
Write-Host ''
Write-Host '[5] stop-all.ps1 stops the tracked processes' -ForegroundColor Cyan
# "unrelated" = every java/node process that is NOT part of the GymKhata tree.
# Only long-lived processes count: a short-lived helper that happens to exit
# during the test is not "killed by stop-all", it simply finished.
$tree = @(Get-GymKhataTreeIds)
$minAge = (Get-Date).AddSeconds(-60)
$unrelatedBefore = @(Get-CimInstance Win32_Process -Filter "Name='node.exe' OR Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $tree -notcontains [int]$_.ProcessId } |
    Where-Object {
        $started = $null
        try { $started = $_.CreationDate } catch { }
        ($null -eq $started) -or ($started -le $minAge)
    } |
    Select-Object -ExpandProperty ProcessId)
Write-Host "        (GymKhata tree: $($tree -join ', ') | unrelated long-lived: $($unrelatedBefore.Count))"

$stop = Invoke-StopScript
Assert-True 'stop-all.ps1 exits 0' ($stop.ExitCode -eq 0) "(exit code $($stop.ExitCode))"

$stillRunning = Get-GymKhataProcessIds
Assert-True 'no tracked process is left running' ($stillRunning.Count -eq 0) "(left: $($stillRunning -join ', '))"

$unrelatedAfter = @(Get-CimInstance Win32_Process -Filter "Name='node.exe' OR Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $tree -notcontains [int]$_.ProcessId } |
    Select-Object -ExpandProperty ProcessId)
$survivors = @($unrelatedBefore | Where-Object { $unrelatedAfter -contains $_ })
$lost = @($unrelatedBefore | Where-Object { $unrelatedAfter -notcontains $_ })
Write-Host "        unrelated before: $($unrelatedBefore -join ', ')"
Write-Host "        unrelated after : $($unrelatedAfter -join ', ')"
if ($lost.Count -gt 0) {
    foreach ($lostId in $lost) {
        $detail = ''
        try {
            $snap = Get-CimInstance Win32_Process -Filter "ProcessId=$lostId" -ErrorAction SilentlyContinue
            if ($snap) { $detail = $snap.CommandLine }
        } catch { }
        Write-Host "        LOST pid $lostId (was alive before the stop): $detail" -ForegroundColor Yellow
    }
}
Assert-True 'unrelated processes were not touched' ($survivors.Count -eq $unrelatedBefore.Count) `
    ("(unrelated before $($unrelatedBefore.Count), survived $($survivors.Count))")

$port8080 = Test-TcpPort -ComputerName 'localhost' -Port 8080 -TimeoutMs 1500
$port5173 = Test-TcpPort -ComputerName 'localhost' -Port 5173 -TimeoutMs 1500
Assert-True 'port 8080 is free again' (-not $port8080)
Assert-True 'port 5173 is free again' (-not $port5173)

$mongoStillUp = Test-TcpPort -ComputerName 'localhost' -Port 27017 -TimeoutMs 1500
Assert-True 'MongoDB was left running' $mongoStillUp

# ---------------------------------------------------------------- report ---
Write-Host ''
Write-Host '=====================================' -ForegroundColor Cyan
if ($script:Failures -eq 0) {
    Write-Host "ALL $($script:Checks) CHECKS PASSED" -ForegroundColor Green
    exit 0
} else {
    Write-Host "$($script:Failures) of $($script:Checks) CHECKS FAILED" -ForegroundColor Red
    exit 1
}
