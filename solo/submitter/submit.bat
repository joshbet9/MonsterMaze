@echo off
REM Double-click to post any new solo runs to the Discord leaderboard webhook.
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\submit.ps1"
echo.
pause
