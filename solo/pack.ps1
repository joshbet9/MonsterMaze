# Builds the 1.8 Solo distribution.
[CmdletBinding()]
param(
    [string]$ReleaseVersion = $(if ($env:MM_RELEASE_VERSION) { $env:MM_RELEASE_VERSION } else { "1.0.0" }),
    [string]$ReleaseNote = $(if ($env:MM_RELEASE_NOTE) { $env:MM_RELEASE_NOTE } else { "Monster Maze Solo release." }),
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $here
$sourceProject = Join-Path $repoRoot '1.8/MonsterMazeStandalone'
$sourceJar = Join-Path $sourceProject 'target/MonsterMazeStandalone.jar'
$dist = Join-Path $here 'solo-dist'
$maps = Join-Path $here 'maps'
$SPIGOT = if ($env:MM_SPIGOT_JAR) { $env:MM_SPIGOT_JAR } else { Join-Path $here 'tools/spigot-1.8.8.jar' }

if (-not $SkipBuild) {
    Push-Location $sourceProject
    try {
        & mvn -B clean package
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }
}

if ($env:MM_SOLO_JDK8_WINDOWS) {
    $JDK8 = $env:MM_SOLO_JDK8_WINDOWS
} elseif ($IsWindows -and $env:MM_JDK8) {
    $JDK8 = $env:MM_JDK8
} elseif (-not $IsWindows) {
    $jdkUrl = 'https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u492-b09/OpenJDK8U-jdk_x64_windows_hotspot_8u492b09.zip'
    $jdkZip = Join-Path ([System.IO.Path]::GetTempPath()) 'MonsterMaze-Temurin8-Windows.zip'
    $jdkExtract = Join-Path ([System.IO.Path]::GetTempPath()) 'MonsterMaze-Temurin8-Windows'
    $headers = @{ 'User-Agent' = 'MonsterMaze-Release/1.0 (https://github.com/joshbet9/MonsterMaze)' }
    if (Test-Path $jdkZip) { Remove-Item -Force $jdkZip }
    if (Test-Path $jdkExtract) { Remove-Item -Recurse -Force $jdkExtract }
    Invoke-WebRequest -Uri $jdkUrl -Headers $headers -OutFile $jdkZip
    $checksumText = (Invoke-WebRequest -Uri ($jdkUrl + '.sha256.txt') -Headers $headers).Content
    $checksumMatch = [regex]::Match($checksumText, '(?i)\b[0-9a-f]{64}\b')
    if (-not $checksumMatch.Success) { throw 'Could not parse Windows JDK 8 SHA-256 checksum.' }
    $expectedChecksum = $checksumMatch.Value.ToLowerInvariant()
    $actualChecksum = (Get-FileHash $jdkZip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualChecksum -ne $expectedChecksum) { throw "Windows JDK 8 SHA-256 mismatch." }
    Expand-Archive -LiteralPath $jdkZip -DestinationPath $jdkExtract -Force
    $JDK8 = (Get-ChildItem $jdkExtract -Directory | Select-Object -First 1).FullName
} else {
    $JDK8 = "C:\Users\Josh\AppData\Local\Programs\Eclipse Adoptium\jdk-8.0.502.7-hotspot"
}

$javaExe = if ($IsWindows) { "java.exe" } else { "java.exe" }

if (-not (Test-Path (Join-Path $JDK8 "bin/$javaExe"))) { throw "JDK8 not found at $JDK8" }
if (-not (Test-Path $SPIGOT)) { throw "spigot jar not found at $SPIGOT" }
if (-not (Test-Path $maps)) { throw "Canonical maps directory not found at $maps" }
if (-not (Test-Path (Join-Path $sourceProject "pom.xml"))) { throw "1.8 source project not found at $sourceProject" }

$requiredMaps = @("mm_colombia","mm_sandycoast","mm_siberian","mm_swampland","mm_tesorohundido","mm_void","mm_volcano")
foreach ($map in $requiredMaps) {
    if (-not (Test-Path (Join-Path $maps $map))) { throw "Required map missing: $map" }
}

if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
New-Item -ItemType Directory -Path $dist | Out-Null
New-Item -ItemType Directory -Path (Join-Path $dist 'server/plugins') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $dist 'runtime/jdk8') -Force | Out-Null
Copy-Item $sourceJar (Join-Path $dist 'server/plugins/MonsterMazeStandalone.jar')
Copy-Item $SPIGOT (Join-Path $dist 'server/spigot-1.8.8.jar')
Copy-Item (Join-Path $repoRoot 'solo/server/*') (Join-Path $dist 'server') -Recurse -Force
Copy-Item (Join-Path $JDK8 '*') (Join-Path $dist 'runtime/jdk8') -Recurse -Force
Copy-Item (Join-Path $maps '*') (Join-Path $dist 'server/maps') -Recurse -Force

$version = [ordered]@{
    version = $ReleaseVersion
    minecraft = '1.8.8'
    releaseNote = $ReleaseNote
    plugin = 'MonsterMazeStandalone.jar'
}
$version | ConvertTo-Json | Set-Content (Join-Path $dist 'version.json') -Encoding utf8

$zip = Join-Path $here 'solo-dist.zip'
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path (Join-Path $dist '*') -DestinationPath $zip -CompressionLevel Optimal
Write-Host "Created $zip"
