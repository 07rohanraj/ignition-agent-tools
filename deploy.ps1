# Deploys ai-agent-tools to the local Ignition 8.3 gateway.
# Ignition 8.3 ignores dropped .modl files unless registered in data\modules.json,
# so this script stops the gateway, registers/updates the entry, and restarts.
#
# Usage: .\deploy.ps1 [-SkipBuild]
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$projectDir   = $PSScriptRoot
$ignitionHome = "C:\Program Files\Inductive Automation\Ignition"
$modlSource   = Join-Path $projectDir "build\ai-agent-tools.unsigned.modl"
$modlTarget   = Join-Path $ignitionHome "user-lib\modules\ai-agent-tools.unsigned.modl"
$modulesJson  = Join-Path $ignitionHome "data\modules.json"
$moduleId     = "com.axcend.ignition.agenttools"
$healthUrl    = "http://localhost:8088/data/agent-tools/health"

if (-not $SkipBuild) {
    Write-Host "== Building =="
    # 'clean' is required: the modl plugin can leave a stale build\moduleContent
    # and skip re-assembly even when the gateway jar has changed.
    & (Join-Path $projectDir "gradlew.bat") clean build --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed." }
}
if (-not (Test-Path $modlSource)) { throw "Modl not found at $modlSource - build first." }

Write-Host "== Stopping gateway =="
& (Join-Path $ignitionHome "stop-ignition.bat") | Out-Null
$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    $proc = Get-Process -Name "IgnitionGateway" -ErrorAction SilentlyContinue
    if (-not $proc) { break }
}

Copy-Item $modlSource $modlTarget -Force

Write-Host "== Registering module in modules.json =="
# The gateway can hold a lingering lock on modules.json right after shutdown - retry.
$modulesJsonWritten = $false
foreach ($attempt in 1..5) {
    try {
        $json = Get-Content $modulesJson -Raw | ConvertFrom-Json
        if ($json.PSObject.Properties.Name -contains $moduleId) {
            $json.$moduleId.filename = $modlTarget
            $json.$moduleId.onStartup = "enabled"
            $json.$moduleId.certFingerprint = ""
        } else {
            $entry = [PSCustomObject]@{
                filename        = $modlTarget
                onStartup       = "enabled"
                certFingerprint = ""
            }
            $json | Add-Member -MemberType NoteProperty -Name $moduleId -Value $entry -Force
        }
        $json | ConvertTo-Json -Depth 5 | Set-Content $modulesJson -Encoding UTF8
        $modulesJsonWritten = $true
        break
    } catch {
        Write-Host "   modules.json write attempt $attempt failed: $($_.Exception.Message)"
        Start-Sleep -Seconds 5
    }
}
if (-not $modulesJsonWritten) { throw "Could not update modules.json after 5 attempts." }

Write-Host "== Starting gateway =="
& (Join-Path $ignitionHome "start-ignition.bat") | Out-Null

$deadline = (Get-Date).AddMinutes(6)
$up = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 15
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8088/web/status/ping" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $up = $true; break }
    } catch { }
}
if (-not $up) { throw "Gateway did not come back up within 6 minutes." }

Start-Sleep -Seconds 30
# Module startup can trail gateway ping by several minutes - wait generously for the module itself.
$deadline = (Get-Date).AddMinutes(8)
while ((Get-Date) -lt $deadline) {
    try {
        $r = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 10
        if ($r.StatusCode -eq 200) {
            Write-Host "== Deploy OK =="
            Write-Host $r.Content
            exit 0
        }
    } catch { }
    Start-Sleep -Seconds 10
}
throw "Module deployed but health endpoint never returned 200. Check wrapper.log."
