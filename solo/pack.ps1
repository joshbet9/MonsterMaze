# pack.ps1 - Builds the distributable "solo-dist" folder + zip that players unzip
# and double-click to play. Run this AFTER setting up the webhook. Produces:
#   solo-dist\   (a ready-to-run copy of the solo game)
#   solo-dist.zip (send this to players)
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\pack.ps1
#
# Edit the paths below if your JDK8 / spigot jar live somewhere else.

$ErrorActionPreference = "Stop"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$dist = Join-Path $here "solo-dist"

# --- Set these to match your machine ------------------------------------
$JDK8    = "C:\Users\Josh\AppData\Local\Programs\Eclipse Adoptium\jdk-8.0.502.7-hotspot"
$SPIGOT  = "C:\monstermaze_test\spigot-1.8.8.jar"
# ------------------------------------------------------------------------

if (-not (Test-Path (Join-Path $JDK8 "bin\java.exe"))) { Write-Host "JDK8 not found at $JDK8"; exit 1 }
if (-not (Test-Path $SPIGOT)) { Write-Host "spigot jar not found at $SPIGOT"; exit 1 }

# Fresh dist
if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }

# Copy the template (launcher, server config, plugin, docs)
Copy-Item -Recurse -Force (Join-Path $here "launcher")  (Join-Path $dist "launcher")
Copy-Item -Recurse -Force (Join-Path $here "submitter") (Join-Path $dist "submitter")
Copy-Item -Recurse -Force (Join-Path $here "server")    (Join-Path $dist "server")
Copy-Item -Force (Join-Path $here "HOW_TO_PLAY.txt")    (Join-Path $dist "HOW_TO_PLAY.txt")

# Strip anything that should not ship (submitter keeps submitted-archive empty)
$stripServer = @(
    "$dist\server\world", "$dist\server\world_nether", "$dist\server\world_the_end",
    "$dist\server\mm_void", "$dist\server\logs",
    "$dist\server\plugins\MonsterMazeStandalone\solo-runs",
    "$dist\submitter\submitted"
)
foreach ($p in $stripServer) { if (Test-Path $p) { Remove-Item -Recurse -Force $p } }

# Bundle the Java runtime (zips-small enough; this is the zero-friction step)
Copy-Item -Recurse -Force $JDK8 (Join-Path $dist "runtime\jdk8")

# Spigot server jar
Copy-Item -Force $SPIGOT (Join-Path $dist "server\spigot-1.8.8.jar")

Write-Host ""
Write-Host "Staging complete at: $dist"
Write-Host ""

# Now zip it
$zip = Join-Path $here "solo-dist.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($dist, $zip, [System.IO.Compression.CompressionLevel]::Optimal, $true)
$size = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "Created $zip ($size MB) - this is what you send to players."
