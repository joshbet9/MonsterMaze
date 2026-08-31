# make_manifest.ps1 - Owner-side tool. Regenerates solo/version.json after you
# change game files and want to cut a new release.
#
# What it does:
#   1. Hashes every file listed in UPDATEABLE below (the set the auto-updater
#      is allowed to replace on a player's machine).
#   2. Writes solo/version.json containing that hash table + install-version.
#
# What it does NOT touch (the updater also never touches these):
#   - submitter/config.ps1   (Discord webhook - user secret)
#   - bot/config.json        (Discord bot token  - user secret)
#   - server/.../config.yml  (mode choice        - user config)
#   - worlds, logs, solo-runs/, submitted/, runtime/, spigot jar
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\make_manifest.ps1
#   powershell -ExecutionPolicy Bypass -File .\make_manifest.ps1 -Version 1.0.1 -Note "Slowball patch"
#
# Commit solo/version.json afterwards. The updater compares install-version,
# then replaces any file whose hash differs from this manifest.

[CmdletBinding()]
param(
    # New install-version for this release (default 1.0.0).
    [string]$Version = "1.0.0",
    # Short human note shown to players in the changelog.
    [string]$Note = "Initial release."
)

$ErrorActionPreference = "Stop"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$soloRoot = Split-Path -Parent $here        # solo/

# The complete set the auto-updater may replace. Paths are relative to solo/.
# Add any new updateable file here (and to the same list in update.ps1).
$UPDATEABLE = @(
    # Launcher
    "launcher\play.bat",
    "launcher\stop.bat",
    "launcher\config.bat",
    # Submitter
    "submitter\submit.bat",
    "submitter\submit.ps1",
    # Server template (plugin jar, server configs)
    "server\plugins\MonsterMazeStandalone.jar",
    "server\bukkit.yml",
    "server\eula.txt",
    "server\server.properties",
    "server\spigot.yml",
    # Docs
    "HOW_TO_PLAY.txt",
    "README.md"
)

function Get-Sha256([string]$path) {
    $full = Join-Path $soloRoot $path
    if (-not (Test-Path -LiteralPath $full)) {
        throw "Updateable file missing: $path"
    }
    $hash = Get-FileHash -LiteralPath $full -Algorithm SHA256
    return $hash.Hash.ToLowerInvariant()
}

$files = @{}
foreach ($rel in $UPDATEABLE) {
    $norm = $rel -replace "\\", "/"
    $files[$norm] = @{
        sha256 = (Get-Sha256 $rel)
        size   = (Get-Item -LiteralPath (Join-Path $soloRoot $rel)).Length
    }
}

$manifest = @{
    "install-version" = $Version
    "note"            = $Note
    "updated"         = (Get-Date -Format "yyyy-MM-dd HH:mm 'UTC'K")
    "files"           = $files
}

$out = Join-Path $soloRoot "version.json"
# UTF-8 WITHOUT BOM (PowerShell 5.1's Set-Content -Encoding UTF8 writes a BOM,
# which breaks ConvertFrom-Json on some clients). Use UTF8Encoding(false).
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($out, ($manifest | ConvertTo-Json -Depth 4), $utf8NoBom)
Write-Host "Wrote $out (install-version=$Version, $($files.Count) files)"

# The updater NEVER replaces these even though they sit next to updateable files.
Write-Host "The updater never replaces: submitter\config.ps1, bot\config.json, server\plugins\MonsterMazeStandalone\config.yml"