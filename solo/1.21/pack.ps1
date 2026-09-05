# Builds the 1.21 Solo distribution.
[CmdletBinding()]
param(
    [string]$ReleaseVersion = $(if ($env:MM_RELEASE_VERSION) { $env:MM_RELEASE_VERSION } else { "1.0.0" }),
    [string]$ReleaseNote = $(if ($env:MM_RELEASE_NOTE) { $env:MM_RELEASE_NOTE } else { "Monster Maze Solo 1.21 release." }),
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $here)
$project = Join-Path $repoRoot '1.21/MonsterMazeStandalone'
$sourceJar = Join-Path $project 'target/MonsterMazeStandalone.jar'
$dist = Join-Path $here 'solo-dist'
$maps = Join-Path $here 'maps'
$paper = if ($env:MM_PAPER_JAR) { $env:MM_PAPER_JAR } else {
    $preferredPaper = Join-Path $here 'tools/paper-1.21.11.jar'
    $genericPaper = Join-Path $here 'tools/paper.jar'
    if (Test-Path $preferredPaper) { $preferredPaper } elseif (Test-Path $genericPaper) { $genericPaper } else { $preferredPaper }
}
$protocol = if ($env:MM_PROTOCOLLIB_JAR) { $env:MM_PROTOCOLLIB_JAR } else { Join-Path $here 'tools/ProtocolLib.jar' }

if ($env:MM_SOLO_JDK21_WINDOWS) {
    $jdk21 = $env:MM_SOLO_JDK21_WINDOWS
} elseif ($IsWindows -and $env:MM_JDK21) {
    $jdk21 = $env:MM_JDK21
} elseif (-not $IsWindows) {
    $jdkUrl = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip'
    $jdkZip = Join-Path ([System.IO.Path]::GetTempPath()) 'MonsterMaze-Temurin21-Windows.zip'
    $jdkExtract = Join-Path ([System.IO.Path]::GetTempPath()) 'MonsterMaze-Temurin21-Windows'
    $headers = @{ 'User-Agent' = 'MonsterMaze-Release/1.0 (https://github.com/joshbet9/MonsterMaze)' }
    if (Test-Path $jdkZip) { Remove-Item -Force $jdkZip }
    if (Test-Path $jdkExtract) { Remove-Item -Recurse -Force $jdkExtract }
    Invoke-WebRequest -Uri $jdkUrl -Headers $headers -OutFile $jdkZip
    if ((Get-Item $jdkZip).Length -lt 50000000) { throw 'Windows JDK 21 download is unexpectedly small.' }
    Expand-Archive -LiteralPath $jdkZip -DestinationPath $jdkExtract -Force
    $jdk21 = (Get-ChildItem $jdkExtract -Directory | Select-Object -First 1).FullName
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
            $javaExe = Join-Path $javaHome 'bin/java.exe'
            if ((Test-Path $javaExe) -and ((& $javaExe -version 2>&1) -match 'version "21')) {
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
if (-not (Test-Path (Join-Path $jdk21 'bin/java.exe'))) { throw "Windows JDK 21 runtime is missing bin/java.exe: $jdk21" }
foreach ($map in $requiredMaps) {
    $mapPath = Join-Path $maps $map
    if (-not (Test-Path (Join-Path $mapPath 'level.dat'))) { throw "1.21 map world missing or invalid: $map" }
}

if (-not $SkipBuild) {
    Write-Host "Building 1.21 MonsterMazeStandalone..."
    Push-Location $project
    try {
        & mvn clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }
} else {
    Write-Host "Using existing tested 1.21 plugin build (--SkipBuild)."
}
if (-not (Test-Path $sourceJar)) { throw "Expected built plugin JAR was not found: $sourceJar" }

if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $dist 'server/plugins/MonsterMazeStandalone') | Out-Null
Copy-Item (Join-Path $here 'launcher') (Join-Path $dist 'launcher') -Recurse -Force
Copy-Item (Join-Path $here 'submitter') (Join-Path $dist 'submitter') -Recurse -Force
Copy-Item (Join-Path $here 'HOW_TO_PLAY.txt') (Join-Path $dist 'HOW_TO_PLAY.txt')
Copy-Item (Join-Path $here 'update.ps1') (Join-Path $dist 'update.ps1')
Copy-Item (Join-Path $here 'update.bat') (Join-Path $dist 'update.bat')
Copy-Item (Join-Path $here 'README.md') (Join-Path $dist 'README.md')
Copy-Item (Join-Path $here 'server/server.properties') (Join-Path $dist 'server/server.properties')
Copy-Item (Join-Path $here 'server/eula.txt') (Join-Path $dist 'server/eula.txt')
Copy-Item (Join-Path $here 'server/plugins/MonsterMazeStandalone/config.yml') (Join-Path $dist 'server/plugins/MonsterMazeStandalone/config.yml')
Copy-Item $sourceJar (Join-Path $dist 'server/plugins/MonsterMazeStandalone.jar')
Copy-Item $paper (Join-Path $dist 'server/paper-1.21.11.jar')
Copy-Item $protocol (Join-Path $dist 'server/plugins/ProtocolLib.jar')
Copy-Item $jdk21 (Join-Path $dist 'runtime/jdk21') -Recurse -Force
foreach ($map in $requiredMaps) { Copy-Item (Join-Path $maps $map) (Join-Path $dist "server/$map") -Recurse -Force }

Remove-Item (Join-Path $dist 'submitter/submitted') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $dist 'server/plugins/MonsterMazeStandalone/solo-runs') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $dist 'server/world') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $dist 'server/logs') -Recurse -Force -ErrorAction SilentlyContinue

