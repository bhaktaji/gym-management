<#
.SYNOPSIS
  Resolves a JDK 17+ installation and returns its path.

.DESCRIPTION
  Used by the build/run scripts so that Maven runs on a modern JDK without
  changing the machine's global environment. Order of preference:
    1. -JavaHome parameter
    2. JAVA_HOME if it points at a JDK 17+
    3. Well-known install locations (Microsoft OpenJDK, Eclipse Temurin, ...)

  The function only inspects the machine; it never installs or modifies anything.
#>
function Get-GymKhataJdk {
    param([string]$JavaHome)

    function Get-MajorVersion([string]$javaExe) {
        if (-not $javaExe -or -not (Test-Path $javaExe)) { return 0 }
        # java prints its version on stderr; capture it without tripping ErrorActionPreference
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $raw = (& $javaExe -version 2>&1) -join "`n"
        } finally {
            $ErrorActionPreference = $prev
        }
        if ($raw -match 'version "(\d+)') { return [int]$Matches[1] }
        if ($raw -match 'version (\d+)\.') { return [int]$Matches[1] }
        return 0
    }

    if ($JavaHome) {
        $exe = Join-Path $JavaHome 'bin/java.exe'
        if ((Get-MajorVersion $exe) -ge 17) { return $JavaHome }
        throw "The JDK at '$JavaHome' is older than 17."
    }

    if ($env:JAVA_HOME) {
        $exe = Join-Path $env:JAVA_HOME 'bin/java.exe'
        if ((Get-MajorVersion $exe) -ge 17) { return $env:JAVA_HOME }
    }

    $candidates = @()
    foreach ($root in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
        if (-not $root) { continue }
        foreach ($pattern in @('Microsoft\jdk-*', 'Eclipse Adoptium\jdk-*', 'Eclipse Adoptium\jdk*',
                               'Java\jdk-*', 'Amazon Corretto\jdk*', 'Zulu\jdk*')) {
            $candidates += Get-ChildItem -Path (Join-Path $root $pattern) -Directory -ErrorAction SilentlyContinue |
                ForEach-Object { $_.FullName }
        }
    }

    $best = $null
    $bestVersion = 0
    foreach ($dir in $candidates) {
        $exe = Join-Path $dir 'bin/java.exe'
        $v = Get-MajorVersion $exe
        if ($v -ge 17 -and $v -gt $bestVersion) {
            $best = $dir
            $bestVersion = $v
        }
    }
    if ($best) { return $best }
    throw "No JDK 17+ found. Pass -JavaHome <path-to-jdk>."
}
