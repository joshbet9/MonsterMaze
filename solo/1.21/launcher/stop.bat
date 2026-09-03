@echo off
setlocal
cd /d "%~dp0"
set "PIDFILE=server.pid"
if not exist "%PIDFILE%" (
    echo No saved server PID found.
    pause
    exit /b 0
)
set /p PID=<"%PIDFILE%"
if "%PID%"=="" del "%PIDFILE%" & exit /b 0
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = Get-Process -Id %PID% -ErrorAction SilentlyContinue; if ($p) { Stop-Process -Id %PID% -Force; Write-Host 'Stopped Monster Maze Solo server.' } else { Write-Host 'Server process is no longer running.' }"
del "%PIDFILE%" 2>nul
echo.
pause
