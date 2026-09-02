# Owner-side release manifest generator for solo/1.21.
[CmdletBinding()]
param(
    [string]$Version='0.1.0-1.21',
    [string]$Note='Initial 1.21 Solo release.',
    [string]$Root=''
)
$ErrorActionPreference='Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Root) { $Root = $here }
$files = [ordered]@{}
function Add-File([string]$rel,[string]$src) {
    if (-not (Test-Path $src)) { throw "Missing manifest file: $src" }
    $files[$rel.Replace('\','/')] = [ordered]@{sha256=(Get-FileHash $src -Algorithm SHA256).Hash.ToLowerInvariant();size=(Get-Item $src).Length}
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
foreach ($map in @('mm_colombia','mm_sandycoast','mm_siberian','mm_swampland','mm_tesorohundido','mm_volcano')) {
    $mapRoot = Join-Path $maps $map
    if (-not (Test-Path $mapRoot)) { throw "Converted map missing: $map" }
    Get-ChildItem $mapRoot -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($maps.Length).TrimStart('\','/')
        Add-File ("server/$rel") $_.FullName
    }
}
$manifest = [ordered]@{'install-version'=$Version;note=$Note;updated=(Get-Date -Format "yyyy-MM-dd HH:mm 'UTC'K");files=$files}
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $Root 'version.json'),($manifest|ConvertTo-Json -Depth 10),$utf8)
Write-Host "Wrote version.json with $($files.Count) files."
