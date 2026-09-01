# make_manifest.ps1 - Owner-side tool. Regenerates solo/version.json.
[CmdletBinding()]
param(
    [string]$Version = "1.0.0",
    [string]$Note = "Initial release."
)
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$soloRoot = Split-Path -Parent $here

# Complete set the auto-updater may replace. Paths are relative to solo/.
$UPDATEABLE = @(
    "launcher\play.bat", "launcher\stop.bat", "launcher\config.bat",
    "submitter\submit.bat", "submitter\submit.ps1",
    "server\plugins\MonsterMazeStandalone.jar",
    "server\bukkit.yml", "server\eula.txt", "server\server.properties", "server\spigot.yml",
    "HOW_TO_PLAY.txt", "README.md"
)

# Canonical arena maps are application assets. Include every file recursively so
# a changed map can be delivered by the normal hash-based updater.
$mapsRoot = Join-Path $soloRoot "maps"
if (-not (Test-Path $mapsRoot)) { throw "Canonical maps directory missing: $mapsRoot" }
$mapNames = @("mm_colombia","mm_sandycoast","mm_siberian","mm_swampland","mm_tesorohundido","mm_void","mm_volcano")
foreach ($map in $mapNames) {
    $mapRoot = Join-Path $mapsRoot $map
    if (-not (Test-Path $mapRoot)) { throw "Required map missing: $map" }
    Get-ChildItem -LiteralPath $mapRoot -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($mapsRoot.Length).TrimStart('\','/')
        $UPDATEABLE += "server\$rel"
    }
}

function Get-Sha256([string]$path) {
    $full = Join-Path $soloRoot $path
    if (-not (Test-Path -LiteralPath $full)) { throw "Updateable file missing: $path" }
    return (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant()
}

$files = [ordered]@{}
foreach ($rel in ($UPDATEABLE | Select-Object -Unique)) {
    $full = Join-Path $soloRoot $rel
    $norm = $rel -replace "\\", "/"
    $files[$norm] = @{ sha256 = Get-Sha256 $rel; size = (Get-Item -LiteralPath $full).Length }
}

$manifest = @{
    "install-version" = $Version
    "note" = $Note
    "updated" = (Get-Date -Format "yyyy-MM-dd HH:mm 'UTC'K")
    "files" = $files
}
$out = Join-Path $soloRoot "version.json"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($out, ($manifest | ConvertTo-Json -Depth 6), $utf8NoBom)
Write-Host "Wrote $out (install-version=$Version, $($files.Count) files)"
