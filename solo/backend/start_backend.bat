@echo off
REM Starts the Monster Maze solo leaderboard backend on port 8123.
REM Keep this window open. It must stay running for the leaderboard to update.
setlocal
cd /d "%~dp0"
echo  Monster Maze backend starting on port 8123...
echo  (Keep this window open.)
python monster_backend.py 8123
pause
