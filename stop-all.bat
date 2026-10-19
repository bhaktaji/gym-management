@echo off
setlocal EnableDelayedExpansion
title GymKhata - Stopper

REM ===========================================================================
REM  stop-all.bat  -  closes the GymKhata services started by start-all.bat
REM ===========================================================================
REM
REM  Two passes, both scoped to GymKhata only:
REM
REM    1. scripts\stop-all.ps1
REM       Stops the processes that the PowerShell launcher tracked, using the
REM       pid AND the recorded start time, so a recycled pid is never killed.
REM
REM    2. A command-line sweep
REM       Catches the console windows that start-all.bat opened, which are not
REM       in the PowerShell tracker's state file. The match is on the GymKhata
REM       jar path and the frontend folder, so unrelated java or node
REM       processes on this machine are never touched.
REM
REM  MongoDB is NOT stopped. It is a shared Windows service, and the launcher
REM  never stops it either.
REM
REM ===========================================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "BACKEND=%ROOT%\backend"
set "FRONTEND=%ROOT%\frontend"

echo.
echo  ============================================================
echo   GymKhata - stopping the stack
echo  ============================================================
echo.

REM ---------------------------------------------------------------------------
REM  Pass 1: the PowerShell tracker's own state
REM ---------------------------------------------------------------------------
if exist "%ROOT%\scripts\stop-all.ps1" (
    echo  [1/2] Stopping tracked processes ^(scripts\stop-all.ps1^)...
    call powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\stop-all.ps1"
    echo.
) else (
    echo  [1/2] scripts\stop-all.ps1 not found - skipping the tracked pass.
    echo.
)

REM ---------------------------------------------------------------------------
REM  Pass 2: anything left that is unmistakably GymKhata
REM ---------------------------------------------------------------------------
echo  [2/2] Closing any remaining GymKhata processes...

REM The marker is the backend jar path and the frontend folder, both taken from
REM this script's own location, so the match is exact for this checkout.
REM The whole PowerShell command is one double-quoted argument, so cmd hands
REM the pipes and braces to PowerShell untouched. $PID skips this very
REM PowerShell process, whose own command line contains the marker.
call :kill_matching "*%BACKEND%\target\*.jar*"
call :kill_matching "*%FRONTEND%*"

echo.
echo  ============================================================
echo   Done.
echo.
echo   MongoDB was left running ^(it is a shared Windows service^).
echo   If a window is still open, close it - the service inside has stopped.
echo  ============================================================
echo.
exit /b 0


:kill_matching
REM  %1 = a wildcard matched against the process command line.
REM  taskkill /T takes the whole tree with it, so the console window and the
REM  program it was running both go away. taskkill's own output is swallowed:
REM  when a child dies with its parent it reports "process not found", which
REM  is noise rather than a real failure.
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -ne $PID -and $_.CommandLine -like '%~1' } | ForEach-Object { Write-Host ('   stopping process ' + $_.ProcessId); taskkill /PID $_.ProcessId /T /F 2>&1 | Out-Null }"
exit /b 0
