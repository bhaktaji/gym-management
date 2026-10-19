<#
.SYNOPSIS
  Starts the whole GymKhata application: MongoDB, backend, frontend.

.DESCRIPTION
  Starts services in a strict order and verifies each one before moving on:

    [1/3] MongoDB   - reuse a healthy instance, or start the Windows service
    [2/3] Backend   - Spring Boot on :8080 (actuator health)
    [3/3] Frontend  - Vite dev server on :5173

  Rules this script obeys:
    * It never changes the global JAVA_HOME, PATH or any machine-wide setting.
    * It never deletes, recreates or resets any database.
    * It only ever stops processes that IT started (tracked in logs/).
    * Re-running it is safe: healthy services are reused, not duplicated.
    * If a stage fails, later stages are not started.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 -Build
#>
[CmdletBinding()]
param(
    # Build the project (frontend + backend jar) before starting.
    [switch]$Build,
    # Force a specific JDK 17+ for the backend runtime.
    [string]$JavaHome,
    # Seconds to wait for MongoDB to become ready.
    [int]$MongoTimeoutSec = 60,
    # Seconds to wait for the backend to become healthy.
    [int]$BackendTimeoutSec = 120,
    # Seconds to wait for the Vite dev server to respond.
    [int]$FrontendTimeoutSec = 90,
    # Backend port (matches server.port in application.yml).
    [int]$BackendPort = 8080,
    # Frontend port (matches server.port in vite.config.ts).
    [int]$FrontendPort = 5173,
    # MongoDB port.
    [int]$MongoPort = 27017
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot 'backend'
$frontendDir = Join-Path $repoRoot 'frontend'
$logDir = Join-Path $repoRoot 'logs'
$statePath = Join-Path $logDir 'gymkhata-processes.json'

$backendUrl = "http://localhost:$BackendPort"
$frontendUrl = "http://localhost:$FrontendPort"
$backendHealthUrl = "$backendUrl/actuator/health"
$backendIdentityUrl = "$backendUrl/api/v1/config/public"

. (Join-Path $PSScriptRoot 'process-tracking.ps1')
. (Join-Path $PSScriptRoot 'resolve-jdk.ps1')

$startedServices = @()

function Exit-WithFailure {
    param([string]$Message, [int]$ExitCode = 1)
    Write-Fail $Message
    if ($startedServices.Count -gt 0) {
        Write-Warn "Still running (started by this script): $($startedServices -join ', ')"
        Write-Detail "stop them with: powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1"
    }
    exit $ExitCode
}

# ---------------------------------------------------------------------------
# Optional build stage
# ---------------------------------------------------------------------------
if ($Build) {
    Write-Step "Building project (scripts\build-all.ps1)..." 'Cyan'

    # A running backend holds the jar open, so the build would fail.
    $runningBackend = $null
    $state = Read-GymKhataState -StatePath $statePath
    foreach ($svc in $state.Services) {
        if ($svc.Name -eq 'backend' -and (Test-TrackedProcess -ProcessId ([int]$svc.ProcessId) -ExpectedStartTime ([string]$svc.StartTime))) {
            $runningBackend = $svc
            break
        }
    }
    if ($runningBackend) {
        Write-Fail "The backend is running (pid $($runningBackend.ProcessId)) and would block the jar from being overwritten."
        Write-Detail "Stop it first:  powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1"
        exit 1
    }

    # Detect a jar that is locked by some other process.
    $existingJars = @(Get-ChildItem (Join-Path $backendDir 'target') -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' })
    foreach ($jar in $existingJars) {
        $locked = $false
        try {
            $stream = [System.IO.File]::Open($jar.FullName, 'Open', 'ReadWrite', 'None')
            $stream.Close()
        } catch {
            $locked = $true
        }
        if ($locked) {
            Write-Fail "'$($jar.Name)' is locked by another process - the jar cannot be overwritten."
            Write-Detail "Stop the running GymKhata backend and try again."
            exit 1
        }
    }

    $buildScript = Join-Path $PSScriptRoot 'build-all.ps1'
    if (-not (Test-Path -LiteralPath $buildScript)) {
        Exit-WithFailure "Build script not found: $buildScript"
    }
    & powershell -ExecutionPolicy Bypass -NoProfile -File $buildScript -JavaHome $JavaHome
    if ($LASTEXITCODE -ne 0) {
        Exit-WithFailure "Build failed (exit code $LASTEXITCODE). Aborting startup." 1
    }
    Write-Ok "Build finished."
    Write-Step ''
}

# ---------------------------------------------------------------------------
# [1/3] MongoDB
# ---------------------------------------------------------------------------
Write-Step "[1/3] Checking MongoDB..."

$javaExe = $null
try {
    $jdkHome = Get-GymKhataJdk -JavaHome $JavaHome
    $javaExe = Join-Path $jdkHome 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaExe)) { $javaExe = $null }
} catch {
    $javaExe = $null
}

