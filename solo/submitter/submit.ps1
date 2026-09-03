# Monster Maze SOLO - submitter (Discord webhook only)
# Watches the plugin's solo-runs folder and posts each finished PB run to the
# Discord webhook for that run's mode (see config.ps1). A run is archived to
# "submitted\" only after a successful post, so failures never lose a run.
#
# Usage (double-click play is primary; this is the push step):
#   PowerShell -ExecutionPolicy Bypass "& .\submit.ps1"
# or run it as a Scheduled Task.

$ErrorActionPreference = "Stop"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$server    = Join-Path $here "..\server\plugins\MonsterMazeStandalone\solo-runs"
$submitted = Join-Path $here "submitted"
$configFile = Join-Path $here "config.ps1"

$WEBHOOKS = @{}
$DEFAULT_WEBHOOK = ""
if (Test-Path $configFile) { . $configFile }

$hasAny = ($WEBHOOKS.GetEnumerator() | Where-Object { $_.Value -and $_.Value -ne "" }).Count -gt 0
if (-not $hasAny -and -not $DEFAULT_WEBHOOK) {
    Write-Host "No webhooks configured. Edit submitter\config.ps1 (see README)."
    Write-Host "Runs are still saved locally in: $server"
    exit 0
}

if (-not (Test-Path $server)) { Write-Host "No solo-runs folder yet; nothing to submit."; exit 0 }
New-Item -ItemType Directory -Force -Path $submitted | Out-Null

$files = @(Get-ChildItem -Path $server -Filter *.json -File)
if ($files.Count -eq 0) { Write-Host "Nothing new to submit."; exit 0 }

foreach ($f in $files) {
    $ok = $true
    $raw = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    try { $run = $raw | ConvertFrom-Json } catch {
        Write-Warning ("Skipping {0}: bad JSON - {1}" -f $f.Name, $_.Exception.Message); continue
    }

    # Choose the webhook for this run's mode.
    $modeKey = ([string]$run.mode).ToLower()
    $webhook = $WEBHOOKS[$modeKey]
    if (-not $webhook) { $webhook = $DEFAULT_WEBHOOK }

    if (-not $webhook) {
        Write-Host ("No webhook for mode '{0}' and no default - kept {1}" -f $run.mode, $f.Name)
        continue
    }

    try {
        $kit  = if ($run.kit) { $run.kit } else { "None" }
        $mins = [math]::Floor($run.timeMs / 60000)
        $secs = [math]::Round(($run.timeMs % 60000) / 1000)
        $time = "{0}m {1}s" -f $mins, $secs
        $embed = @{
            title  = "$($run.name) - new PB (stage $($run.stage))"
            color  = 0x33aa66
            fields = @(
                @{ name = "Mode";    value = [string]$run.mode;                 inline = $true },
                @{ name = "Pattern"; value = "Maze $($run.pattern + 1)";          inline = $true },
                @{ name = "Kit";     value = [string]$kit;                        inline = $true },
                @{ name = "Stage";   value = [string]$run.stage;                  inline = $true },
                @{ name = "Time";    value = $time;                               inline = $true }
            )
            footer = @{ text = "uuid $($run.uuid) | configHash $($run.configHash)" }
        }
        $body = @{ content = "New solo PB submitted!"; embeds = @($embed) } | ConvertTo-Json -Depth 5
        $null = Invoke-RestMethod -Uri $webhook -Method Post -ContentType "application/json" -Body $body
        Write-Host ("Posted {0} to {1}: stage {2}" -f $run.name, $run.mode, $run.stage)
        Move-Item -LiteralPath $f.FullName -Destination (Join-Path $submitted $f.Name) -Force
        Write-Host ("Archived {0}" -f $f.Name)
    } catch {
        Write-Warning ("Failed to post {0}: {1}" -f $f.Name, $_.Exception.Message)
        Write-Host ("Kept {0} (will retry next run)" -f $f.Name)
    }
}
