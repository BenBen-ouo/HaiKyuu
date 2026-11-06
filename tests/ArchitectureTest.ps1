param(
    [Parameter(Mandatory)][string]$ClassesPath,
    [Parameter(Mandatory)][string]$JdkBin
)
$ErrorActionPreference = 'Stop'
$dependencies = @(& (Join-Path $JdkBin 'jdeps.exe') --ignore-missing-deps -verbose:class -filter:none $ClassesPath)
if ($LASTEXITCODE -ne 0) { throw "jdeps failed: $LASTEXITCODE" }
$violations = @($dependencies | Where-Object { $_ -match '^\s+model\.\S+\s+->\s+(view|controller|network)\.' })
if ($violations.Count -gt 0) { throw "Model dependency violation:`n$($violations -join "`n")" }

function Test-Reachable($graph, [string]$from, [string]$target) {
    $pending = New-Object 'System.Collections.Generic.Stack[string]'
    $visited = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    $pending.Push($from)
    while ($pending.Count -gt 0) {
        $node = $pending.Pop()
        if ($node -ceq $target) { return $true }
        if (-not $visited.Add($node)) { continue }
        foreach ($dependency in $graph[$node]) { $pending.Push($dependency) }
    }
    return $false
}

$graph = New-Object 'System.Collections.Generic.Dictionary[string,object]' ([StringComparer]::Ordinal)
$edges = New-Object 'System.Collections.Generic.List[object]'
foreach ($line in $dependencies) {
    if ($line -match '^\s+((?:model|view|controller|network)\.\S+)\s+->\s+((?:model|view|controller|network)\.\S+)') {
        $source = $Matches[1]
        $target = $Matches[2]
        foreach ($node in @($source, $target)) {
            if (-not $graph.ContainsKey($node)) {
                $graph[$node] = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
            }
        }
        $graph[$source].Add($target) | Out-Null
        $edges.Add(@{ Source = $source; Target = $target })
    }
}
foreach ($edge in $edges) {
    if (($edge.Source.Split('.')[0] -cne $edge.Target.Split('.')[0]) -and
            (Test-Reachable $graph $edge.Target $edge.Source)) {
        throw "Cross-layer class cycle: $($edge.Source) -> $($edge.Target)"
    }
}
# Ownership back-references and nested types within one layer are not forbidden cycles.
Write-Host 'ArchitectureTest passed (compiled dependencies and cross-layer class cycles)'
