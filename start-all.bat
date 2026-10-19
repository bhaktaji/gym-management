@echo off
setlocal EnableDelayedExpansion
title GymKhata - Launcher

REM ===========================================================================
REM  start-all.bat  -  one-click local launcher for GymKhata
REM ===========================================================================
REM
REM  Starts, in order:
REM    1. MongoDB        (native Windows service - NO Docker)
REM    2. Spring Boot    (prebuilt jar - there is NO mvnw wrapper in this repo)
REM    3. Vite           (React dev server)
REM  then opens the browser once the frontend is actually listening.
REM
REM  Each service gets its own console window, so its logs stay readable and
REM  closing one service does not disturb the others.
REM
REM  ---------------------------------------------------------------------------
REM  THREE THINGS THIS SCRIPT DOES NOT ASSUME, BECAUSE ASSUMING THEM BREAKS IT
REM  ---------------------------------------------------------------------------
REM
REM  1. THE `java` ON PATH IS A JRE 8, NOT A JDK.
REM     The machine's global JAVA_HOME points at a JRE 8, and `java -version`
REM     on PATH reports 1.8.0_504. The jar is compiled for Java 21, so the
REM     backend would die with UnsupportedClassVersionError. This script
REM     LOCATES a real JDK 17+ instead of assuming `java` is usable.
REM     Resolution order: JAVA_HOME - then Microsoft, Eclipse Adoptium, Java,
REM     Amazon Corretto and Zulu install folders - then fail with a message.
REM     The JDK's bin directory is put on PATH for this process only, so the
REM     child consoles can call a bare `java` and never touch the real PATH.
REM
REM  2. THE FRONTEND RUNS ON PORT 5173.
REM     vite.config.ts pins `server.port = 5173`, and the backend CORS
REM     allowlist in application.yml lists http://localhost:5173. Opening
REM     5173 is correct here - do not "fix" it to 3000 without changing both.
REM
REM  3. `npm install` IS NOT RUN ON EVERY LAUNCH.
REM     node_modules is already present, so a full install on each start would
REM     add minutes for no benefit. It runs only when node_modules is missing.
REM
REM  ---------------------------------------------------------------------------
REM  WHAT YOU STILL HAVE TO DO YOURSELF
REM  ---------------------------------------------------------------------------
REM
REM  * The backend runs from the jar in backend\target. If it is not there yet,
REM    build it once with:  scripts\build-all.ps1
REM    (or run this script with the `build` argument:  start-all.bat build)
REM
REM  * MongoDB must be installed as the "MongoDB" Windows service. This script
REM    starts that service if it is stopped, but it never installs MongoDB and
REM    it never deletes or resets any database.
REM
REM  * The app uses a DEVELOPMENT jwt secret by default (app.jwt.secret in
REM    application.yml). That is fine for local work. The `prod` profile refuses
REM    to start with it - set JWT_SECRET for anything non-local.
REM
REM ===========================================================================


REM ---------------------------------------------------------------------------
REM  Resolve paths from this script's own location, so the launcher works no
REM  matter which directory it is invoked from (including double-click).
REM ---------------------------------------------------------------------------
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "BACKEND=%ROOT%\backend"
set "FRONTEND=%ROOT%\frontend"

REM Port 5173 - see note 2 in the header. Changing this without also changing
REM vite.config.ts and the backend CORS allowlist will break the frontend.
set "FRONTEND_URL=http://localhost:5173"
set "BACKEND_URL=http://localhost:8080"
set "BACKEND_HEALTH=%BACKEND_URL%/actuator/health"
set "MONGO_PORT=27017"

echo.
echo  ============================================================
echo   GymKhata - local launcher
echo  ============================================================
echo   Root     : %ROOT%
echo   Backend  : %BACKEND%
echo   Frontend : %FRONTEND%
echo.

