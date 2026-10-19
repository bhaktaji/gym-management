<#
.SYNOPSIS
  Stops the GymKhata services that were started by scripts\start-all.ps1.

.DESCRIPTION
  Only processes recorded in logs\gymkhata-processes.json are stopped, and
  only when their pid is still the same process that was started (the recorded
  start time is compared, so a recycled pid is never killed).

  Services that were already running before start-all.ps1 was used are NOT
  tracked and are therefore left alone.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1
#>
[CmdletBinding()]
param(
    # Show what would be stopped without stopping anything.
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $repoRoot 'logs'
$statePath = Join-Path $logDir 'gymkhata-processes.json'

. (Join-Path $PSScriptRoot 'process-tracking.ps1')

if (-not (Test-Path -LiteralPath $statePath)) {
    Write-Warn "No process state file found at $statePath"
    Write-Detail 'Nothing was started by scripts\start-all.ps1 on this machine, so nothing to stop.'
    exit 0
}

$state = Read-GymKhataState -StatePath $statePath
$services = @($state.Services)

if ($services.Count -eq 0) {
    Write-Warn 'No tracked processes to stop.'
    exit 0
}

$stopped = @()
$skipped = @()

foreach ($svc in $services) {
    $name = [string]$svc.Name
    $procId = [int]$svc.ProcessId
    $startTime = [string]$svc.StartTime

    if (-not (Test-TrackedProcess -ProcessId $procId -ExpectedStartTime $startTime)) {
        Write-Warn "$name (pid $procId) is not running any more - cleaning up the stale entry."
        $skipped += $name
        if (-not $WhatIf) {
            Remove-GymKhataService -StatePath $statePath -Name $name
        }
        continue
    }

    if ($WhatIf) {
        Write-Step "$name (pid $procId) would be stopped." 'Yellow'
        $stopped += $name
        continue
    }

    Write-Step "Stopping $name (pid $procId)..."
    Stop-TrackedProcess -ProcessId $procId -ExpectedStartTime $startTime | Out-Null
    $stopped += $name
    Remove-GymKhataService -StatePath $statePath -Name $name
}

if ($WhatIf) {
    Write-Step ''
    Write-Warn "Dry run: $($stopped -join ', ')"
    exit 0
}

Write-Step ''
if ($stopped.Count -gt 0) {
    Write-Ok "Stopped: $($stopped -join ', ')"
} else {
    Write-Warn 'Nothing needed stopping.'
}
if ($skipped.Count -gt 0) {
    Write-Detail "stale entries cleaned: $($skipped -join ', ')"
}
Write-Detail 'MongoDB was not touched (the script never stops the database service).'
exit 0

