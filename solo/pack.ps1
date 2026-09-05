# pack.ps1 - Builds the distributable "solo-dist" folder + zip that players unzip
# and double-click to play.
[CmdletBinding()]
param(
    [string]$ReleaseVersion = $(if ($env:MM_RELEASE_VERSION) { $env:MM_RELEASE_VERSION } else { "1.0.0" }),
    [string]$ReleaseNote = $(if ($env:MM_RELEASE_NOTE) { $env:MM_RELEASE_NOTE } else { "Monster Maze Solo release." }),
    [switch]$SkipBuild
)
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $here
$dist = Join-Path $here "solo-dist"
$SPIGOT = if ($env:MM_SPIGOT_JAR) { $env:MM_SPIGOT_JAR } else { "C:\monstermaze_test\spigot-1.8.8.jar" }
$maps = Join-Path $here "maps"
$sourceProject = Join-Path $repoRoot "1.8/MonsterMazeStandalone"
$sourceJar = Join-Path $sourceProject "target/MonsterMazeStandalone.jar"
$soloJar = Join-Path $here "server/plugins/MonsterMazeStandalone.jar"
$maven = "mvn"

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
    if ((Get-Item $jdkZip).Length -lt 50000000) { throw 'Windows JDK 8 download is unexpectedly small.' }
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

if (-not $SkipBuild) {
    Write-Host "Building 1.8 MonsterMazeStandalone from $sourceProject ..."
    Push-Location $sourceProject
    try {
        & $maven clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }
} else {
    Write-Host "Using existing tested 1.8 plugin build (--SkipBuild)."
}

if (-not (Test-Path $sourceJar)) { throw "Expected built plugin JAR was not found: $sourceJar" }
New-Item -ItemType Directory -Force -Path (Split-Path $soloJar) | Out-Null
Copy-Item -Force $sourceJar $soloJar
Write-Host "Copied canonical plugin JAR to solo/server/plugins/MonsterMazeStandalone.jar"

if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
Copy-Item -Recurse -Force (Join-Path $here "launcher") (Join-Path $dist "launcher")
Copy-Item -Recurse -Force (Join-Path $here "submitter") (Join-Path $dist "submitter")
Copy-Item -Recurse -Force (Join-Path $here "server") (Join-Path $dist "server")
Copy-Item -Force (Join-Path $here "HOW_TO_PLAY.txt") (Join-Path $dist "HOW_TO_PLAY.txt")

foreach ($map in $requiredMaps) {
    Copy-Item -Recurse -Force (Join-Path $maps $map) (Join-Path $dist "server/$map")
}

$stripServer = @(
    (Join-Path $dist "server/world"), (Join-Path $dist "server/world_nether"), (Join-Path $dist "server/world_the_end"),
    (Join-Path $dist "server/logs"), (Join-Path $dist "server/plugins/MonsterMazeStandalone/solo-runs")
)
$strip = @($stripServer + @(
    (Join-Path $dist "submitter/submitted"), (Join-Path $dist ".update-tmp"), (Join-Path $dist ".update-backup")
))
foreach ($p in ($strip | Select-Object -Unique)) { if (Test-Path $p) { Remove-Item -Recurse -Force $p } }

Copy-Item -Recurse -Force $JDK8 (Join-Path $dist "runtime/jdk8")
Copy-Item -Force $SPIGOT (Join-Path $dist "server/spigot-1.8.8.jar")

$manifestTool = Join-Path $here "updater_tools/make_manifest.ps1"
$versionFile = Join-Path $here "version.json"
if (-not (Test-Path $manifestTool)) { throw "Manifest tool not found: $manifestTool" }
if (-not (Test-Path $versionFile)) { throw "version.json not found: $versionFile" }
$pwsh = (Get-Command pwsh -ErrorAction SilentlyContinue).Source
if (-not $pwsh) { $pwsh = (Get-Command powershell -ErrorAction Stop).Source }
& $pwsh -NoProfile -ExecutionPolicy Bypass -File $manifestTool -Version $ReleaseVersion -Note $ReleaseNote -SourceBaseUrl $env:MM_RELEASE_SOURCE_BASE_URL -ReleaseAssetBaseUrl $env:MM_RELEASE_ASSET_BASE_URL
if ($LASTEXITCODE -ne 0) { throw "Manifest generation failed with exit code $LASTEXITCODE." }

Copy-Item -Force $versionFile (Join-Path $dist "version.json")
Copy-Item -Force (Join-Path $here "update.ps1") (Join-Path $dist "update.ps1")
Copy-Item -Force (Join-Path $here "update.bat") (Join-Path $dist "update.bat")
Set-Content -LiteralPath (Join-Path $dist "installed.version") -Value $ReleaseVersion -Encoding ascii

Write-Host "Staging complete at: $dist"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $here "solo-dist.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
$fs = [System.IO.File]::Open($zip, [System.IO.FileMode]::CreateNew)
$archive = New-Object System.IO.Compression.ZipArchive($fs,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    $fileCount = 0
    Get-ChildItem -LiteralPath $dist -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($dist.Length).TrimStart('\\','/')
        $entryName = $relative.Replace('\\','/')
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
    $missingRoots = @($requiredRoots | Where-Object {
        $root = $_
        -not (@($entries | Where-Object { $_.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase) }).Count)
    })
    $hasForbiddenWrapper = @($entries | Where-Object { $_ -like 'solo-dist/*' }).Count -gt 0
} finally { $probe.Dispose() }
if ($bad) { throw "ZIP contains backslash entry names." }
if ($missingRoots.Count -gt 0) { throw "ZIP is missing required root path(s): $($missingRoots -join ', ')" }
if ($hasForbiddenWrapper) { throw "ZIP contains an unexpected solo-dist/ wrapper directory." }

$verify = [System.IO.File]::OpenRead($zip)
try {
    $verify.Seek(-22,[System.IO.SeekOrigin]::End) | Out-Null
    $tail = New-Object byte[] 4
    $verify.Read($tail,0,4) | Out-Null
    $okEocd = ($tail[0] -eq 0x50) -and ($tail[1] -eq 0x4B) -and ($tail[2] -eq 0x05) -and ($tail[3] -eq 0x06)
} finally { $verify.Dispose() }
if (-not $okEocd) { throw "ZIP is missing its end-of-central-directory record." }
Write-Host "Verified: updater-compatible root paths, EOCD present, forward-slash names, $size MB."