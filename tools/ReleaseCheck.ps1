function Invoke-ReleaseCheck {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Executable,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$LogPath
    )

    Add-Content -LiteralPath $LogPath -Value "`n=== $Name ===" -Encoding UTF8
    # Windows PowerShell represents native stderr as error records. Keep those
    # in the log and decide success by the process exit code, not by stderr.
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    if (-not (Test-Path -LiteralPath $Executable -PathType Leaf)) {
        Write-Host "[FAIL] $Name"
        throw "Executable not found: $Executable. Details: $LogPath"
    }
    & $Executable @Arguments *>&1 | Out-File -LiteralPath $LogPath -Append -Encoding UTF8 -ErrorAction Stop
    $checkExitCode = $LASTEXITCODE
    if ($checkExitCode -ne 0) {
        Write-Host "[FAIL] $Name"
        throw "$Name failed (exit code $checkExitCode). Details: $LogPath"
    }
    Write-Host "[PASS] $Name"
}
