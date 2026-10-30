param(
    [switch]$Reset
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ResetVal = if ($Reset) { "true" } else { "false" }

$env:SPRING_PROFILES_ACTIVE = "demo"
if (-not $env:MONGODB_URI) {
    $env:MONGODB_URI = "mongodb://localhost:27017/gymkhata_demo"
}
$env:DEMO_RESET = $ResetVal

Write-Host "==> Running GymKhata Demo Seeder (profile=demo, uri=$($env:MONGODB_URI), reset=$ResetVal)"
Push-Location "$Root\backend"
try {
    mvn test-compile test "-Dtest=DemoDataSeederTest" "-DfailIfNoTests=false" "-Ddemo.reset=$ResetVal"
} finally {
    Pop-Location
}
