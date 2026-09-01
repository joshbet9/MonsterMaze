# update.ps1 - Monster Maze SOLO auto-updater
# Downloads only files whose SHA-256 differs from the published manifest.
# Canonical mm_* arena maps are updateable application assets; runtime worlds are not.
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$INSTALLED_MARKER = Join-Path $here "installed.version"
$MANIFEST_URL = if ($env:MM_UPDATE_MANIFEST_URL) { $env:MM_UPDATE_MANIFEST_URL } else { "https://raw.githubusercontent.com/joshbet9/MonsterMaze/main/solo/version.json" }
function Write-Step([string]$msg) { Write-Host "[MM-Update] $msg" }

Write-Step "Checking for updates..."
try {
    $raw = Invoke-RestMethod -Uri $MANIFEST_URL -TimeoutSec 20
    if ($raw -is [System.Management.Automation.PSCustomObject] -and $raw."install-version") { $manifest = $raw }
    else {
        $txt = ([string]$raw).TrimStart([char]0xFEFF) -replace "^[\u00EF\u00BB\u00BF]+", ""
        $manifest = ($txt | ConvertFrom-Json)
    }
} catch {
    Write-Host "  Could not reach the update server: $MANIFEST_URL"
    Write-Host "  $($_.Exception.Message)"
    Write-Host "  Your game still works - this only checks for updates."
    exit 1
}

$remoteVersion = [string]$manifest."install-version"
if (-not $remoteVersion) { Write-Host "  The update manifest is missing install-version. Aborting."; exit 1 }
$installedVersion = $null
if (Test-Path -LiteralPath $INSTALLED_MARKER) { $installedVersion = (Get-Content -LiteralPath $INSTALLED_MARKER -Raw).Trim() }
if ($installedVersion -eq $remoteVersion) { Write-Step "You are on the latest version ($remoteVersion). Nothing to do."; exit 0 }
Write-Step "Update available: installed=$installedVersion remote=$remoteVersion"
if ($manifest.note) { Write-Host "  What's new: $($manifest.note)" }

$running = $false
try { $running = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction Stop | Where-Object { $_.CommandLine -like "*spigot-1.8.8.jar*" }).Count -gt 0 } catch { $running = $false }
if ($running) {
    Write-Host "  The Minecraft server is currently running."
    Write-Host "  Close it first, then run update.bat again."
    exit 1
}

$rawBase = ($MANIFEST_URL -replace "version\.json$", "")
$toUpdate = @()
foreach ($key in $manifest.files.PSObject.Properties.Name) {
    $entry = $manifest.files.$key
    $localPath = Join-Path $here ($key -replace "/", "\")
    $currentHash = $null
    if (Test-Path -LiteralPath $localPath) { $currentHash = (Get-FileHash -LiteralPath $localPath -Algorithm SHA256).Hash.ToLowerInvariant() }
    if ($currentHash -ne $entry.sha256) { $toUpdate += @{ file = $localPath; rel = $key; sha256 = $entry.sha256 } }
}
if ($toUpdate.Count -eq 0) {
    Write-Step "No file changes detected for this version. Recording $remoteVersion."
    Set-Content -LiteralPath $INSTALLED_MARKER -Value $remoteVersion -Encoding ascii
    exit 0
}

Write-Step "Downloading $($toUpdate.Count) changed file(s)..."
$tmpDir = Join-Path $here ".update-tmp"
$backupDir = Join-Path $here ".update-backup"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
$failures = 0
$changed = 0

foreach ($u in $toUpdate) {
    # Preserve the relative directory structure. This matters for maps because
    # every map contains files with identical names such as region/r.0.0.mca.
    $tmp = Join-Path $tmpDir (($u.rel -replace "/", "\") + ".tmp")
    $backup = Join-Path $backupDir ($u.rel -replace "/", "\")
    $dest = $u.file
    New-Item -ItemType Directory -Force -Path (Split-Path $tmp) | Out-Null

    $guard = $u.rel.ToLowerInvariant()
    if (@("submitter/config.ps1","bot/config.json","server/plugins/monstermazestandalone/config.yml") -contains $guard) {
        Write-Host ("  Skipping protected file: {0} (preserving your settings)" -f $u.rel)
        continue
    }

    try {
        Invoke-WebRequest -Uri ($rawBase + $u.rel) -OutFile $tmp -TimeoutSec 120 -UseBasicParsing
        $dlHash = (Get-FileHash -LiteralPath $tmp -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($dlHash -ne $u.sha256) { throw "Hash mismatch: expected $($u.sha256), got $dlHash" }

        if (Test-Path -LiteralPath $dest) {
            New-Item -ItemType Directory -Force -Path (Split-Path $backup) | Out-Null
            Copy-Item -LiteralPath $dest -Destination $backup -Force
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

Remove-Item -LiteralPath $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
if ($failures -eq 0) {
    Set-Content -LiteralPath $INSTALLED_MARKER -Value $remoteVersion -Encoding ascii
    Write-Step "Update complete: $changed file(s) updated. You are now on $remoteVersion."
} else {
    Write-Step "Update finished with $failures failure(s), $changed updated."
    Write-Host "  Re-run update.bat to retry failed files. Backups are in $backupDir"
    exit 1
}
