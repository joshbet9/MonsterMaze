# Builds the 1.21 Solo distribution.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $here)
$project = Join-Path $repoRoot '1.21\MonsterMazeStandalone'
$sourceJar = Join-Path $project 'target\MonsterMazeStandalone.jar'
$dist = Join-Path $here 'solo-dist'
$maps = Join-Path $here 'maps'
$releaseVersion = '1.0.6'
$releaseNote = 'Current Monster Maze Solo implementation: competitive backend, seasonal ratings, tournaments, challenge functionality, and release-package fixes.'
$paper = if ($env:MM_PAPER_JAR) { $env:MM_PAPER_JAR } else {
    $preferredPaper = Join-Path $here 'tools\paper-1.21.11.jar'
    $genericPaper = Join-Path $here 'tools\paper.jar'
    if (Test-Path $preferredPaper) { $preferredPaper } elseif (Test-Path $genericPaper) { $genericPaper } else { $preferredPaper }
}
$protocol = if ($env:MM_PROTOCOLLIB_JAR) { $env:MM_PROTOCOLLIB_JAR } else { Join-Path $here 'tools\ProtocolLib.jar' }

# Prefer an explicitly supplied JDK, otherwise discover an installed Temurin/OpenJDK 21.
if ($env:MM_JDK21) {
    $jdk21 = $env:MM_JDK21
} else {
    $jdkRoots = @(
        'C:\Users\Josh\AppData\Local\Programs\Eclipse Adoptium',
        'C:\Program Files\Eclipse Adoptium'
    )
    $jdk21 = $null
    foreach ($root in $jdkRoots) {
        if (Test-Path $root) {
            $candidate = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '^jdk-21(?:\.|$)' } |
                Sort-Object Name -Descending |
                Select-Object -First 1
            if ($candidate) { $jdk21 = $candidate.FullName; break }
        }
    }
    if (-not $jdk21) {
        $java = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($java) {
            $javaHome = Split-Path -Parent (Split-Path -Parent $java.Source)
            if ((Test-Path (Join-Path $javaHome 'bin\java.exe')) -and ((& (Join-Path $javaHome 'bin\java.exe') -version 2>&1) -match 'version "21')) {
                $jdk21 = $javaHome
            }
        }
    }
}

$requiredMaps = @('mm_colombia','mm_sandycoast','mm_siberian','mm_swampland','mm_tesorohundido','mm_volcano','mm_void')

if (-not (Test-Path (Join-Path $project 'pom.xml'))) { throw "1.21 source project not found: $project" }
if (-not (Test-Path $paper)) { throw "Paper 1.21.11 jar not found: $paper" }
if (-not (Test-Path $protocol)) { throw "ProtocolLib.jar not found: $protocol" }
if (-not $jdk21 -or -not (Test-Path $jdk21)) { throw "JDK 21 directory not found. Set MM_JDK21 or install a JDK 21." }
foreach ($map in $requiredMaps) {
    $mapPath = Join-Path $maps $map
    if (-not (Test-Path (Join-Path $mapPath 'level.dat'))) { throw "1.21 map world missing or invalid: $map" }
}

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
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'make_manifest.ps1') -Version $releaseVersion -Note $releaseNote -Root $dist
if ($LASTEXITCODE -ne 0) { throw "Manifest generation failed." }
# Keep the raw GitHub manifest synchronized too, so existing client updaters see this release.
Copy-Item -Force (Join-Path $dist 'version.json') (Join-Path $here 'version.json')
Set-Content (Join-Path $dist 'installed.version') -Value $releaseVersion -Encoding ascii

$zip = Join-Path $here 'solo-1.21-dist.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $dist '*') -DestinationPath $zip -CompressionLevel Optimal

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$probe = [System.IO.Compression.ZipFile]::OpenRead($zip)
try {
    $entries = @($probe.Entries | ForEach-Object { $_.FullName })
    $bad = @($entries | Where-Object { $_ -match '\\' }).Count -gt 0
    $requiredRoots = @('server/','launcher/','submitter/','runtime/')
    $missingRoots = @($requiredRoots | Where-Object {
        $root = $_
        -not (@($entries | Where-Object { $_.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase) }).Count)
    })
    $hasForbiddenWrapper = @($entries | Where-Object { $_ -like 'solo-dist/*' }).Count -gt 0
    $hasManifest = @($entries | Where-Object { $_ -eq 'version.json' }).Count -eq 1
    $hasMarker = @($entries | Where-Object { $_ -eq 'installed.version' }).Count -eq 1
} finally { $probe.Dispose() }
if ($bad) { throw "ZIP contains backslash entry names." }
if ($missingRoots.Count -gt 0) { throw "ZIP is missing required root path(s): $($missingRoots -join ', ')" }
if ($hasForbiddenWrapper) { throw "ZIP contains an unexpected solo-dist/ wrapper directory." }
if (-not $hasManifest) { throw "ZIP is missing root version.json." }
if (-not $hasMarker) { throw "ZIP is missing root installed.version." }

$size = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "Built: $zip"
Write-Host "Verified: updater-compatible root paths, manifest + version marker present, forward-slash names, $size MB."
