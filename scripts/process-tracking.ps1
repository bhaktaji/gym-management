<#
.SYNOPSIS
  Shared helpers for start-all.ps1 / stop-all.ps1 / test-start-all.ps1.

.DESCRIPTION
  Contains process tracking, HTTP/TCP probing and logging helpers. Nothing in
  here changes machine-wide state: no global JAVA_HOME, no PATH edits, no
  registry writes.

  Process tracking notes:
    * Only processes that THIS repository's scripts started are recorded.
    * Each entry stores the pid AND the process start time, so a recycled pid
      from a stale state file is never mistaken for a live process.
    * Shutdown only ever walks the descendants of a recorded pid, so unrelated
      processes are never touched.
#>

$script:GymKhataStateFileName = 'gymkhata-processes.json'

# ---------------------------------------------------------------- logging ----

function Write-Step {
    param([string]$Message, [string]$Color = 'Cyan')
    $ts = (Get-Date).ToString('HH:mm:ss')
    Write-Host "[$ts] $Message" -ForegroundColor $Color
}

function Write-Detail {
    param([string]$Message, [string]$Color = 'DarkGray')
    Write-Host "        $Message" -ForegroundColor $Color
}

function Write-Ok {
    param([string]$Message)
    Write-Step $Message 'Green'
}

function Write-Warn {
    param([string]$Message)
    Write-Step $Message 'Yellow'
}

function Write-Fail {
    param([string]$Message)
    Write-Step $Message 'Red'
}

# Redacts anything that looks like a secret before it is written to a log.
function Protect-SecretText {
    param([string]$Text)
    if ([string]::IsNullOrEmpty($Text)) { return $Text }
    $Text = [regex]::Replace($Text, '(?i)(password|passwd|pwd|token|secret|apikey|api_key|authorization)(\s*[:=]\s*)("?)([^\s"'',;&]+)', '$1$2$3***$3')
    $Text = [regex]::Replace($Text, '(?i)(mongodb(\+srv)?://[^:]+:)([^@]+)@', '$1***@')
    return $Text
}

# ------------------------------------------------------------- diagnostics ---

function Test-TcpPort {
    param(
        [string]$ComputerName = 'localhost',
        [int]$Port,
        [int]$TimeoutMs = 1500
    )
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($ComputerName, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        try { $client.Close() } catch { }
    }
}

# Returns @{ Ok; StatusCode; Body }. Never prompts, never throws.
function Get-HttpStatus {
    param(
        [string]$Url,
        [int]$TimeoutSec = 5
    )
    $result = [pscustomobject]@{ Ok = $false; StatusCode = 0; Body = '' }
    try {
        $request = [System.Net.HttpWebRequest]::Create($Url)
        $request.Method = 'GET'
        $request.Timeout = $TimeoutSec * 1000
        $request.ReadWriteTimeout = $TimeoutSec * 1000
        $request.AllowAutoRedirect = $true
        $request.UserAgent = 'GymKhata-StartupScript'
        $response = $request.GetResponse()
        $result.StatusCode = [int]$response.StatusCode
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        $result.Body = $reader.ReadToEnd()
        $reader.Close()
        $response.Close()
        $result.Ok = ($result.StatusCode -ge 200 -and $result.StatusCode -lt 400)
    } catch [System.Net.WebException] {
        if ($_.Exception.Response) {
            try { $result.StatusCode = [int]$_.Exception.Response.StatusCode } catch { }
        }
    } catch {
        # connection refused / DNS / timeout - leave Ok = $false
    }
    return $result
}

function Get-LogTail {
    param(
        [string]$Path,
        [int]$Lines = 25
    )
    if (-not $Path -or -not (Test-Path -LiteralPath $Path)) { return @() }
    try {
        return @(Get-Content -LiteralPath $Path -Tail $Lines -ErrorAction SilentlyContinue)
    } catch {
        return @()
    }
}

# --------------------------------------------------------- process tracking ---

# Returns @{ Alive; StartTime; Name } for a pid.
function Get-ProcessInfo {
    param([int]$ProcessId)
    $info = [pscustomobject]@{ Alive = $false; StartTime = $null; Name = '' }
    if ($ProcessId -le 0) { return $info }
    try {
        $p = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if ($null -eq $p) { return $info }
        $info.Alive = $true
        $info.Name = $p.ProcessName
        try { $info.StartTime = $p.StartTime.ToUniversalTime().ToString('o') } catch { $info.StartTime = '' }
    } catch {
        return $info
    }
    return $info
}

# True when the pid is alive AND its start time matches the recorded one.
# This is what makes a stale pid file safe: a recycled pid never matches.
function Test-TrackedProcess {
    param(
        [int]$ProcessId,
        [string]$ExpectedStartTime
    )
    if ($ProcessId -le 0) { return $false }
    $info = Get-ProcessInfo -ProcessId $ProcessId
    if (-not $info.Alive) { return $false }
    if ([string]::IsNullOrWhiteSpace($ExpectedStartTime)) { return $true }
    if ([string]::IsNullOrWhiteSpace($info.StartTime)) { return $true }
    return ($info.StartTime -eq $ExpectedStartTime)
}

