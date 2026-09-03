@echo off
REM ============================================================
REM  Monster Maze SOLO - launcher
REM  Windows 7 / 8 / 10 / 11
REM  One double-click to start your own solo Monster Maze server.
REM ============================================================
setlocal
cd /d "%~dp0"

REM ---- Locate Java 8 --------------------------------------------------
REM Order: 1) bundled runtime\jdk8  2) config.bat JAVA_BIN  3) PATH
set "JAVA_BIN=java"
if exist "..\runtime\jdk8\bin\java.exe" set "JAVA_BIN=..\runtime\jdk8\bin\java.exe"
if exist ".\config.bat" call ".\config.bat"

REM java on PATH only (a bare name); resolve to a full path for PowerShell.
if /i "%JAVA_BIN%"=="java" for /f "delims=" %%i in ('where java.exe 2^>nul') do set "JAVA_BIN=%%i" & goto :found
:found

powershell -NoProfile -ExecutionPolicy Bypass -Command "$jb='%JAVA_BIN%'; if (!(Test-Path $jb)) { Write-Host ('Java not found at: ' + $jb); exit 1 }; $ok = & $jb -version 2>&1 | Out-String; if ($LASTEXITCODE -ne 0) { Write-Host 'Java does not run.'; exit 1 }; exit 0"
if errorlevel 1 (
    echo.
    echo  Java 8 was not found.
    echo  Easiest fix: download the bundled "runtime" folder next to solo\launcher.
    echo  Or edit solo\launcher\config.bat and set JAVA_BIN to your Java 8 path.
    echo.
    pause
    exit /b 1
)

echo.
echo  Monster Maze SOLO server starting...
echo  Keep this window open. When you see "Done", open Minecraft 1.8.9,
echo  go to Multiplayer, add a server, and use the address:  localhost
echo.

REM ---- Start the server (detached, minimized) and save its PID -------
cd ..\server
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$jb='%JAVA_BIN%'; if (![System.IO.Path]::IsPathRooted($jb)) { $jb = (Join-Path (Get-Location) $jb) }; $p = Start-Process -FilePath $jb -ArgumentList '-Xmx1G','-jar','spigot-1.8.8.jar','nogui' -WorkingDirectory (Get-Location) -WindowStyle Minimized -PassThru; $p.Id | Out-File -Encoding ascii '..\launcher\server.pid'; Write-Host ('Started server, PID ' + $p.Id)"
echo.
echo  Server is running.
echo  When done, double-click stop.bat (or close the server window).
echo.
pause
