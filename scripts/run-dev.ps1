<#
.SYNOPSIS
  Starts the backend and the Vite dev server for local development.

.DESCRIPTION
  Backend  : http://localhost:8080  (Maven spring-boot:run)
  Frontend : http://localhost:5173  (Vite dev server, proxies /api to :8080)
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'

. (Join-Path $PSScriptRoot 'resolve-jdk.ps1')
$env:JAVA_HOME = Get-GymKhataJdk
Write-Host "Using JDK: $env:JAVA_HOME" -ForegroundColor DarkGray

Write-Host "Starting backend..." -ForegroundColor Cyan
$backendJob = Start-Job -ScriptBlock {
    param($dir)
    Set-Location $dir
    mvn spring-boot:run
} -ArgumentList $backend

Write-Host "Starting frontend dev server..." -ForegroundColor Cyan
$frontendJob = Start-Job -ScriptBlock {
    param($dir)
    Set-Location $dir
    npm run dev
} -ArgumentList $frontend

Write-Host ""
Write-Host "  frontend : http://localhost:5173" -ForegroundColor Green
Write-Host "  backend  : http://localhost:8080" -ForegroundColor Green
Write-Host "  stop     : scripts/stop-dev.ps1" -ForegroundColor Green
Write-Host ""

try {
    Receive-Job -Job $backendJob -Wait -AutoRemoveJob
} finally {
    Get-Job | Where-Object { $_.State -ne 'Running' } | Remove-Job -Force
}
