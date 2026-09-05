# make_manifest.ps1 - Owner-side tool. Regenerates solo/version.json.
[CmdletBinding()]
param(
    [string]$Version = "1.0.0",
    [string]$Note = "Initial release.",
    [string]$SourceBaseUrl = ""
)
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$soloRoot = Split-Path -Parent $here

$UPDATEABLE = @(
    "launcher\play.bat", "launcher\stop.bat", "launcher\config.bat",
    "submitter\submit.bat", "submitter\submit.ps1",
    "server\plugins\MonsterMazeStandalone.jar",
    "server\bukkit.yml", "server\eula.txt", "server\server.properties", "server\spigot.yml",
    "HOW_TO_PLAY.txt", "README.md"
)

function Add-ManifestFile([System.Collections.IDictionary]$files, [string]$manifestPath, [string]$sourcePath, [string]$sourceRel = "") {
    if (-not (Test-Path -LiteralPath $sourcePath)) { throw "Updateable file missing: $sourcePath" }
    $norm = $manifestPath -replace "\\", "/"
    $entry = [ordered]@{
        sha256 = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
        size = (Get-Item -LiteralPath $sourcePath).Length
    }
    if ($SourceBaseUrl) {
        if (-not $sourceRel) { $sourceRel = $norm }
        $entry.url = ($SourceBaseUrl.TrimEnd('/') + '/' + ($sourceRel -replace "\\", "/"))
    }
    $files[$norm] = $entry
}

$files = [ordered]@{}
foreach ($rel in $UPDATEABLE) {
    Add-ManifestFile $files $rel (Join-Path $soloRoot $rel)
}

$mapsRoot = Join-Path $soloRoot "maps"
if (-not (Test-Path $mapsRoot)) { throw "Canonical maps directory missing: $mapsRoot" }
$mapNames = @("mm_colombia","mm_sandycoast","mm_siberian","mm_swampland","mm_tesorohundido","mm_void","mm_volcano")
foreach ($map in $mapNames) {
    $mapRoot = Join-Path $mapsRoot $map
    if (-not (Test-Path $mapRoot)) { throw "Required map missing: $map" }
    Get-ChildItem -LiteralPath $mapRoot -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($mapsRoot.Length).TrimStart('\','/')
        Add-ManifestFile $files ("server\$relative") $_.FullName ("maps\$relative")
    }
}

$manifest = [ordered]@{
    "install-version" = $Version
    "note" = $Note
    "updated" = (Get-Date -Format "yyyy-MM-dd HH:mm 'UTC'K")
    "files" = $files
}
$out = Join-Path $soloRoot "version.json"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($out, ($manifest | ConvertTo-Json -Depth 10), $utf8NoBom)
Write-Host "Wrote $out (install-version=$Version, $($files.Count) files)"
