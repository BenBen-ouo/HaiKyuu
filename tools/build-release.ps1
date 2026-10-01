$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path.TrimEnd('\')
$workRoot = Join-Path $projectRoot '.release-build'
$workMarker = Join-Path $workRoot '.haikyuu-generated'
$releaseRoot = Join-Path $projectRoot 'release'
$releaseApp = Join-Path $releaseRoot 'HaiKyuu'
$releaseMarker = Join-Path $releaseApp '.haikyuu-generated'

function Assert-SafePath([string]$target) {
    $full = [System.IO.Path]::GetFullPath($target).TrimEnd('\')
    if (-not $full.StartsWith($projectRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to operate outside the project: $full"
    }
    $cursor = $full
    while ($cursor.Length -gt $projectRoot.Length) {
        if (Test-Path -LiteralPath $cursor) {
            $item = Get-Item -LiteralPath $cursor -Force
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Refusing to operate through a directory link: $cursor"
            }
        }
        $cursor = Split-Path -Path $cursor -Parent
    }
    return $full
}

function Assert-ToolSucceeded([string]$step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$step failed (exit code $LASTEXITCODE)."
    }
}

function Assert-ReleaseNotRunning([string]$appPath) {
    $runningIds = @(
        Get-Process -Name HaiKyuu -ErrorAction SilentlyContinue | Where-Object {
            try {
                $_.Path -and $_.Path.StartsWith(
                    $appPath + '\', [System.StringComparison]::OrdinalIgnoreCase)
            } catch {
                $false
            }
        } | ForEach-Object { $_.Id }
    )
    if ($runningIds.Count -gt 0) {
        throw "Close the running release/HaiKyuu.exe before rebuilding (PID: $($runningIds -join ', ')). Existing release was not replaced."
    }
}

function Find-JdkBin {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) {
        $candidates.Add((Join-Path $env:JAVA_HOME 'bin'))
    }
    $pathJavac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($pathJavac) {
        $candidates.Add((Split-Path -Path $pathJavac.Source -Parent))
    }
    foreach ($vendor in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Amazon Corretto')) {
        $vendorRoot = Join-Path $env:ProgramFiles $vendor
        if (Test-Path -LiteralPath $vendorRoot) {
            foreach ($jdk in Get-ChildItem -LiteralPath $vendorRoot -Directory | Sort-Object Name) {
                $candidates.Add((Join-Path $jdk.FullName 'bin'))
            }
        }
    }
    foreach ($candidate in $candidates | Select-Object -Unique) {
        $complete = $true
        foreach ($tool in @('java.exe', 'javac.exe', 'jar.exe', 'jpackage.exe')) {
            if (-not (Test-Path -LiteralPath (Join-Path $candidate $tool))) {
                $complete = $false
                break
            }
        }
        if ($complete) { return $candidate }
    }
    throw 'No complete JDK found (java, javac, jar, jpackage). Set JAVA_HOME.'
}

try {
    Set-Location -LiteralPath $projectRoot
    $jdkBin = Find-JdkBin
    $java = Join-Path $jdkBin 'java.exe'
    $javac = Join-Path $jdkBin 'javac.exe'
    $jar = Join-Path $jdkBin 'jar.exe'
    $jpackage = Join-Path $jdkBin 'jpackage.exe'
    Write-Host "Using JDK: $jdkBin"

    Assert-SafePath $workRoot | Out-Null
    Assert-SafePath $releaseApp | Out-Null
    if (Test-Path -LiteralPath $releaseApp) {
        Assert-ReleaseNotRunning $releaseApp
    }
    if (Test-Path -LiteralPath $workRoot) {
        if (-not (Test-Path -LiteralPath $workMarker)) {
            throw ".release-build has no generated marker. Inspect it first: $workRoot"
        }
        Remove-Item -LiteralPath $workRoot -Recurse -Force
    }

    $classes = Join-Path $workRoot 'classes'
    $tests = Join-Path $workRoot 'tests'
    $isolated = Join-Path $workRoot 'isolated-run'
    $input = Join-Path $workRoot 'input'
    $packageDest = Join-Path $workRoot 'package'
    foreach ($directory in @($classes, $tests, $isolated, $input, $packageDest)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    Set-Content -LiteralPath $workMarker -Value 'HaiKyuu generated release build workspace' -Encoding UTF8

    Write-Host '1/6 Compiling game and tests...'
    & $javac -encoding UTF-8 -d $classes Main.java
    Assert-ToolSucceeded 'Game compilation'
    & $javac -encoding UTF-8 -cp $classes -d $tests `
        tests\GameplayFlowTest.java tests\LauncherTest.java `
        tests\NetworkSyncTest.java tests\PackagingSmokeTest.java
    Assert-ToolSucceeded 'Test compilation'

    Write-Host '2/6 Running gameplay, launcher, and packet tests...'
    $testClasspath = "$classes;$tests"
    foreach ($testClass in @('GameplayFlowTest', 'LauncherTest', 'network.NetworkSyncTest')) {
        & $java '-Djava.awt.headless=true' -cp $testClasspath $testClass
        Assert-ToolSucceeded $testClass
    }

    Write-Host '3/6 Copying assets and building the standalone JAR...'
    $assetRoot = Join-Path $projectRoot 'assets'
    $images = @(Get-ChildItem -LiteralPath (Join-Path $assetRoot 'images') -File)
    if ($images.Count -eq 0) { throw 'assets/images is empty.' }
    Copy-Item -LiteralPath $assetRoot -Destination $classes -Recurse
    $jarPath = Join-Path $input 'HaiKyuu.jar'
    & $jar --create --file $jarPath --main-class Main -C $classes .
    Assert-ToolSucceeded 'JAR creation'
    $jarEntries = @(& $jar --list --file $jarPath)
    Assert-ToolSucceeded 'JAR listing'
    foreach ($image in $images) {
        if ($jarEntries -notcontains ('assets/images/' + $image.Name)) {
            throw "JAR is missing image: $($image.Name)"
        }
    }

    Write-Host '4/6 Testing JAR without external assets...'
    Push-Location -LiteralPath $isolated
    try {
        $smokeClasspath = "$jarPath;$tests"
        & $java '-Djava.awt.headless=true' -cp $smokeClasspath PackagingSmokeTest @($images.Name)
        Assert-ToolSucceeded 'JAR resource and render smoke test'
    } finally {
        Pop-Location
    }

    Write-Host '5/6 Creating Windows app-image with bundled Java runtime...'
    & $jpackage --type app-image --name HaiKyuu --input $input `
        --main-jar HaiKyuu.jar --main-class Main --dest $packageDest
    Assert-ToolSucceeded 'jpackage'
    $stagedApp = Join-Path $packageDest 'HaiKyuu'
    foreach ($required in @('HaiKyuu.exe', 'app\HaiKyuu.jar', 'runtime')) {
        if (-not (Test-Path -LiteralPath (Join-Path $stagedApp $required))) {
            throw "app-image is missing: $required"
        }
    }
    Set-Content -LiteralPath (Join-Path $stagedApp '.haikyuu-generated') `
        -Value 'HaiKyuu generated Windows app-image' -Encoding UTF8

    Write-Host '6/6 Updating dist and uncompressed release...'
    $distRoot = Join-Path $projectRoot 'dist'
    New-Item -ItemType Directory -Path $distRoot, $releaseRoot -Force | Out-Null
    Assert-SafePath $distRoot | Out-Null
    Assert-SafePath $releaseApp | Out-Null
    $previousApp = Join-Path $workRoot 'previous-release'
    Assert-SafePath $previousApp | Out-Null
    if (Test-Path -LiteralPath $releaseApp) {
        if (-not (Test-Path -LiteralPath $releaseMarker)) {
            throw "release/HaiKyuu has no generated marker. Inspect it first: $releaseApp"
        }
        Assert-ReleaseNotRunning $releaseApp
        $oldLogs = Join-Path $releaseApp 'diagnostics'
        if (Test-Path -LiteralPath $oldLogs) {
            $logBackup = Join-Path $projectRoot ('diagnostics\release-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
            New-Item -ItemType Directory -Path (Split-Path $logBackup -Parent) -Force | Out-Null
            Copy-Item -LiteralPath $oldLogs -Destination $logBackup -Recurse
            Write-Host "Previous diagnostics backed up to: $logBackup"
        }
        # Rename the complete old image first; a locked file cannot leave it half deleted.
        Move-Item -LiteralPath $releaseApp -Destination $previousApp
    }
    try {
        Move-Item -LiteralPath $stagedApp -Destination $releaseApp
    } catch {
        if ((Test-Path -LiteralPath $previousApp) -and
                -not (Test-Path -LiteralPath $releaseApp)) {
            Move-Item -LiteralPath $previousApp -Destination $releaseApp
        }
        throw
    }
    Copy-Item -LiteralPath $jarPath -Destination (Join-Path $distRoot 'HaiKyuu.jar') -Force
    $oldZipPath = Join-Path $releaseRoot 'HaiKyuu-Windows.zip'
    Assert-SafePath $oldZipPath | Out-Null
    if (Test-Path -LiteralPath $oldZipPath) {
        Remove-Item -LiteralPath $oldZipPath -Force
    }
    if (Test-Path -LiteralPath $previousApp) {
        try {
            Remove-Item -LiteralPath $previousApp -Recurse -Force
        } catch {
            Write-Warning "New release is ready, but the previous image could not be removed: $previousApp"
        }
    }

    Write-Host "Built: $releaseApp\HaiKyuu.exe"
    Write-Host "Distribute the whole uncompressed folder: $releaseApp"
    Write-Host 'Manual checks on a Java-free Windows PC and real LAN are still required.'
    exit 0
} catch {
    Write-Error $_
    exit 1
}
