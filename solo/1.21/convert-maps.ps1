# Converts the canonical 1.8 mm_* worlds into 1.21 world data using Paper.
# Run this before the 1.21 release pack is built.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$canonical = Join-Path $here '..\maps'
$out = Join-Path $here 'maps'
$paper = if ($env:MM_PAPER_JAR) { $env:MM_PAPER_JAR } else { Join-Path $here 'tools\paper-1.21.11.jar' }
$java = if ($env:MM_JAVA_BIN) { $env:MM_JAVA_BIN } else { 'java' }
$maps = @('mm_colombia','mm_sandycoast','mm_siberian','mm_swampland','mm_tesorohundido','mm_volcano')
if (-not (Test-Path $paper)) { throw "Paper 1.21.11 jar not found: $paper" }
if (-not (Test-Path $canonical)) { throw "Canonical map source missing: $canonical" }
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null

foreach ($map in $maps) {
    $source = Join-Path $canonical $map
    $target = Join-Path $out $map
    if (-not (Test-Path (Join-Path $source 'level.dat'))) { throw "Missing level.dat for $map" }
    Write-Host "Preparing $map ..."
    Copy-Item -Recurse -Force $source $target

    $serverProps = @"
accepts-transfers=false
allow-flight=true
difficulty=normal
enable-command-block=false
enable-status=false
gamemode=survival
generate-structures=false
hardcore=false
level-name=$map
max-players=1
motd=Monster Maze map conversion
online-mode=false
pvp=false
server-port=25565
spawn-animals=false
spawn-monsters=false
spawn-npcs=false
view-distance=6
simulation-distance=6
"@
    Set-Content -LiteralPath (Join-Path $here 'tools\server.properties') -Value $serverProps -Encoding ascii

    Push-Location (Join-Path $here 'tools')
    try {
        # The queued 'stop' is consumed after Paper finishes loading the world,
        # causing Paper to save the upgraded Anvil data before exiting.
        cmd.exe /c "echo stop^|\"$java\" -Xmx2G -jar \"$paper\" --nogui --world \"$map\""
        if ($LASTEXITCODE -ne 0) { throw "Paper conversion failed for $map (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
    Remove-Item -LiteralPath (Join-Path $out $map 'session.lock') -Force -ErrorAction SilentlyContinue
    Write-Host "Converted $map."
}

Write-Host ""
Write-Host "1.21 map conversion complete: $out"
Write-Host "These converted mm_* directories are release assets and should be committed with the 1.21 Solo release."
