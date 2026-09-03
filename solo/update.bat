@echo off
REM ============================================================
REM  Monster Maze SOLO - update
REM  Double-click to check for and install game updates.
REM  Your scores, settings, and Discord setup are never touched.
REM  Close the Minecraft server first if it is running.
REM ============================================================
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\update.ps1"
echo.
echo  (Press any key to close this window.)
pause >nul