$pwsh = (Get-Command pwsh -ErrorAction SilentlyContinue).Source
if (-not $pwsh) { $pwsh = (Get-Command powershell -ErrorAction Stop).Source }
& $pwsh -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'make_manifest.ps1') -Version $ReleaseVersion -Note $ReleaseNote -Root $dist -SourceBaseUrl $env:MM_RELEASE_SOURCE_BASE_URL -ReleaseAssetBaseUrl $env:MM_RELEASE_ASSET_BASE_URL
if ($LASTEXITCODE -ne 0) { throw "Manifest generation failed." }
Copy-Item -Force (Join-Path $dist 'version.json') (Join-Path $here 'version.json')
Set-Content (Join-Path $dist 'installed.version') -Value $ReleaseVersion -Encoding ascii

$zip = Join-Path $here 'solo-1.21-dist.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
Add-Type -AssemblyName System.IO.Compression
$fs = [System.IO.File]::Open($zip, [System.IO.FileMode]::CreateNew)
$archive = New-Object System.IO.Compression.ZipArchive($fs,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    $fileCount = 0
    Get-ChildItem -LiteralPath $dist -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($dist.Length).TrimStart([char]92,[char]47)
        $entryName = $relative.Replace([char]92,[char]47)
        $entry = $archive.CreateEntry($entryName,[System.IO.Compression.CompressionLevel]::Optimal)
        $in = $_.OpenRead()
        try { $out = $entry.Open(); try { $in.CopyTo($out) } finally { $out.Dispose() } } finally { $in.Dispose() }
        $fileCount++
    }
    Write-Host "Packed $fileCount files."
} finally { $archive.Dispose(); $fs.Dispose() }

$size = [math]::Round((Get-Item $zip).Length / 1MB, 1)
$probe = [System.IO.Compression.ZipFile]::OpenRead($zip)
try {
    $entries = @($probe.Entries | ForEach-Object { $_.FullName })
    $bad = @($entries | Where-Object { $_ -match '\\\\' }).Count -gt 0
    $requiredRoots = @('server/','launcher/','submitter/','runtime/')
    $missingRoots = @($requiredRoots | Where-Object { $root = $_; -not (@($entries | Where-Object { $_.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase) }).Count) })
    $hasForbiddenWrapper = @($entries | Where-Object { $_ -like 'solo-dist/*' }).Count -gt 0
    $hasManifest = @($entries | Where-Object { $_ -eq 'version.json' }).Count -eq 1
    $hasMarker = @($entries | Where-Object { $_ -eq 'installed.version' }).Count -eq 1
} finally { $probe.Dispose() }
if ($bad) { throw "ZIP contains backslash entry names." }
if ($missingRoots.Count -gt 0) { throw "ZIP is missing required root path(s): $($missingRoots -join ', ')" }
if ($hasForbiddenWrapper) { throw "ZIP contains an unexpected solo-dist/ wrapper directory." }
if (-not $hasManifest) { throw "ZIP is missing root version.json." }
if (-not $hasMarker) { throw "ZIP is missing root installed.version." }

$verify = [System.IO.File]::OpenRead($zip)
try {
    if ($verify.Length -lt 22) { throw "ZIP is too small to contain an end-of-central-directory record." }
    $verify.Seek(-22,[System.IO.SeekOrigin]::End) | Out-Null
    $tail = New-Object byte[] 4
    $verify.Read($tail,0,4) | Out-Null
    $okEocd = ($tail[0] -eq 0x50) -and ($tail[1] -eq 0x4B) -and ($tail[2] -eq 0x05) -and ($tail[3] -eq 0x06)
} finally { $verify.Dispose() }
if (-not $okEocd) { throw "ZIP is missing its end-of-central-directory record." }
Write-Host "Built: $zip"
Write-Host "Verified: updater-compatible root paths, manifest + version marker present, EOCD present, forward-slash names, $size MB."