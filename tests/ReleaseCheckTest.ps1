$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\tools\ReleaseCheck.ps1')
$checkLog = Join-Path ([System.IO.Path]::GetTempPath()) ('haikyuu-release-check-' + [guid]::NewGuid().ToString('N') + '.log')
$powershell = Join-Path $PSHOME 'powershell.exe'
try {
    $output = @(Invoke-ReleaseCheck -Name 'successful check' -Executable $powershell -LogPath $checkLog `
        -Arguments @('-NoProfile', '-Command', "Write-Output 'private statistics'; [Console]::Error.WriteLine('private stderr'); exit 0") 6>&1)
    if ($output.Count -ne 1 -or "$($output[0])" -ne '[PASS] successful check') {
        throw 'Successful checks must display only the pass result.'
    }
    $details = Get-Content -LiteralPath $checkLog -Raw -Encoding UTF8
    if ($details -notmatch 'private statistics' -or $details -notmatch 'private stderr') {
        throw 'Both stdout and stderr must be retained in the log.'
    }

    foreach ($failure in @("Write-Output 'failure details'; exit 7", "throw 'script failure'")) {
        $caught = $false
        $output = @(try {
            Invoke-ReleaseCheck -Name 'failed check' -Executable $powershell -LogPath $checkLog `
                -Arguments @('-NoProfile', '-Command', $failure) 6>&1
        } catch {
            $caught = $true
            if ($_.Exception.Message -notmatch [regex]::Escape($checkLog)) {
                throw 'Failure must point to its detailed log.'
            }
        })
        if (-not $caught -or $output.Count -ne 1 -or "$($output[0])" -ne '[FAIL] failed check') {
            throw 'A nonzero exit or script exception must report failure and stop the caller.'
        }
    }
    $details = Get-Content -LiteralPath $checkLog -Raw -Encoding UTF8
    if ($details -notmatch 'failure details' -or $details -notmatch 'script failure') {
        throw 'Failure details must remain available.'
    }
    Write-Host 'ReleaseCheckTest passed'
} finally {
    if (Test-Path -LiteralPath $checkLog) { Remove-Item -LiteralPath $checkLog -Force }
}
