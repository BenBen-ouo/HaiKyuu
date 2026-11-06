$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\tools\ReleaseReplacement.ps1')
$workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$testRoot = Join-Path $workspace ('build\release-pair-test-' + [guid]::NewGuid().ToString('N'))

foreach ($lockedFile in @('none', 'stagedZip', 'oldZip')) {
    $scenario = Join-Path $testRoot $lockedFile
    $stage = Join-Path $scenario 'stage'
    $oldApp = Join-Path $scenario 'release\HaiKyuu'
    $recovery = Join-Path $scenario 'recovery'
    $stageZip = Join-Path $scenario 'new.zip'
    $oldZip = Join-Path $scenario 'release\HaiKyuu-test.zip'
    foreach ($directory in @($stage, $oldApp, $recovery)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $directory '.haikyuu-generated') -Value 'test artifact'
    }
    Set-Content -LiteralPath (Join-Path $stage 'version.txt') -Value 'new'
    Set-Content -LiteralPath (Join-Path $oldApp 'version.txt') -Value 'old'
    Set-Content -LiteralPath $stageZip -Value 'new zip'
    Set-Content -LiteralPath $oldZip -Value 'old zip'
    $locked = $null
    $failed = $false
    try {
        if ($lockedFile -ne 'none') {
            $target = if ($lockedFile -eq 'stagedZip') { $stageZip } else { $oldZip }
            $locked = [IO.File]::Open($target, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
        }
        Install-ReleasePair -ProjectRoot $workspace -StagedApp $stage -StagedZip $stageZip `
            -ReleaseApp $oldApp -ReleaseZip $oldZip -RecoveryRoot $recovery
    } catch {
        $failed = $true
        if ($lockedFile -eq 'none') { throw }
    } finally {
        if ($null -ne $locked) { $locked.Dispose() }
    }
    $expected = if ($lockedFile -eq 'none') { 'new' } else { 'old' }
    if (((Get-Content -LiteralPath (Join-Path $oldApp 'version.txt') -Raw).Trim() -ne $expected) -or
            ((Get-Content -LiteralPath $oldZip -Raw).Trim() -ne ($expected + ' zip'))) {
        throw "Inconsistent app/ZIP after scenario: $lockedFile"
    }
    if (($lockedFile -ne 'none') -and -not $failed) { throw 'Expected a replacement failure' }
}
Write-Host 'ReleaseReplacementTest passed'