function Test-MongoReadiness {
    <#
      Real database readiness check.
        1. mongosh  - a genuine 'ping' command against the server
        2. MongoDB Java driver (single-file source mode) - also a genuine ping
        3. TCP only  - port readiness only, clearly labelled as such
      Never writes to the database.
    #>
    # short server-selection/connect timeouts so a dead server fails fast and
    # the caller's timeout is respected
    $uri = "mongodb://localhost:$MongoPort/?serverSelectionTimeoutMS=2000&connectTimeoutMS=2000"

    # Cheap first: if the port is not even open there is nothing to ping.
    if (-not (Test-TcpPort -ComputerName 'localhost' -Port $MongoPort -TimeoutMs 1500)) {
        return [pscustomobject]@{ Ready = $false; Level = 'none'; Detail = 'not accepting connections' }
    }

    $mongosh = Get-Command mongosh -ErrorAction SilentlyContinue
    if (-not $mongosh) {
        foreach ($candidate in @(
                "C:\Program Files\MongoDB\Shell\mongosh.exe",
                "C:\Program Files\MongoDB\Server\*\bin\mongosh.exe")) {
            $found = Get-Item $candidate -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($found) { $mongosh = $found; break }
        }
    }
    if ($mongosh) {
        $mongoshPath = if ($mongosh -is [System.Management.Automation.ApplicationInfo]) { $mongosh.Source } else { $mongosh.Path }
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            & $mongoshPath --quiet --eval "db.adminCommand('ping').ok" $uri 2>&1 | Out-Null
        } finally {
            $ErrorActionPreference = $prev
        }
        if ($LASTEXITCODE -eq 0) {
            return [pscustomobject]@{ Ready = $true; Level = 'database'; Detail = 'mongosh ping ok' }
        }
    }

    if ($javaExe) {
        $driverJars = Get-MongoDriverClasspath
        if ($driverJars) {
            $pingSource = Join-Path $logDir 'MongoPing.java'
            $source = @'
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class MongoPing {
    public static void main(String[] args) {
        try (MongoClient c = MongoClients.create(args[0])) {
            MongoDatabase db = c.getDatabase("admin");
            Document r = db.runCommand(new Document("ping", 1));
            double ok = r.getDouble("ok");
            System.out.println(ok == 1.0 ? "PING_OK" : "PING_FAIL");
            System.exit(ok == 1.0 ? 0 : 1);
        } catch (Exception e) {
            System.out.println("PING_ERROR " + e.getClass().getSimpleName());
            System.exit(2);
        }
    }
}
'@
            [System.IO.File]::WriteAllText($pingSource, $source, (New-Object System.Text.UTF8Encoding($false)))
            $prev = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $output = & $javaExe --class-path $driverJars $pingSource $uri 2>&1
                $exit = $LASTEXITCODE
            } finally {
                $ErrorActionPreference = $prev
            }
            if ($exit -eq 0 -and ($output -join "`n") -match 'PING_OK') {
                return [pscustomobject]@{ Ready = $true; Level = 'database'; Detail = 'MongoDB driver ping ok' }
            }
        }
    }

    if (Test-TcpPort -ComputerName 'localhost' -Port $MongoPort -TimeoutMs 1500) {
        return [pscustomobject]@{
            Ready = $true
            Level = 'port'
            Detail = 'port is open, but database readiness could not be verified (no mongosh, no MongoDB driver on this machine)'
        }
    }
    return [pscustomobject]@{ Ready = $false; Level = 'none'; Detail = 'not accepting connections' }
}

