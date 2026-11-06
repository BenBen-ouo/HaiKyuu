function Install-ReleasePair {
    param(
        [Parameter(Mandatory)][string]$ProjectRoot,
        [Parameter(Mandatory)][string]$StagedApp,
        [Parameter(Mandatory)][string]$StagedZip,
        [Parameter(Mandatory)][string]$ReleaseApp,
        [Parameter(Mandatory)][string]$ReleaseZip,
        [Parameter(Mandatory)][string]$RecoveryRoot
    )

    $root = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\')
    $previousApp = Join-Path $RecoveryRoot 'previous-release'
    $previousZip = Join-Path $RecoveryRoot 'previous-release.zip'
    $recoveryMarker = Join-Path $RecoveryRoot '.haikyuu-recovery-needed'
    foreach ($path in @($StagedApp, $StagedZip, $ReleaseApp, $ReleaseZip,
            $RecoveryRoot, $previousApp, $previousZip, $recoveryMarker)) {
        $full = [IO.Path]::GetFullPath($path).TrimEnd('\')
        if (-not $full.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw "Release path is outside the workspace: $full"
        }
        $cursor = $full
        while ($cursor.Length -gt $root.Length) {
            if (Test-Path -LiteralPath $cursor) {
                if (((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                    throw "Release path contains a link: $cursor"
                }
            }
            $cursor = Split-Path -Path $cursor -Parent
        }
    }
    foreach ($directory in @($StagedApp, $RecoveryRoot)) {
        if (-not (Test-Path -LiteralPath (Join-Path $directory '.haikyuu-generated'))) {
            throw "Missing generated marker: $directory"
        }
    }
    if ((Test-Path -LiteralPath $ReleaseApp) -and
            -not (Test-Path -LiteralPath (Join-Path $ReleaseApp '.haikyuu-generated'))) {
        throw "Missing release marker: $ReleaseApp"
    }
    if ((Test-Path -LiteralPath $previousApp) -or (Test-Path -LiteralPath $previousZip) -or
            (Test-Path -LiteralPath $recoveryMarker)) {
        throw "Previous release recovery files remain. Inspect: $RecoveryRoot"
    }

    $oldAppMoved = $false
    $oldZipMoved = $false
    $newAppMoved = $false
    $newZipMoved = $false
    Set-Content -LiteralPath $recoveryMarker -Encoding UTF8 -Value 'Release replacement in progress; inspect before deleting.'
    try {
        if (Test-Path -LiteralPath $ReleaseApp) {
            Move-Item -LiteralPath $ReleaseApp -Destination $previousApp -ErrorAction Stop
            $oldAppMoved = $true
        }
        if (Test-Path -LiteralPath $ReleaseZip) {
            Move-Item -LiteralPath $ReleaseZip -Destination $previousZip -ErrorAction Stop
            $oldZipMoved = $true
        }
        Move-Item -LiteralPath $StagedApp -Destination $ReleaseApp -ErrorAction Stop
        $newAppMoved = $true
        Move-Item -LiteralPath $StagedZip -Destination $ReleaseZip -ErrorAction Stop
        $newZipMoved = $true
        Remove-Item -LiteralPath $recoveryMarker -ErrorAction Stop
    } catch {
        $originalFailure = $_
        $rollbackFailures = New-Object 'System.Collections.Generic.List[string]'
        # Undo only completed moves; keep both the old release and failure evidence.
        foreach ($operation in @(
            @{ Active = $newZipMoved; Source = $ReleaseZip; Target = $StagedZip },
            @{ Active = $newAppMoved; Source = $ReleaseApp; Target = $StagedApp },
            @{ Active = $oldZipMoved; Source = $previousZip; Target = $ReleaseZip },
            @{ Active = $oldAppMoved; Source = $previousApp; Target = $ReleaseApp }
        )) {
            if ($operation.Active) {
                try {
                    Move-Item -LiteralPath $operation.Source -Destination $operation.Target -ErrorAction Stop
                } catch {
                    $rollbackFailures.Add($_.Exception.Message)
                }
            }
        }
        if ($rollbackFailures.Count -gt 0) {
            Set-Content -LiteralPath $recoveryMarker -Encoding UTF8 -Value ($rollbackFailures -join "`r`n")
            throw "Release replacement failed; recovery is incomplete. Preserve $RecoveryRoot. $originalFailure"
        }
        Remove-Item -LiteralPath $recoveryMarker -ErrorAction Stop
        throw $originalFailure
    }
}