# All descendant pids of a pid (recursively), deepest first.
function Get-DescendantProcessIds {
    param([int]$ParentProcessId)
    $descendants = New-Object System.Collections.Generic.List[int]
    if ($ParentProcessId -le 0) { return $descendants }

    $queue = New-Object System.Collections.Generic.Queue[int]
    $queue.Enqueue($ParentProcessId)
    while ($queue.Count -gt 0) {
        $current = $queue.Dequeue()
        $children = @()
        try {
            $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$current" -ErrorAction SilentlyContinue)
        } catch {
            $children = @()
        }
        foreach ($child in $children) {
            $childId = [int]$child.ProcessId
            if ($childId -gt 0 -and -not $descendants.Contains($childId)) {
                $descendants.Add($childId)
                $queue.Enqueue($childId)
            }
        }
    }
    # deepest first so children die before parents
    [array]::Reverse($descendants)
    return $descendants
}

function Get-GymKhataStatePath {
    param([string]$LogDir)
    return (Join-Path $LogDir $script:GymKhataStateFileName)
}

function Read-GymKhataState {
    param([string]$StatePath)
    $state = [pscustomobject]@{ UpdatedAt = ''; Services = @() }
    if (-not (Test-Path -LiteralPath $StatePath)) { return $state }
    try {
        $raw = Get-Content -LiteralPath $StatePath -Raw -ErrorAction SilentlyContinue
        if ([string]::IsNullOrWhiteSpace($raw)) { return $state }
        $parsed = $raw | ConvertFrom-Json
        $services = @()
        if ($parsed.PSObject.Properties.Name -contains 'Services' -and $parsed.Services) {
            $services = @($parsed.Services)
        }
        $updated = ''
        if ($parsed.PSObject.Properties.Name -contains 'UpdatedAt' -and $parsed.UpdatedAt) {
            $updated = [string]$parsed.UpdatedAt
        }
        return [pscustomobject]@{ UpdatedAt = $updated; Services = $services }
    } catch {
        Write-Warn "State file '$StatePath' is unreadable and will be ignored: $($_.Exception.Message)"
        return $state
    }
}

function Write-GymKhataState {
    param(
        [string]$StatePath,
        $Services
    )
    $payload = [pscustomobject]@{
        UpdatedAt = (Get-Date).ToUniversalTime().ToString('o')
        Services  = @($Services)
    }
    $json = $payload | ConvertTo-Json -Depth 6
    Set-Content -LiteralPath $StatePath -Value $json -Encoding UTF8
}

function Save-GymKhataService {
    param(
        [string]$StatePath,
        [string]$Name,
        [int]$ProcessId,
        [string]$StartTime,
        [string]$LogFile,
        [int]$Port,
        [string]$Url
    )
    $state = Read-GymKhataState -StatePath $StatePath
    $services = @($state.Services | Where-Object { $_.Name -ne $Name })
    $services += [pscustomobject]@{
        Name      = $Name
        ProcessId = $ProcessId
        StartTime = $StartTime
        LogFile   = $LogFile
        Port      = $Port
        Url       = $Url
    }
    Write-GymKhataState -StatePath $StatePath -Services $services
}

function Remove-GymKhataService {
    param(
        [string]$StatePath,
        [string]$Name
    )
    $state = Read-GymKhataState -StatePath $StatePath
    $services = @($state.Services | Where-Object { $_.Name -ne $Name })
    Write-GymKhataState -StatePath $StatePath -Services $services
}

# Stops a tracked process and its descendants. Only ever touches processes that
# descend from a pid this repository's scripts started.
function Stop-TrackedProcess {
    param(
        [int]$ProcessId,
        [string]$ExpectedStartTime,
        [switch]$Quiet
    )
    if (-not (Test-TrackedProcess -ProcessId $ProcessId -ExpectedStartTime $ExpectedStartTime)) {
        if (-not $Quiet) { Write-Detail "process $ProcessId is not running (or the pid was recycled) - nothing to stop" }
        return $false
    }
    $ids = @($ProcessId) + @(Get-DescendantProcessIds -ParentProcessId $ProcessId)
    foreach ($id in $ids) {
        try {
            Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
        } catch { }
    }
    if (-not $Quiet) { Write-Detail "stopped process $ProcessId and $($ids.Count - 1) descendant(s)" }
    return $true
}

# Quotes a command-line argument that contains spaces so Start-Process passes it
# through as a single argument.
function Format-ProcessArgument {
    param([string]$Value)
    if ($null -eq $Value) { return '""' }
    if ($Value -match '[\s"]') {
        return '"' + ($Value -replace '"', '\"') + '"'
    }
    return $Value
}