REM ---------------------------------------------------------------------------
REM  Fail fast: verify the tree before opening a single window. Discovering a
REM  missing directory after three consoles have already spawned is much worse
REM  than refusing to start.
REM ---------------------------------------------------------------------------
if not exist "%BACKEND%\pom.xml" (
    echo  [ERROR] Not found: "%BACKEND%\pom.xml"
    echo          Run this script from the repository root, or move it back there.
    goto :fail
)
if not exist "%FRONTEND%\package.json" (
    echo  [ERROR] Not found: "%FRONTEND%\package.json"
    echo          The frontend directory is missing or incomplete.
    goto :fail
)

REM The jar is the backend artifact. Look for any jar in target, so a version
REM bump does not break the launcher. A *.jar.original file is not matched by
REM the *.jar glob. The `if exist` guard rejects the literal glob when the
REM folder holds no jar at all.
set "JAR="
for %%J in ("%BACKEND%\target\*.jar") do if exist "%%~fJ" set "JAR=%%~fJ"
if not defined JAR (
    echo  [ERROR] No backend jar in "%BACKEND%\target".
    echo.
    echo          Build it first:
    echo              scripts\build-all.ps1
    echo          or start with the build option:
    echo              start-all.bat build
    goto :fail
)

REM `build` as the first argument means: build, then start.
if /i "%~1"=="build" (
    echo  [BUILD] Building the project ^(scripts\build-all.ps1^)...
    REM `call` on a .ps1 would make cmd try to run it as a batch file, so the
    REM PowerShell host is invoked explicitly and the exit code is checked.
    call powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\build-all.ps1"
    if errorlevel 1 (
        echo  [ERROR] The build failed. Nothing was started.
        goto :fail
    )
    set "JAR="
    for %%J in ("%BACKEND%\target\*.jar") do if exist "%%~fJ" set "JAR=%%~fJ"
    if not defined JAR (
        echo  [ERROR] The build finished but no jar was produced.
        goto :fail
    )
    echo.
)

REM node / npm must exist for the frontend window.
where node >nul 2>&1
if errorlevel 1 (
    echo  [ERROR] node was not found on this machine.
    echo          Install Node.js 18 or newer, then run this again.
    goto :fail
)
where npm >nul 2>&1
if errorlevel 1 (
    echo  [ERROR] npm was not found on this machine.
    echo          Install Node.js 18 or newer, then run this again.
    goto :fail
)


REM ===========================================================================
REM  0. Locate a real JDK 17+  (see note 1 in the header - PATH `java` is JRE 8)
REM ===========================================================================
set "FOUND_JDK="

if defined JAVA_HOME call :check_jdk "%JAVA_HOME%"
if defined FOUND_JDK echo  [OK]    JDK found in JAVA_HOME: %FOUND_JDK%

if not defined FOUND_JDK for /d %%D in ("%ProgramFiles%\Microsoft\jdk-*") do call :check_jdk "%%~fD"
if not defined FOUND_JDK for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-*") do call :check_jdk "%%~fD"
if not defined FOUND_JDK for /d %%D in ("%ProgramFiles%\Java\jdk-*") do call :check_jdk "%%~fD"
if not defined FOUND_JDK for /d %%D in ("%ProgramFiles%\Amazon Corretto\jdk*") do call :check_jdk "%%~fD"
if not defined FOUND_JDK for /d %%D in ("%ProgramFiles%\Zulu\jdk*") do call :check_jdk "%%~fD"

if not defined FOUND_JDK (
    echo.
    echo  [ERROR] No JDK 17 or newer could be located.
    echo.
    echo          The `java` on PATH is a JRE 8, which cannot run this jar.    echo          Install a JDK 17+ ^(21 recommended^) and run this again.
    goto :fail
)

