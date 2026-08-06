<#
.SYNOPSIS
    Runs a build or start command and keeps a durable record of it under logs/.

.DESCRIPTION
    The Windows equivalent of scripts/run-with-logs.sh, kept in step with it. The team develops on
    Windows while the deploy host is Linux, so both exist rather than assuming a shell.

    The problem this solves: `mvn test` and `npm run build` print to a terminal and are gone the
    moment it closes. When a build fails on someone else's machine, or a run dies overnight, the
    only useful artefact - the output - is the thing that was never kept. Runtime logging is
    handled by logback-spring.xml; this covers everything that happens around the application.

    Output is tee'd, so you still watch it live. Each log carries a header recording the commit,
    branch and start time, which is what makes an old log worth anything.

.PARAMETER Task
    build-backend, test-backend, build-frontend, test-frontend, run-backend, run-frontend, or all.

.EXAMPLE
    .\scripts\run-with-logs.ps1 test-backend
.EXAMPLE
    .\scripts\run-with-logs.ps1 all
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('build-backend', 'test-backend', 'build-frontend', 'test-frontend',
                 'run-backend', 'run-frontend', 'all')]
    [string]$Task
)

$ErrorActionPreference = 'Continue'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$LogDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $RepoRoot 'logs' }
$Timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'

New-Item -ItemType Directory -Force -Path (Join-Path $LogDir 'build') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $LogDir 'run') | Out-Null

function Get-GitValue([string]$Arguments) {
    try {
        $value = & git -C $RepoRoot $Arguments.Split(' ') 2>$null
        if ($LASTEXITCODE -eq 0 -and $value) { return $value } else { return 'unknown' }
    } catch {
        return 'unknown'
    }
}

function Invoke-Captured {
    param(
        [string]$Name,
        [string]$Category,
        [string]$WorkDir,
        [string]$Command,
        [string[]]$CommandArgs
    )

    $logFile = Join-Path $LogDir "$Category\$Name-$Timestamp.log"
    $latestFile = Join-Path $LogDir "$Category\$Name-latest.log"

    $header = @(
        '=============================================================='
        " Portiq $Category log"
        " task      : $Name"
        " command   : $Command $($CommandArgs -join ' ')"
        " directory : $WorkDir"
        " branch    : $(Get-GitValue 'rev-parse --abbrev-ref HEAD')"
        " commit    : $(Get-GitValue 'rev-parse --short HEAD')"
        " started   : $(Get-Date -Format 'o')"
        " host      : $env:COMPUTERNAME"
        '=============================================================='
        ''
    )
    $header | Out-File -FilePath $logFile -Encoding utf8

    Push-Location $WorkDir
    try {
        # Tee-Object writes to the file while the output still reaches the console, so a long
        # build stays watchable instead of going silent for two minutes.
        & $Command @CommandArgs 2>&1 | Tee-Object -FilePath $logFile -Append
        $status = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    @(
        ''
        '--------------------------------------------------------------'
        " finished  : $(Get-Date -Format 'o')"
        " exit code : $status"
        '--------------------------------------------------------------'
    ) | Out-File -FilePath $logFile -Append -Encoding utf8

    Copy-Item -Path $logFile -Destination $latestFile -Force

    if ($status -eq 0) {
        Write-Host ">> $Name succeeded. Log: $logFile" -ForegroundColor Green
    } else {
        Write-Host ">> $Name FAILED (exit $status). Log: $logFile" -ForegroundColor Red
    }
    return $status
}

$backend = Join-Path $RepoRoot 'backend'
$frontend = Join-Path $RepoRoot 'frontend'

switch ($Task) {
    'build-backend'  { $code = Invoke-Captured 'backend-build'  'build' $backend  'mvn' @('-B', 'clean', 'package', '-DskipTests') }
    'test-backend'   { $code = Invoke-Captured 'backend-test'   'build' $backend  'mvn' @('-B', 'test') }
    'build-frontend' { $code = Invoke-Captured 'frontend-build' 'build' $frontend 'npm' @('run', 'build') }
    'test-frontend'  { $code = Invoke-Captured 'frontend-test'  'build' $frontend 'npm' @('test') }
    # Runtime logging also goes to logs/portiq*.log via logback; this captures startup output and
    # anything written straight to stdout/stderr, which logback never sees.
    'run-backend'    { $code = Invoke-Captured 'backend-run'    'run'   $backend  'mvn' @('-B', 'spring-boot:run') }
    'run-frontend'   { $code = Invoke-Captured 'frontend-run'   'run'   $frontend 'npm' @('run', 'dev') }
    'all' {
        $code = 0
        if ((Invoke-Captured 'backend-test'   'build' $backend  'mvn' @('-B', 'test')) -ne 0) { $code = 1 }
        if ((Invoke-Captured 'backend-build'  'build' $backend  'mvn' @('-B', 'clean', 'package', '-DskipTests')) -ne 0) { $code = 1 }
        if ((Invoke-Captured 'frontend-test'  'build' $frontend 'npm' @('test')) -ne 0) { $code = 1 }
        if ((Invoke-Captured 'frontend-build' 'build' $frontend 'npm' @('run', 'build')) -ne 0) { $code = 1 }
        Write-Host ""
        Write-Host "All logs are under $LogDir\build\"
    }
}

exit $code
