@echo off
REM Double-click to build the distributable solo-dist.zip that you send to players.
REM (Requires the paths in pack.ps1 to point at your JDK8 and spigot jar.)
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\pack.ps1"
echo.
pause
