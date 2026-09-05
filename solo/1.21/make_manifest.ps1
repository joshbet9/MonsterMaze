# Owner-side release manifest generator for solo/1.21.
[CmdletBinding()]
param(
    [string]$Version='1.0.0',
    [string]$Note='Monster Maze Solo 1.21 release.',
    [string]$Root='',
    [string]$SourceBaseUrl=''
)
$ErrorActionPreference='Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Root) { $Root = $here }
$files = [ordered]@{}
function Add-File([string]$rel,[string]$src,[string]$sourceRel='') {
    if (-not (Test-Path $src)) { throw "Missing manifest file: $src" }
    $norm = $rel.Replace('\','/')
    $entry = [ordered]@{sha256=(Get-FileHash $src -Algorithm SHA256).Hash.ToLowerInvariant();size=(Get-Item $src).Length}
    if ($SourceBaseUrl) {
        if (-not $sourceRel) { $sourceRel = $norm }
        $entry.url = $SourceBaseUrl.TrimEnd('/') + '/' + $sourceRel.Replace('\','/')
    }
    $files[$norm] = $entry
}
$updateable = @(
 'launcher/play.bat','launcher/stop.bat','launcher/config.bat',
 'submitter/submit.bat','submitter/submit.ps1',
 'server/plugins/MonsterMazeStandalone.jar','server/plugins/ProtocolLib.jar',
 'server/paper-1.21.11.jar','server/eula.txt','server/server.properties',
 'HOW_TO_PLAY.txt','README.md'
)
foreach ($rel in $updateable) { Add-File $rel (Join-Path $Root $rel) }
$maps = Join-Path $Root 'server'
foreach ($map in @('mm_colombia','mm_sandycoast','mm_siberian','mm_swampland','mm_tesorohundido','mm_volcano','mm_void')) {
    $mapRoot = Join-Path $maps $map
    if (-not (Test-Path (Join-Path $mapRoot 'level.dat'))) { throw "1.21 map missing or invalid: $map" }
    Get-ChildItem $mapRoot -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($maps.Length).TrimStart('\','/')
        Add-File ("server/$rel") $_.FullName ("maps/$rel")
    }
}
$manifest = [ordered]@{'install-version'=$Version;note=$Note;updated=(Get-Date -Format "yyyy-MM-dd HH:mm 'UTC'K");files=$files}
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $Root 'version.json'),($manifest|ConvertTo-Json -Depth 10),$utf8)
Write-Host "Wrote version.json with $($files.Count) files."
