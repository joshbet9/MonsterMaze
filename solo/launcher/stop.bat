@echo off
REM Stops the local solo Monster Maze server started by play.bat.
REM Uses the PID saved by play.bat (server.pid) - works on Win10 & Win11 (no wmic).
setlocal
cd /d "%~dp0"

echo  Stopping Monster Maze SOLO server...

if exist "server.pid" (
    set /p SERVER_PID=<server.pid
    set "SERVER_PID=%SERVER_PID: =%"
    taskkill /PID %SERVER_PID% /T /F >nul 2>&1
    if not errorlevel 1 echo  Server stopped (PID %SERVER_PID%).
    del server.pid >nul 2>&1
)

REM Secondary: stop any remaining java running the spigot jar, using PowerShell
REM (CimInstance is the modern replacement for wmic and works on Win11).
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*spigot-1.8.8.jar*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"

echo  Done. It is safe to close the remaining windows.
pause
