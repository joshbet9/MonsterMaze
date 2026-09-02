# Monster Maze SOLO 1.21 submitter
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$server = Join-Path $here "..\server\plugins\MonsterMazeStandalone\solo-runs"
$submitted = Join-Path $here "submitted"
$configFile = Join-Path $here "config.ps1"
$WEBHOOKS = @{}
$DEFAULT_WEBHOOK = ""
if (Test-Path $configFile) { . $configFile }
if ((-not $DEFAULT_WEBHOOK) -and (($WEBHOOKS.GetEnumerator() | Where-Object { $_.Value }).Count -eq 0)) { Write-Host "No webhooks configured. Copy config.ps1.example to config.ps1."; exit 0 }
if (-not (Test-Path $server)) { Write-Host "No solo-runs folder yet; nothing to submit."; exit 0 }
New-Item -ItemType Directory -Force $submitted | Out-Null
foreach ($f in @(Get-ChildItem $server -Filter *.json -File)) {
    try { $run = [System.IO.File]::ReadAllText($f.FullName,[Text.Encoding]::UTF8) | ConvertFrom-Json } catch { Write-Warning "Skipping $($f.Name): bad JSON"; continue }
    $mode = ([string]$run.mode).ToLowerInvariant(); $webhook = $WEBHOOKS[$mode]; if (-not $webhook) { $webhook = $DEFAULT_WEBHOOK }
    if (-not $webhook) { Write-Host "No webhook for $mode; kept $($f.Name)"; continue }
    try {
        $kit = if ($run.kit) { $run.kit } else { 'None' }
        $mins = [math]::Floor($run.timeMs / 60000); $secs = [math]::Round(($run.timeMs % 60000) / 1000); $time = "{0}m {1}s" -f $mins,$secs
        $embed = @{ title="$($run.name) - new PB (stage $($run.stage))"; color=0x33aa66; fields=@(
            @{name='Mode';value=[string]$run.mode;inline=$true}, @{name='Pattern';value="Maze $($run.pattern + 1)";inline=$true}, @{name='Kit';value=[string]$kit;inline=$true}, @{name='Stage';value=[string]$run.stage;inline=$true}, @{name='Time';value=$time;inline=$true}
        ); footer=@{text="uuid $($run.uuid) | configHash $($run.configHash)"} }
        $body = @{content='New solo PB submitted!';embeds=@($embed)} | ConvertTo-Json -Depth 5
        Invoke-RestMethod -Uri $webhook -Method Post -ContentType 'application/json' -Body $body | Out-Null
        Move-Item $f.FullName (Join-Path $submitted $f.Name) -Force
        Write-Host "Posted $($f.Name)"
    } catch { Write-Warning "Failed $($f.Name): $($_.Exception.Message)" }
}
