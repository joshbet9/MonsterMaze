@echo off
REM Monster Maze SOLO 1.21 launcher
setlocal
cd /d "%~dp0"

if exist "..\update.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "..\update.ps1"
    echo.
)

set "JAVA_BIN=java"
if exist "..\runtime\jdk21\bin\java.exe" set "JAVA_BIN=..\runtime\jdk21\bin\java.exe"
if exist ".\config.bat" call ".\config.bat"

if /i "%JAVA_BIN%"=="java" for /f "delims=" %%i in ('where java.exe 2^>nul') do set "JAVA_BIN=%%i" & goto :found
:found

powershell -NoProfile -ExecutionPolicy Bypass -Command "$jb='%JAVA_BIN%'; if (!(Test-Path $jb)) { Write-Host ('Java not found at: ' + $jb); exit 1 }; $v = & $jb -version 2>&1 | Out-String; if ($LASTEXITCODE -ne 0) { Write-Host 'Java does not run.'; exit 1 }; if ($v -notmatch 'version \"21\.') { Write-Host 'Monster Maze Solo 1.21 requires Java 21.'; Write-Host $v; exit 1 }; exit 0"
if errorlevel 1 (
    echo.
    echo Java 21 was not found.
    echo The public release includes a bundled runtime\jdk21 folder.
    echo Or set JAVA_BIN in launcher\config.bat.
    pause
    exit /b 1
)

echo.
echo Monster Maze SOLO 1.21 server starting...
echo When the server is ready, join Minecraft 1.21.11 at localhost.
echo.

cd ..\server
powershell -NoProfile -ExecutionPolicy Bypass -Command "$jb='%JAVA_BIN%'; if (![System.IO.Path]::IsPathRooted($jb)) { $jb = (Join-Path (Get-Location) $jb) }; $p = Start-Process -FilePath $jb -ArgumentList '-Xmx2G','-jar','paper-1.21.11.jar','--nogui' -WorkingDirectory (Get-Location) -WindowStyle Minimized -PassThru; $p.Id | Out-File -Encoding ascii '..\launcher\server.pid'; Write-Host ('Started server, PID ' + $p.Id)"
echo.
echo Server is running.
echo Double-click launcher\stop.bat when you are finished.
echo.
pause
