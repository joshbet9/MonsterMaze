# Builds the 1.21 Solo distribution.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $here)
$project = Join-Path $repoRoot '1.21\MonsterMazeStandalone'
$sourceJar = Join-Path $project 'target\MonsterMazeStandalone.jar'
$dist = Join-Path $here 'solo-dist'
$maps = Join-Path $here 'maps'
$paper = if ($env:MM_PAPER_JAR) { $env:MM_PAPER_JAR } else { Join-Path $here 'tools\paper-1.21.11.jar' }
$protocol = if ($env:MM_PROTOCOLLIB_JAR) { $env:MM_PROTOCOLLIB_JAR } else { Join-Path $here 'tools\ProtocolLib.jar' }
$jdk21 = if ($env:MM_JDK21) { $env:MM_JDK21 } else { 'C:\Users\Josh\AppData\Local\Programs\Eclipse Adoptium\jdk-21' }
$requiredMaps = @('mm_colombia','mm_sandycoast','mm_siberian','mm_swampland','mm_tesorohundido','mm_volcano')

if (-not (Test-Path (Join-Path $project 'pom.xml'))) { throw "1.21 source project not found: $project" }
if (-not (Test-Path $paper)) { throw "Paper 1.21.11 jar not found: $paper" }
if (-not (Test-Path $protocol)) { throw "ProtocolLib.jar not found: $protocol" }
if (-not (Test-Path $jdk21)) { throw "JDK 21 directory not found: $jdk21" }
foreach ($map in $requiredMaps) { if (-not (Test-Path (Join-Path $maps $map))) { throw "Converted 1.21 map missing: $map. Run convert-maps.ps1 first." } }

Write-Host "Building 1.21 MonsterMazeStandalone..."
Push-Location $project
try {
    & mvn.cmd clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }
if (-not (Test-Path $sourceJar)) { throw "Build succeeded but JAR was not produced." }

if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $dist 'server\plugins\MonsterMazeStandalone') | Out-Null
Copy-Item (Join-Path $here 'launcher') (Join-Path $dist 'launcher') -Recurse -Force
Copy-Item (Join-Path $here 'submitter') (Join-Path $dist 'submitter') -Recurse -Force
Copy-Item (Join-Path $here 'HOW_TO_PLAY.txt') (Join-Path $dist 'HOW_TO_PLAY.txt')
Copy-Item (Join-Path $here 'update.ps1') (Join-Path $dist 'update.ps1')
Copy-Item (Join-Path $here 'update.bat') (Join-Path $dist 'update.bat')
Copy-Item (Join-Path $here 'README.md') (Join-Path $dist 'README.md')
Copy-Item (Join-Path $here 'server\server.properties') (Join-Path $dist 'server\server.properties')
Copy-Item (Join-Path $here 'server\eula.txt') (Join-Path $dist 'server\eula.txt')
Copy-Item (Join-Path $here 'server\plugins\MonsterMazeStandalone\config.yml') (Join-Path $dist 'server\plugins\MonsterMazeStandalone\config.yml')
Copy-Item $sourceJar (Join-Path $dist 'server\plugins\MonsterMazeStandalone.jar')
Copy-Item $paper (Join-Path $dist 'server\paper-1.21.11.jar')
Copy-Item $protocol (Join-Path $dist 'server\plugins\ProtocolLib.jar')
Copy-Item $jdk21 (Join-Path $dist 'runtime\jdk21') -Recurse -Force
foreach ($map in $requiredMaps) { Copy-Item (Join-Path $maps $map) (Join-Path $dist "server\$map") -Recurse -Force }

# Keep user data out of a clean release.
Remove-Item (Join-Path $dist 'submitter\submitted') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $dist 'server\plugins\MonsterMazeStandalone\solo-runs') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $dist 'server\world') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $dist 'server\logs') -Recurse -Force -ErrorAction SilentlyContinue

# Generate the updater manifest from exactly what will be shipped.
$version = if (Test-Path (Join-Path $here 'version.json')) { [string](Get-Content (Join-Path $here 'version.json') -Raw | ConvertFrom-Json).'install-version' } else { '0.1.0-1.21' }
$note = if (Test-Path (Join-Path $here 'version.json')) { [string](Get-Content (Join-Path $here 'version.json') -Raw | ConvertFrom-Json).note } else { 'Initial 1.21 Solo release.' }
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'make_manifest.ps1') -Version $version -Note $note -Root $dist
if ($LASTEXITCODE -ne 0) { throw "Manifest generation failed." }
Set-Content (Join-Path $dist 'installed.version') -Value $version -Encoding ascii

$zip = Join-Path $here 'solo-1.21-dist.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $dist '*') -DestinationPath $zip -CompressionLevel Optimal
Write-Host "Built: $zip"
