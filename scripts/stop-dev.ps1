<#
.SYNOPSIS
  Stops the background dev jobs started by scripts/run-dev.ps1.
#>
$ErrorActionPreference = 'SilentlyContinue'
Get-Job | Where-Object { $_.State -eq 'Running' } | Stop-Job
Get-Job | Remove-Job -Force
Write-Host "Dev jobs stopped."
