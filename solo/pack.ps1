# pack.ps1 - Builds the distributable "solo-dist" folder + zip that players unzip
# and double-click to play.
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$dist = Join-Path $here "solo-dist"
$JDK8 = "C:\Users\Josh\AppData\Local\Programs\Eclipse Adoptium\jdk-8.0.502.7-hotspot"
$SPIGOT = "C:\monstermaze_test\spigot-1.8.8.jar"
$maps = Join-Path $here "maps"

if (-not (Test-Path (Join-Path $JDK8 "bin\java.exe"))) { Write-Host "JDK8 not found at $JDK8"; exit 1 }
if (-not (Test-Path $SPIGOT)) { Write-Host "spigot jar not found at $SPIGOT"; exit 1 }
if (-not (Test-Path $maps)) { Write-Host "Canonical maps directory not found at $maps"; exit 1 }

$requiredMaps = @("mm_colombia","mm_sandycoast","mm_siberian","mm_swampland","mm_tesorohundido","mm_void","mm_volcano")
foreach ($map in $requiredMaps) {
    if (-not (Test-Path (Join-Path $maps $map))) { throw "Required map missing: $map" }
}

if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
Copy-Item -Recurse -Force (Join-Path $here "launcher") (Join-Path $dist "launcher")
Copy-Item -Recurse -Force (Join-Path $here "submitter") (Join-Path $dist "submitter")
Copy-Item -Recurse -Force (Join-Path $here "server") (Join-Path $dist "server")
Copy-Item -Force (Join-Path $here "HOW_TO_PLAY.txt") (Join-Path $dist "HOW_TO_PLAY.txt")

# Canonical tested arena worlds. These are application assets, not runtime worlds.
foreach ($map in $requiredMaps) {
    Copy-Item -Recurse -Force (Join-Path $maps $map) (Join-Path $dist "server\$map")
}

$versionJson = Join-Path $here "version.json"
if (Test-Path $versionJson) {
    Copy-Item -Force $versionJson (Join-Path $dist "version.json")
    Copy-Item -Force (Join-Path $here "update.ps1") (Join-Path $dist "update.ps1")
    Copy-Item -Force (Join-Path $here "update.bat") (Join-Path $dist "update.bat")
    $v = (Get-Content $versionJson -Raw | ConvertFrom-Json)."install-version"
    Set-Content -LiteralPath (Join-Path $dist "installed.version") -Value $v -Encoding ascii
} else {
    Write-Warning "version.json not found - regenerate it with updater_tools\make_manifest.ps1."
}

# Strip generated/runtime state, but deliberately KEEP the canonical mm_* arena maps.
$stripServer = @(
    "$dist\server\world", "$dist\server\world_nether", "$dist\server\world_the_end",
    "$dist\server\logs", "$dist\server\plugins\MonsterMazeStandalone\solo-runs"
)
$strip = @($stripServer + @(
    "$dist\submitter\submitted", "$dist\.update-tmp", "$dist\.update-backup"
))
foreach ($p in ($strip | Select-Object -Unique)) { if (Test-Path $p) { Remove-Item -Recurse -Force $p } }

Copy-Item -Recurse -Force $JDK8 (Join-Path $dist "runtime\jdk8")
Copy-Item -Force $SPIGOT (Join-Path $dist "server\spigot-1.8.8.jar")

Write-Host "Staging complete at: $dist"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $here "solo-dist.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
$fs = [System.IO.File]::Open($zip, [System.IO.FileMode]::CreateNew)
$archive = New-Object System.IO.Compression.ZipArchive($fs,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    $baseName = Split-Path -Leaf $dist
    $fileCount = 0
    Get-ChildItem -LiteralPath $dist -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($dist.Length).TrimStart('\','/')
        $entryName = ($baseName + "/" + $relative).Replace('\','/')
        $entry = $archive.CreateEntry($entryName,[System.IO.Compression.CompressionLevel]::Optimal)
        $in = $_.OpenRead()
        try { $out = $entry.Open(); try { $in.CopyTo($out) } finally { $out.Dispose() } } finally { $in.Dispose() }
        $fileCount++
    }
    Write-Host "Packed $fileCount files."
} finally { $archive.Dispose(); $fs.Dispose() }

$size = [math]::Round((Get-Item $zip).Length / 1MB, 1)
$verify = [System.IO.File]::OpenRead($zip)
try {
    $verify.Seek(-22,[System.IO.SeekOrigin]::End) | Out-Null
    $tail = New-Object byte[] 4
    $verify.Read($tail,0,4) | Out-Null
    $okEocd = ($tail[0] -eq 0x50) -and ($tail[1] -eq 0x4B) -and ($tail[2] -eq 0x05) -and ($tail[3] -eq 0x06)
} finally { $verify.Dispose() }
if (-not $okEocd) { throw "ZIP is missing its end-of-central-directory record." }
$probe = [System.IO.Compression.ZipFile]::OpenRead($zip)
try { $bad = @($probe.Entries | Where-Object { $_.FullName -match '\\' }).Count -gt 0 } finally { $probe.Dispose() }
if ($bad) { throw "ZIP contains backslash entry names." }
Write-Host "Verified: EOCD present, forward-slash names, $size MB."
