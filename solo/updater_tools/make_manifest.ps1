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

function Add-ManifestFile([hashtable]$files, [string]$manifestPath, [string]$sourcePath) {
    if (-not (Test-Path -LiteralPath $sourcePath)) { throw "Updateable file missing: $sourcePath" }
    $norm = $manifestPath -replace "\\", "/"
    $files[$norm] = @{
        sha256 = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
        size = (Get-Item -LiteralPath $sourcePath).Length
    }
}

$files = [ordered]@{}
foreach ($rel in $UPDATEABLE) {
    Add-ManifestFile $files $rel (Join-Path $soloRoot $rel)
}

# Canonical arena maps live in solo/maps, but are installed under server/mm_*.
# Hash the canonical source directly so the manifest can be generated before pack.ps1.
$mapsRoot = Join-Path $soloRoot "maps"
if (-not (Test-Path $mapsRoot)) { throw "Canonical maps directory missing: $mapsRoot" }
$mapNames = @("mm_colombia","mm_sandycoast","mm_siberian","mm_swampland","mm_tesorohundido","mm_void","mm_volcano")
foreach ($map in $mapNames) {
    $mapRoot = Join-Path $mapsRoot $map
    if (-not (Test-Path $mapRoot)) { throw "Required map missing: $map" }
    Get-ChildItem -LiteralPath $mapRoot -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($mapsRoot.Length).TrimStart('\','/')
        Add-ManifestFile $files ("server\$relative") $_.FullName
    }
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
