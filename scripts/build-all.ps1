<#
.SYNOPSIS
  Builds the whole project: frontend (Vite) then backend (Maven).

.DESCRIPTION
  The frontend bundle is copied into the backend's static resources so the
  Spring Boot jar serves the whole app (API + PWA) on a single port.

  Requires a JDK 17+ on PATH. On this machine JDK 21 is used; the script
  does not change any global environment.
#>
param(
    [switch]$SkipTests,
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $root 'frontend'
$backend = Join-Path $root 'backend'
$staticDir = Join-Path $backend 'src/main/resources/static'

. (Join-Path $PSScriptRoot 'resolve-jdk.ps1')
$jdk = Get-GymKhataJdk -JavaHome $JavaHome
$env:JAVA_HOME = $jdk
Write-Host "Using JDK: $jdk" -ForegroundColor DarkGray

Write-Host "==> Building frontend" -ForegroundColor Cyan
Push-Location $frontend
try {
    if (-not (Test-Path 'node_modules')) {
        Write-Host "    installing npm dependencies..."
        npm install --no-audit --no-fund
    }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "frontend build failed" }
} finally {
    Pop-Location
}

Write-Host "==> Copying frontend bundle into backend static resources" -ForegroundColor Cyan
if (Test-Path $staticDir) { Remove-Item $staticDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $staticDir | Out-Null
Copy-Item -Path (Join-Path $frontend 'dist/*') -Destination $staticDir -Recurse -Force

Write-Host "==> Building backend" -ForegroundColor Cyan
Push-Location $backend
try {
    if ($SkipTests) {
        mvn -q clean package -DskipTests
    } else {
        mvn -q clean package
    }
    if ($LASTEXITCODE -ne 0) { throw "backend build failed" }
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $backend 'target') -Filter '*.jar' |
    Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1

Write-Host ""
Write-Host "Build complete." -ForegroundColor Green
Write-Host "  run with : java -jar $($jar.FullName)"
Write-Host "  app url : http://localhost:8080"
Write-Host "  api docs: http://localhost:8080/swagger-ui.html"
