param(
    [Parameter(Mandatory)][string]$ClassesPath,
    [Parameter(Mandatory)][string]$JdkBin
)
$ErrorActionPreference = 'Stop'
$dependencies = @(& (Join-Path $JdkBin 'jdeps.exe') --ignore-missing-deps -verbose:class -filter:none $ClassesPath)
if ($LASTEXITCODE -ne 0) { throw "jdeps failed: $LASTEXITCODE" }
$violations = @($dependencies | Where-Object { $_ -match '^\s+model\.\S+\s+->\s+(view|controller|network)\.' })
if ($violations.Count -gt 0) { throw "Model dependency violation:`n$($violations -join "`n")" }
Write-Host 'ArchitectureTest passed (compiled model dependencies)'
