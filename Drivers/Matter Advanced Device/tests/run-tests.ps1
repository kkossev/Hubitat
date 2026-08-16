# Local checks for the Matter Advanced Device driver. Run from the driver folder:
#     .\tests\run-tests.ps1
# Needs Groovy 4 and a JDK on PATH. Nothing here touches the hub.

$ErrorActionPreference = 'Stop'
$here   = Split-Path -Parent $MyInvocation.MyCommand.Path
$driver = Join-Path $here '..\Matter_Advanced_Device.groovy'
$build  = Join-Path $here 'build'
$failed = $false

Write-Host "`n=== 1. compile the driver against the Hubitat stubs ===" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path (Join-Path $build 'stubs') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $build 'driver') | Out-Null
Push-Location (Join-Path $here 'stubs')
groovyc -d (Join-Path $build 'stubs') hubitat/matter/DataType.groovy hubitat/helper/HexUtils.groovy hubitat/device/HubAction.groovy hubitat/device/Protocol.groovy
Pop-Location
if ($LASTEXITCODE -ne 0) { Write-Host 'stub compile FAILED' -ForegroundColor Red; exit 1 }

# groovyc will not accept a filename with spaces in the class name, so compile a copy
$tmp = Join-Path $build 'MatterAdvancedDevice.groovy'
Copy-Item $driver $tmp -Force
groovyc -cp (Join-Path $build 'stubs') -d (Join-Path $build 'driver') $tmp
if ($LASTEXITCODE -ne 0) { Write-Host 'driver compile FAILED' -ForegroundColor Red; $failed = $true }
else { Write-Host 'driver compiles clean' -ForegroundColor Green }

Write-Host "`n=== 2. helper assertions (extracted from the current driver) ===" -ForegroundColor Cyan
python (Join-Path $here 'extract_helpers.py')
if ($LASTEXITCODE -ne 0) { Write-Host 'extraction FAILED' -ForegroundColor Red; exit 1 }
$out = groovy (Join-Path $here '_generated_helpers_test.groovy') 2>&1
$out | ForEach-Object { Write-Host $_ }
if ($out -match 'FAILURE') { $failed = $true }

Write-Host "`n=== 3. collector state machine simulation ===" -ForegroundColor Cyan
$out = groovy (Join-Path $here 'collector_sim.groovy') 2>&1
$out | ForEach-Object { Write-Host $_ }
if ($out -match 'FAILURE') { $failed = $true }

Write-Host ''
if ($failed) { Write-Host 'SOME CHECKS FAILED' -ForegroundColor Red; exit 1 }
Write-Host 'ALL LOCAL CHECKS PASSED' -ForegroundColor Green
