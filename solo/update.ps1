# update.ps1 - Monster Maze SOLO auto-updater
#
# Compares the *installed* version against the manifest published in the GitHub
# repo. Only downloads + atomically replaces files whose hash actually changed,
# then records the new version locally. Non-technical users double-click
# update.bat; this script does all the error handling.
#
# Safety rules (the whole point):
#   - NEVER touches submitter\config.ps1, bot\config.json, or
#     server\plugins\MonsterMazeStandalone\config.yml  (user config / secrets).
#   - NEVER touches worlds, logs, solo-runs\, submitted\, runtime\, or the
#     spigot server jar.
#   - If the Minecraft server is running, it refuses to update rather than
#     silently killing anything.
#   - Every download is verified against the manifest's SHA-256 before the
#     existing file is replaced. A failure aborts with the old files intact.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\update.ps1
#   (or double-click update.bat)

$ErrorActionPreference = "Stop"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$INSTALLED_MARKER = Join-Path $here "installed.version"
$MANIFEST_URL = if ($env:MM_UPDATE_MANIFEST_URL) { $env:MM_UPDATE_MANIFEST_URL } else {
    "https://raw.githubusercontent.com/joshbet9/MonsterMaze/main/solo/version.json"
}

function Write-Step([string]$msg) { Write-Host "[MM-Update] $msg" }

# ---- 1. Load the remote manifest ------------------------------------------
Write-Step "Checking for updates..."
$manifest = $null
try {
    $raw = Invoke-RestMethod -Uri $MANIFEST_URL -TimeoutSec 20
    # Invoke-RestMethod sometimes returns the raw JSON string (BOM/no content
    # type) instead of parsed objects. Normalize both cases defensively.
    if ($raw -is [System.Management.Automation.PSCustomObject] -and $raw."install-version") {
        $manifest = $raw
    } else {
        $txt = [string]$raw
        # Strip a UTF-8 BOM a few ways: some responses decode to U+FEFF, some
        # to the raw bytes misread as latin-1 (\xEF\xBB\xBF). Both are harmless
        # headers, but both break ConvertFrom-Json - so drop them.
        $txt = $txt.TrimStart([char]0xFEFF)
        $txt = $txt -replace "^[\u00EF\u00BB\u00BF]+", ""
        $manifest = ($txt | ConvertFrom-Json)
    }
} catch {
    Write-Host ""
    Write-Host "  Could not reach the update server: $MANIFEST_URL"
    Write-Host "  $($_.Exception.Message)"
    Write-Host "  Your game still works - this only checks for updates."
    Write-Host "  Tip: you need internet for the update check."
    exit 1
}

$remoteVersion = [string]$manifest."install-version"
if (-not $remoteVersion) {
    Write-Host "  The update manifest is missing install-version. Aborting."
    exit 1
}

# ---- 2. Compare installed version ------------------------------------------
$installedVersion = $null
if (Test-Path -LiteralPath $INSTALLED_MARKER) {
    $installedVersion = (Get-Content -LiteralPath $INSTALLED_MARKER -Raw).Trim()
}

if ($installedVersion -eq $remoteVersion) {
    Write-Step "You are on the latest version ($remoteVersion). Nothing to do."
    exit 0
}

Write-Step "Update available: installed=$installedVersion remote=$remoteVersion"
if ($manifest.note) { Write-Host "  What's new: $($manifest.note)" }
Write-Host ""

# ---- 3. Refuse if the server is running ------------------------------------
$running = $false
try {
    $running = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction Stop |
        Where-Object { $_.CommandLine -like "*spigot-1.8.8.jar*" }).Count -gt 0
} catch { $running = $false }
if ($running) {
    Write-Host "  The Minecraft server is currently running."
    Write-Host "  Close it first (double-click launcher\stop.bat) then run update.bat again."
    exit 1
}

# ---- 4. Diff each updateable file; download changed ones --------------------
$rawBase = ($MANIFEST_URL -replace "version\.json$", "")

$toUpdate = @()
foreach ($key in $manifest.files.PSObject.Properties.Name) {
    $entry = $manifest.files.$key
    $localPath = Join-Path $here ($key -replace "/", "\")
    $currentHash = $null
    if (Test-Path -LiteralPath $localPath) {
        $currentHash = (Get-FileHash -LiteralPath $localPath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    if ($currentHash -ne $entry.sha256) {
        $toUpdate += @{ file = $localPath; rel = $key; sha256 = $entry.sha256 }
    }
}

if ($toUpdate.Count -eq 0) {
    Write-Step "No file changes detected for this version. Recording $remoteVersion."
    Set-Content -LiteralPath $INSTALLED_MARKER -Value $remoteVersion -Encoding ascii
    exit 0
}

Write-Step "Downloading $($toUpdate.Count) changed file(s)..."
$tmpDir = Join-Path $here ".update-tmp"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

$failures = 0
$backupDir = Join-Path $here ".update-backup"
$changed = 0
$skip = @()

foreach ($u in $toUpdate) {
    $url = $rawBase + $u.rel
    $dest = $u.file
    $tmp = Join-Path $tmpDir ((Split-Path $u.file -Leaf) + ".tmp")

    # SAFETY GUARD: never touch user config / secrets even if the manifest listed them.
    $guard = $u.rel.ToLowerInvariant()
    if (@("submitter/config.ps1","bot/config.json","server/plugins/monstermazestandalone/config.yml") -contains $guard) {
        Write-Host ("  Skipping protected file: {0} (preserving your settings)" -f $u.rel)
        $skip += $u.rel
        continue
    }

    try {
        Invoke-WebRequest -Uri $url -OutFile $tmp -TimeoutSec 60 -UseBasicParsing
        $dlHash = (Get-FileHash -LiteralPath $tmp -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($dlHash -ne $u.sha256) {
            throw "Hash mismatch: expected $($u.sha256), got $dlHash"
        }

        # Atomic-ish replace: back up existing file, then move new one into place.
        if (Test-Path -LiteralPath $dest) {
            New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
            Copy-Item -LiteralPath $dest -Destination (Join-Path $backupDir (Split-Path $dest -Leaf)) -Force
        } else {
            New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
        }
        Move-Item -LiteralPath $tmp -Destination $dest -Force
        $changed++
        Write-Host ("  Updated {0}" -f $u.rel)
    } catch {
        $failures++
        Write-Warning ("  FAILED {0}: {1}" -f $u.rel, $_.Exception.Message)
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    }
}

# ---- 5. Cleanup and record version -----------------------------------------
Remove-Item -LiteralPath $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
if ($failures -eq 0) {
    Set-Content -LiteralPath $INSTALLED_MARKER -Value $remoteVersion -Encoding ascii
    Write-Host ""
    Write-Step "Update complete: $changed file(s) updated. You are now on $remoteVersion."
} else {
    Write-Host ""
    Write-Step "Update finished with $failures failure(s), $changed updated."
    Write-Host "  Some files may not have updated. You can re-run update.bat to retry."
    Write-Host "  Backups (if any) are in $backupDir"
    exit 1
}