REM Put the JDK's bin directory FIRST on PATH for this process only. The child
REM consoles inherit this environment, so they can call a bare `java` - which
REM sidesteps the "quotes inside quotes" problem that `start ... cmd /k` has.
REM The real system PATH is never modified.
for %%I in ("%FOUND_JDK%\bin") do set "JDK_BIN=%%~fI"
set "PATH=%JDK_BIN%;%PATH%"
echo  [OK]    JDK on PATH for this run: %JDK_BIN%


REM ===========================================================================
REM  1. MongoDB  -  native Windows service, NO Docker
REM ===========================================================================
echo.
echo  [1/4] Checking MongoDB...

call :probe_port %MONGO_PORT%
if errorlevel 1 goto :MONGO_NEEDS_START
echo  [OK]    MongoDB is already listening on port %MONGO_PORT% - reusing it.
goto :MONGO_READY

:MONGO_NEEDS_START
echo         Not listening on port %MONGO_PORT% - trying the Windows service...
sc query MongoDB >nul 2>&1
if errorlevel 1 (
    echo.
    echo  [ERROR] MongoDB is not running and no "MongoDB" Windows service
    echo          was found on this machine.
    echo.
    echo          Install MongoDB 9 as the "MongoDB" service, or start your own
    echo          mongod on port %MONGO_PORT%, then run this again.
    goto :fail
)
net start MongoDB

set /a MONGO_TRIES=0
:MONGO_WAIT
set /a MONGO_TRIES+=1
call :probe_port %MONGO_PORT%
if not errorlevel 1 goto :MONGO_READY
if %MONGO_TRIES% GEQ 30 (
    echo  [ERROR] MongoDB did not become ready within 60 seconds.
    echo          Check the MongoDB service, then run this again.
    goto :fail
)
call :sleep 2
goto :MONGO_WAIT

:MONGO_READY
echo  [OK]    MongoDB is ready on port %MONGO_PORT%.


REM ===========================================================================
REM  2. Spring Boot backend  -  prebuilt jar, NO wrapper
REM ===========================================================================
echo.
echo  [2/4] Starting the GymKhata backend...

REM `java` is bare on purpose - the JDK's bin directory was prepended to PATH
REM above and the child console inherits it. /D sets the working directory so
REM the app's relative paths resolve next to the jar. cmd /k keeps the window
REM open after the app exits, so any stack trace stays readable.
start "GymKhata Backend" /D "%BACKEND%" cmd /k "java -jar "%JAR%""


REM ===========================================================================
REM  3. Vite frontend  -  install only when needed
REM ===========================================================================
echo  [3/4] Starting the GymKhata frontend...

REM `cd /d` is inside the command (and not only the /D switch) so the console
REM command line carries the frontend path. That lets stop-all.bat find and
REM close exactly this window later, and it works even if the path has spaces.
start "GymKhata Frontend" /D "%FRONTEND%" cmd /k "cd /d "%FRONTEND%" && if exist node_modules (npm run dev) else (npm install && npm run dev)"


REM ===========================================================================
REM  4. Wait for the backend and the frontend, then open the browser
REM ===========================================================================
echo.
echo  [4/4] Waiting for the backend and the frontend to come up...

REM Poll instead of guessing. A fixed sleep either opens the browser too early
REM (nothing listening yet) or wastes time on a warm start.
set /a PROBES=0
:WAIT_BACKEND
set /a PROBES+=1
call :http_has "%BACKEND_HEALTH%" "UP"
if not errorlevel 1 goto :BACKEND_UP
if %PROBES% GEQ 60 (
    echo  [WARN] The backend did not answer within 120s.
    echo         Check the "GymKhata Backend" window for a stack trace.
    goto :WAIT_FRONTEND
)
call :sleep 2
goto :WAIT_BACKEND

:BACKEND_UP
echo         Backend answered after %PROBES% probe^(s^).

