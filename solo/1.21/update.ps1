# Monster Maze SOLO 1.21 updater
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$marker = Join-Path $here "installed.version"
$manifestUrl = if ($env:MM_UPDATE_MANIFEST_URL) { $env:MM_UPDATE_MANIFEST_URL } else { "https://github.com/joshbet9/MonsterMaze/releases/latest/download/solo-1.21-version.json" }
function Step([string]$m) { Write-Host "[MM-Update] $m" }
Step "Checking for updates..."
try { $manifest = Invoke-RestMethod -Uri $manifestUrl -TimeoutSec 20 } catch { Write-Host "Update server unavailable; continuing without an update."; exit 0 }
$remote = [string]$manifest.'install-version'
if (-not $remote) { Write-Host "Manifest has no install-version; skipping."; exit 0 }
$local = if (Test-Path $marker) { (Get-Content $marker -Raw).Trim() } else { "" }
if ($local -eq $remote) { Step "Already on $remote."; exit 0 }

$running = $false
try { $running = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction Stop | Where-Object { $_.CommandLine -like "*paper-1.21.11.jar*" }).Count -gt 0 } catch {}
if ($running) { Write-Host "Server is running. Close it before updating."; exit 1 }

$base = ($manifestUrl -replace "[^/]+$", "")
$tmpDir = Join-Path $here ".update-tmp"
$backupDir = Join-Path $here ".update-backup"
New-Item -ItemType Directory -Force $tmpDir | Out-Null
$failed = 0
foreach ($key in $manifest.files.PSObject.Properties.Name) {
    $entry = $manifest.files.$key
    $dest = Join-Path $here ($key -replace '/', '\\')
    $hash = if (Test-Path $dest) { (Get-FileHash $dest -Algorithm SHA256).Hash.ToLowerInvariant() } else { "" }
    if ($hash -eq $entry.sha256) { continue }
    $guard = $key.ToLowerInvariant()
    if (@('submitter/config.ps1','server/server.properties','server/plugins/monstermazestandalone/config.yml') -contains $guard) { Write-Host "Preserving $key"; continue }
    $sourceUrl = if ($entry.url) { [string]$entry.url } else {
        $source = $key
        if ($source -match '^server/(mm_[^/]+)(/.*)?$') { $source = 'maps/' + $Matches[1] + $Matches[2] }
        $base + $source
    }
    $tmp = Join-Path $tmpDir (($key -replace '/', '\\') + '.tmp')
    New-Item -ItemType Directory -Force (Split-Path $tmp) | Out-Null
    try {
        Invoke-WebRequest -Uri $sourceUrl -OutFile $tmp -TimeoutSec 120 -UseBasicParsing
        $got = (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($got -ne $entry.sha256) { throw "Hash mismatch" }
        if (Test-Path $dest) {
            $backup = Join-Path $backupDir ($key -replace '/', '\\')
            New-Item -ItemType Directory -Force (Split-Path $backup) | Out-Null
            Copy-Item $dest $backup -Force
        } else { New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null }
        Move-Item $tmp $dest -Force
        Write-Host "Updated $key"
    } catch { Write-Warning "Failed $key : $($_.Exception.Message)"; $failed++ }
}
Remove-Item $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
if ($failed) { Write-Host "$failed file(s) failed; run update.bat again."; exit 1 }
Set-Content $marker $remote -Encoding ascii
Step "Updated to $remote."
