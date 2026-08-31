@echo off
rem Run the Monster Maze leaderboard bot in the foreground (for testing).
rem For always-on hosting, use a process manager on the host instead.
cd /d "%~dp0"
set PYTHON=python
if exist "venv\Scripts\python.exe" set PYTHON=venv\Scripts\python.exe
%PYTHON% monster_bot.py
pause