set /a PROBES=0
:WAIT_FRONTEND
set /a PROBES+=1
call :http_has "%FRONTEND_URL%" "root"
if not errorlevel 1 goto :FRONTEND_UP
if %PROBES% GEQ 30 (
    echo  [WARN] The frontend did not answer within 60s.
    echo         Check the "GymKhata Frontend" window for a compile error.
    goto :OPEN_BROWSER
)
call :sleep 2
goto :WAIT_FRONTEND

:FRONTEND_UP
echo         Frontend answered after %PROBES% probe^(s^).

:OPEN_BROWSER
echo.
echo  Opening %FRONTEND_URL%
REM The empty "" is the window title argument - without it, `start` treats the
REM URL as the title and opens nothing at all.
start "" "%FRONTEND_URL%"

echo.
echo  ============================================================
echo   GymKhata is starting. Two windows were opened:
echo     "GymKhata Backend"   - Spring Boot on :8080
echo     "GymKhata Frontend"  - Vite on :5173
echo.
echo   App      %FRONTEND_URL%
echo   API      %BACKEND_HEALTH%
echo   Swagger  %BACKEND_URL%/swagger-ui.html
echo   Admin    %BACKEND_URL%/admin
echo.
echo   Login: super admin +919000000001 / ChangeMe@123
echo           ^(the built-in defaults - override with SUPER_ADMIN_PHONE and
echo            SUPER_ADMIN_PASSWORD before first boot^)
echo.
echo   Stop the stack with:  stop-all.bat
echo  ============================================================
echo.
echo  This window can be closed. The services keep running in theirs.
REM A plain `exit /b` here - NOT `exit` - so double-clicking the script closes
REM this window instead of terminating the console hosting the other services.
exit /b 0


:fail
echo.
echo  Launch aborted. No services were started.
echo.
pause
exit /b 1


REM ===========================================================================
REM  Subroutines
REM ===========================================================================

:check_jdk
REM  %1 = a candidate JDK home. Sets FOUND_JDK when it is a JDK 17 or newer.
REM  A JRE has no bin\javac.exe, so that alone rules out the JRE on PATH.
set "CAND=%~1"
if not exist "%CAND%\bin\java.exe" exit /b 1
if not exist "%CAND%\bin\javac.exe" exit /b 1

REM Call the candidate's own java by putting its bin first on PATH for a
REM moment. That avoids quoting a path that may contain spaces inside a
REM for /f command, which cmd mangles.
set "SAVED_PATH=%PATH%"
set "PATH=%CAND%\bin;%PATH%"
set "MAJV="
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do if not defined MAJV set "MAJV=%%~V"
set "PATH=%SAVED_PATH%"
if not defined MAJV exit /b 1

REM "21.0.12.1" has to become 21, so split the version on the dot.
set "MAJ="
for /f "tokens=1 delims=." %%V in ("%MAJV%") do set "MAJ=%%V"
if not defined MAJ exit /b 1
if %MAJ% GEQ 17 (
    set "FOUND_JDK=%CAND%"
    exit /b 0
)
exit /b 1

:probe_port
REM  %1 = a port number. Errorlevel 0 when something is listening on it.
REM  A real TCP connect, not a netstat string match: MongoDB's listening
REM  socket does not always show up with a LISTENING state in netstat.
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient;$c.Connect('127.0.0.1',%1);$c.Close();exit 0}catch{exit 1}" >nul 2>&1
exit /b %errorlevel%

:http_has
REM  %1 = url, %2 = text that must appear in the body. Errorlevel 0 on a hit.
where curl >nul 2>&1
if errorlevel 1 exit /b 1
curl -s --max-time 3 "%~1" 2>nul | findstr /i "%~2" >nul 2>&1
exit /b %errorlevel%

:sleep
REM  %1 = seconds. `timeout` needs a real console, so fall back to PowerShell
REM  when the script runs with its input redirected.
timeout /t %1 /nobreak >nul 2>&1
if errorlevel 1 powershell -NoProfile -Command "Start-Sleep -Seconds %~1" >nul 2>&1
exit /b 0