function Get-MongoDriverClasspath {
    <#
      Builds a classpath for the MongoDB Java driver from the local Maven
      repository, using the newest version that has ALL required artifacts.
      Returns $null when the driver is not available locally.
    #>
    $repo = "$env:USERPROFILE\.m2\repository\org\mongodb"
    if (-not (Test-Path -LiteralPath $repo)) { return $null }
    $need = @('mongodb-driver-sync', 'mongodb-driver-core', 'bson')

    $versions = @()
    foreach ($artifact in $need) {
        $dir = Join-Path $repo $artifact
        if (-not (Test-Path -LiteralPath $dir)) { return $null }
        $found = @(Get-ChildItem $dir -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Name -match ('^' + [regex]::Escape($artifact) + '-\d') -and
                $_.Name -notlike '*sources*' -and
                $_.Name -notlike '*javadoc*'
            })
        if ($found.Count -eq 0) { return $null }
        $versions += , @($found | ForEach-Object { $_.Directory.Name } | Sort-Object -Unique)
    }

    # newest version directory that every artifact has
    $common = @($versions[0] | Where-Object { $v = $_; ($versions | Where-Object { $v -notin $_ }).Count -eq 0 })
    if ($common.Count -eq 0) { return $null }
    $chosen = $common | Sort-Object -Property @{ Expression = { Get-VersionSortKey $_ } } -Descending | Select-Object -First 1

    $jars = @()
    foreach ($artifact in $need) {
        $jar = Get-ChildItem (Join-Path $repo $artifact) -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Directory.Name -eq $chosen -and
                $_.Name -match ('^' + [regex]::Escape($artifact) + '-\d') -and
                $_.Name -notlike '*sources*' -and
                $_.Name -notlike '*javadoc*'
            } | Select-Object -First 1
        if (-not $jar) { return $null }
        $jars += $jar.FullName
    }
    return ($jars -join ';')
}

function Get-VersionSortKey {
    param([string]$Text)
    try { return [version]($Text -replace '[^0-9.].*$', '') } catch { return [version]'0.0' }
}

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$mongo = Test-MongoReadiness
if ($mongo.Ready) {
    if ($mongo.Level -eq 'database') {
        Write-Ok "MongoDB is already running and healthy (${MongoPort}) - reusing it."
    } else {
        Write-Warn "MongoDB port ${MongoPort} is open - reusing it."
        Write-Detail $mongo.Detail
    }
} else {
    Write-Detail "MongoDB is not accepting connections on port ${MongoPort} - trying to start the Windows service..."

    $service = Get-Service -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like '*mongo*' -or $_.DisplayName -like '*mongo*' } |
        Select-Object -First 1

    if (-not $service) {
        Exit-WithFailure "MongoDB is not running and no 'MongoDB' Windows service was found on this machine." 2
    }

    Write-Detail "service '$($service.Name)' ($($service.Status))"
    try {
        if ($service.Status -ne 'Running') {
            Start-Service -Name $service.Name -ErrorAction Stop
        }
    } catch {
        Exit-WithFailure "Could not start the MongoDB service '$($service.Name)': $($_.Exception.Message)" 2
    }

    $deadline = (Get-Date).AddSeconds($MongoTimeoutSec)
    $mongo = $null
    while ((Get-Date) -lt $deadline) {
        $mongo = Test-MongoReadiness
        if ($mongo.Ready) { break }
        Start-Sleep -Seconds 2
    }

    if (-not $mongo -or -not $mongo.Ready) {
        Exit-WithFailure "MongoDB did not become ready within ${MongoTimeoutSec}s." 2
    }
    if ($mongo.Level -eq 'database') {
        Write-Ok "MongoDB started and is healthy (${MongoPort})."
    } else {
        Write-Warn "MongoDB started and port ${MongoPort} is open, but database readiness could not be verified."
        Write-Detail $mongo.Detail
    }
}

# ---------------------------------------------------------------------------
# [2/3] Backend
# ---------------------------------------------------------------------------
Write-Step "[2/3] Starting GymKhata backend..."

