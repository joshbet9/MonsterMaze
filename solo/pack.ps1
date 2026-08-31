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

# Auto-updater (version manifest + updater scripts + version marker)
#
# NOTE: version.json must have been generated first. If you have not run
# it, we still ship the updater with whatever manifest exists; a missing
# manifest would make the first update check fail, so regenerate it:
#   powershell -File .\updater_tools\make_manifest.ps1 -Version 1.0.0
$versionJson = Join-Path $here "version.json"
if (Test-Path $versionJson) {
    Copy-Item -Force $versionJson   (Join-Path $dist "version.json")
    Copy-Item -Force (Join-Path $here "update.ps1") (Join-Path $dist "update.ps1")
    Copy-Item -Force (Join-Path $here "update.bat") (Join-Path $dist "update.bat")
    # Installed version marker: a fresh pack is current by definition.
    $v = (Get-Content $versionJson -Raw | ConvertFrom-Json)."install-version"
    Set-Content -LiteralPath (Join-Path $dist "installed.version") -Value $v -Encoding ascii
} else {
    Write-Warning "version.json not found - updater will not ship. Run updater_tools\make_manifest.ps1 first."
}

# Strip anything that should not ship (submitter keeps submitted-archive empty)
$stripServer = @(
    "$dist\server\world", "$dist\server\world_nether", "$dist\server\world_the_end",
    "$dist\server\mm_void", "$dist\server\logs",
    "$dist\server\plugins\MonsterMazeStandalone\solo-runs",
    "$dist\submitter\submitted",
    "$dist\.update-tmp", "$dist\.update-backup"
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
