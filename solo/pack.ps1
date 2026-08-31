# pack.ps1 - Builds the distributable "solo-dist" folder + zip that players unzip
# and double-click to play. Run this AFTER setting up the webhook. Produces:
#   solo-dist\   (a ready-to-run copy of the solo game)
#   solo-dist.zip (send this to players)
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\pack.ps1
#
# Edit the paths below if your JDK8 / spigot jar live somewhere else.

$ErrorActionPreference = "Stop"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$dist = Join-Path $here "solo-dist"

# --- Set these to match your machine ------------------------------------
$JDK8    = "C:\Users\Josh\AppData\Local\Programs\Eclipse Adoptium\jdk-8.0.502.7-hotspot"
$SPIGOT  = "C:\monstermaze_test\spigot-1.8.8.jar"
# ------------------------------------------------------------------------

if (-not (Test-Path (Join-Path $JDK8 "bin\java.exe"))) { Write-Host "JDK8 not found at $JDK8"; exit 1 }
if (-not (Test-Path $SPIGOT)) { Write-Host "spigot jar not found at $SPIGOT"; exit 1 }

# Fresh dist
if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }

# Copy the template (launcher, server config, plugin, docs)
Copy-Item -Recurse -Force (Join-Path $here "launcher")  (Join-Path $dist "launcher")
Copy-Item -Recurse -Force (Join-Path $here "submitter") (Join-Path $dist "submitter")
Copy-Item -Recurse -Force (Join-Path $here "server")    (Join-Path $dist "server")
Copy-Item -Force (Join-Path $here "HOW_TO_PLAY.txt")    (Join-Path $dist "HOW_TO_PLAY.txt")

# NOTE: per-map arena worlds (WIP, not published yet) are intentionally NOT shipped.
# See 1.8\MonsterMazeStandalone (map work-in-progress) - do not enable the plugin's
# per-map feature in the public solo build until it is finished.


# Auto-updater (version manifest + updater scripts + version marker)
#
# NOTE: version.json must have been generated first. If you have not run
# it, we still ship the updater with whatever manifest exists; a missing
# manifest would make the first update check fail, so regenerate it:
#   powershell -File .\updater_tools\make_manifest.ps1 -Version 1.0.0
$versionJson = Join-Path $here "version.json"
if (Test-Path $versionJson) {
    Copy-Item -Force $versionJson   (Join-Path $dist "version.json")
    Copy-Item -Force (Join-Path $here "update.ps1") (Join-Path $dist "update.ps1")
    Copy-Item -Force (Join-Path $here "update.bat") (Join-Path $dist "update.bat")
    # Installed version marker: a fresh pack is current by definition.
    $v = (Get-Content $versionJson -Raw | ConvertFrom-Json)."install-version"
    Set-Content -LiteralPath (Join-Path $dist "installed.version") -Value $v -Encoding ascii
} else {
    Write-Warning "version.json not found - updater will not ship. Run updater_tools\make_manifest.ps1 first."
}

# Strip anything that should not ship (submitter keeps submitted-archive empty)
$stripServer = @(
    "$dist\server\world", "$dist\server\world_nether", "$dist\server\world_the_end",
    "$dist\server\mm_void", "$dist\server\logs",
    "$dist\server\plugins\MonsterMazeStandalone\solo-runs",
    "$dist\submitter\submitted",
    "$dist\.update-tmp", "$dist\.update-backup"
)
# WIP per-map arena worlds - never ship these in the public solo build.
foreach ($mapName in @("colombia","sandycoast","siberian","swampland","tesorohundido","volcano")) {
    $stripServer += Join-Path "$dist\server" $mapName
}
foreach ($p in ($stripServer | Select-Object -Unique)) { if (Test-Path $p) { Remove-Item -Recurse -Force $p } }

# Bundle the Java runtime (zips-small enough; this is the zero-friction step)
Copy-Item -Recurse -Force $JDK8 (Join-Path $dist "runtime\jdk8")

# Spigot server jar
Copy-Item -Force $SPIGOT (Join-Path $dist "server\spigot-1.8.8.jar")

Write-Host ""
Write-Host "Staging complete at: $dist"
Write-Host ""

# Now zip it.
#
# IMPORTANT: We MUST build the zip with forward-slash (/) entry names and a valid
# end-of-central-directory record. [ZipFile]::CreateFromDirectory on Windows writes
# entries with BACKSLASH (\ ) separators, which is not standard ZIP and causes many
# extractors (7-Zip and others) to error out. So we write the entries ourselves.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $here "solo-dist.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }

$fs = [System.IO.File]::Open($zip, [System.IO.FileMode]::CreateNew)
$archive = New-Object System.IO.Compression.ZipArchive($fs,
    [System.IO.Compression.ZipArchiveMode]::Create)
try {
    # Base entry path so the zip unzips into a "solo-dist" folder.
    $baseName = Split-Path -Leaf $dist          # "solo-dist"
    $toZip = Join-Path $dist "."                # ensure trailing sep off root
    $fileCount = 0
    Get-ChildItem -LiteralPath $dist -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($dist.Length).TrimStart('\','/')
        $entryName = ($baseName + "/" + $relative).Replace('\','/')
        $entry = $archive.CreateEntry($entryName,
            [System.IO.Compression.CompressionLevel]::Optimal)
        $in = $_.OpenRead()
        try {
            $out = $entry.Open()
            try { $in.CopyTo($out) } finally { $out.Dispose() }
        } finally { $in.Dispose() }
        $fileCount++
    }
    Write-Host "Packed $fileCount files (forward-slash names)."
} finally {
    $archive.Dispose()   # flushes and writes the central directory + EOCD
    $fs.Dispose()
}

# Verify the zip really is intact before we tell anyone to use it. A corrupt or
# truncated zip is worse than no zip, so fail hard here if the end-of-central-directory
# record is missing.
$size = [math]::Round((Get-Item $zip).Length / 1MB, 1)
$verify = [System.IO.File]::OpenRead($zip)
try {
    $verify.Seek(-22, [System.IO.SeekOrigin]::End) | Out-Null
    $tail = New-Object byte[] 4
    $verify.Read($tail, 0, 4) | Out-Null
    $okEocd = ($tail[0] -eq 0x50) -and ($tail[1] -eq 0x4B) -and ($tail[2] -eq 0x05) -and ($tail[3] -eq 0x06)
} finally { $verify.Dispose() }

if (-not $okEocd) {
    Write-Host "ERROR: $zip is missing its end-of-central-directory record and is CORRUPT." -ForegroundColor Red
    Write-Host "Do NOT send this zip. Fix the packing error and re-run pack.bat." -ForegroundColor Red
    exit 1
}

# Sample the entry-name separators to be sure we didn't ship backslash names.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$probe = [System.IO.Compression.ZipFile]::OpenRead($zip)
$firstEntry = $probe.Entries | Select-Object -First 1 -ExpandProperty FullName
$probe.Dispose()
if ($firstEntry -match '\\') {
    Write-Host "ERROR: zip contains backslash entry names - extraction will fail in some tools." -ForegroundColor Red
    exit 1
}

Write-Host "Verified: EOCD present, forward-slash names, $size MB - this is what you send to players."