$health = Get-HttpStatus -Url $backendHealthUrl -TimeoutSec 3
$identity = Get-HttpStatus -Url $backendIdentityUrl -TimeoutSec 3
$backendAlreadyRunning = ($health.Ok -and $health.Body -match '"status"\s*:\s*"UP"') -and
                         ($identity.Ok -and $identity.Body -match '"name"\s*:\s*"GymKhata"')

if ($backendAlreadyRunning) {
    Write-Ok "GymKhata backend is already running and healthy on ${backendUrl} - reusing it."
    $state = Read-GymKhataState -StatePath $statePath
    $existing = $state.Services | Where-Object { $_.Name -eq 'backend' } | Select-Object -First 1
    if (-not $existing) {
        Write-Detail 'note: the running backend was not started by this script, so stop-all.ps1 will leave it alone.'
    }
} else {
    # Is the port taken by something that is not GymKhata?
    if (Test-TcpPort -ComputerName 'localhost' -Port $BackendPort -TimeoutMs 1200) {
        Exit-WithFailure "Port ${BackendPort} is already in use by something that is not a healthy GymKhata backend. Refusing to start a duplicate." 3
    }

    $jar = Get-ChildItem (Join-Path $backendDir 'target') -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
    if (-not $jar) {
        Write-Fail "Backend jar not found in $(Join-Path $backendDir 'target')."
        Write-Detail "Build it first:  powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1"
        Write-Detail "or start with:  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 -Build"
        exit 4
    }

    if (-not $javaExe) {
        Exit-WithFailure "No Java 17+ runtime was found. Pass -JavaHome <path> or install a JDK 17+." 4
    }
    Write-Detail "runtime: $javaExe"
    Write-Detail "jar    : $($jar.FullName)"

    $backendLog = Join-Path $logDir 'backend.log'
    $backendErr = Join-Path $logDir 'backend.err.log'
    $backendArgs = @('-jar', (Format-ProcessArgument $jar.FullName))

    $proc = Start-Process -FilePath $javaExe -ArgumentList $backendArgs -WorkingDirectory $backendDir `
        -RedirectStandardOutput $backendLog -RedirectStandardError $backendErr -WindowStyle Hidden -PassThru

    Write-Detail "pid $($proc.Id) - waiting for $backendHealthUrl (timeout ${BackendTimeoutSec}s)"

    $deadline = (Get-Date).AddSeconds($BackendTimeoutSec)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        if ($proc.HasExited) { break }
        $h = Get-HttpStatus -Url $backendHealthUrl -TimeoutSec 3
        if ($h.Ok -and $h.Body -match '"status"\s*:\s*"UP"') { $ready = $true; break }
        Start-Sleep -Seconds 1
    }

    if (-not $ready) {
        Write-Fail "The backend did not become healthy within ${BackendTimeoutSec}s."
        if ($proc.HasExited) {
            Write-Detail "the process exited with code $($proc.ExitCode)"
        }
        Write-Detail "last lines of $backendLog :"
        foreach ($line in (Get-LogTail -Path $backendLog -Lines 20)) { Write-Detail (Protect-SecretText $line) }
        Write-Detail "last lines of $backendErr :"
        foreach ($line in (Get-LogTail -Path $backendErr -Lines 20)) { Write-Detail (Protect-SecretText $line) }
        Stop-TrackedProcess -ProcessId $proc.Id -ExpectedStartTime '' -Quiet | Out-Null
        exit 5
    }

    $procInfo = Get-ProcessInfo -ProcessId $proc.Id
    Save-GymKhataService -StatePath $statePath -Name 'backend' -ProcessId $proc.Id `
        -StartTime $procInfo.StartTime -LogFile $backendLog -Port $BackendPort -Url $backendUrl
    $startedServices += 'backend'
    Write-Ok "GymKhata backend is up on ${backendUrl} (pid $($proc.Id))."
    Write-Detail "logs: $backendLog"
}

# ---------------------------------------------------------------------------
# [3/3] Frontend
# ---------------------------------------------------------------------------
Write-Step "[3/3] Starting GymKhata frontend..."

$frontendCheck = Get-HttpStatus -Url "$frontendUrl/" -TimeoutSec 3
$frontendAlreadyRunning = $frontendCheck.Ok -and $frontendCheck.Body -match 'id="root"'

if ($frontendAlreadyRunning) {
    Write-Ok "The Vite dev server is already running on ${frontendUrl} - reusing it."
} else {
    if (Test-TcpPort -ComputerName 'localhost' -Port $FrontendPort -TimeoutMs 1200) {
        Exit-WithFailure "Port ${FrontendPort} is already in use by something that is not the GymKhata dev server. Refusing to start a duplicate." 6
    }

    if (-not (Test-Path -LiteralPath $frontendDir)) {
        Exit-WithFailure "Frontend directory not found: $frontendDir" 6
    }
    $packageJson = Join-Path $frontendDir 'package.json'
    if (-not (Test-Path -LiteralPath $packageJson)) {
        Exit-WithFailure "package.json not found in $frontendDir" 6
    }
    if (-not (Test-Path -LiteralPath (Join-Path $frontendDir 'node_modules'))) {
        Write-Fail "node_modules is missing in $frontendDir"
        Write-Detail "Install the frontend dependencies first:"
        Write-Detail "    cd frontend; npm install"
        exit 7
    }

    $npmCmd = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source
    if (-not $npmCmd) { $npmCmd = (Get-Command npm -ErrorAction SilentlyContinue).Source }
    if (-not $npmCmd) {
        Exit-WithFailure "npm was not found on this machine. Install Node.js 18+ and try again." 7
    }

    $frontendLog = Join-Path $logDir 'frontend.log'
    $frontendErr = Join-Path $logDir 'frontend.err.log'

    # npm run dev through cmd.exe so the .cmd shim and its node child both
    # survive after this script exits and can be tracked as one process tree.
    $npmArgs = @('/d', '/s', '/c', ('""{0}" run dev"' -f $npmCmd))

    $proc = Start-Process -FilePath $env:ComSpec -ArgumentList $npmArgs -WorkingDirectory $frontendDir `
        -RedirectStandardOutput $frontendLog -RedirectStandardError $frontendErr -WindowStyle Hidden -PassThru

    Write-Detail "pid $($proc.Id) - waiting for ${frontendUrl}/ (timeout ${FrontendTimeoutSec}s)"

    $deadline = (Get-Date).AddSeconds($FrontendTimeoutSec)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        if ($proc.HasExited) { break }
        $f = Get-HttpStatus -Url "$frontendUrl/" -TimeoutSec 3
        if ($f.Ok -and $f.Body -match 'id="root"') { $ready = $true; break }
        Start-Sleep -Seconds 1
    }

    if (-not $ready) {
        Write-Fail "The frontend did not start within ${FrontendTimeoutSec}s."
        if ($proc.HasExited) {
            Write-Detail "the process exited with code $($proc.ExitCode)"
        }
        Write-Detail "last lines of $frontendLog :"
        foreach ($line in (Get-LogTail -Path $frontendLog -Lines 20)) { Write-Detail (Protect-SecretText $line) }
        Write-Detail "last lines of $frontendErr :"
        foreach ($line in (Get-LogTail -Path $frontendErr -Lines 20)) { Write-Detail (Protect-SecretText $line) }
        Stop-TrackedProcess -ProcessId $proc.Id -ExpectedStartTime '' -Quiet | Out-Null
        if ($startedServices.Count -gt 0) {
            Write-Warn "The backend is still running (started by this script): $($startedServices -join ', ')"
            Write-Detail "stop it with: powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1"
        }
        exit 8
    }

    $procInfo = Get-ProcessInfo -ProcessId $proc.Id
    Save-GymKhataService -StatePath $statePath -Name 'frontend' -ProcessId $proc.Id `
        -StartTime $procInfo.StartTime -LogFile $frontendLog -Port $FrontendPort -Url $frontendUrl
    $startedServices += 'frontend'
    Write-Ok "GymKhata frontend is up on ${frontendUrl} (pid $($proc.Id))."
    Write-Detail "logs: $frontendLog"
}

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
Write-Step ''
Write-Ok 'GymKhata is running.'
Write-Detail "backend   : $backendUrl"
Write-Detail "frontend  : $frontendUrl"
Write-Detail "admin     : $backendUrl/admin"
Write-Detail "API docs  : $backendUrl/swagger-ui.html"
Write-Detail "logs      : $logDir"
Write-Detail "stop with : powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1"
exit 0
