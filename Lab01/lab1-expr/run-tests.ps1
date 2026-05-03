# Run all testcases/*.mj and compare stdout + exit code to *.output
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path "target\classpath.txt")) {
    mvn -q dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt" | Out-Null
}
$cp = (Get-Content "target\classpath.txt" -Raw).Trim()
$jcp = "target\classes;$cp"

function Normalize-Text([string]$s) {
    if ($null -eq $s) { return "" }
    return ($s -replace "`r`n", "`n").TrimEnd("`n")
}

function Exit-Match([int]$actual, [int]$expected) {
    if ($actual -eq $expected) { return $true }
    # OJ / reference may use 255 for Java System.exit(-1) on some platforms
    if (($expected -eq 255) -and ($actual -eq -1)) { return $true }
    if (($expected -eq -1) -and ($actual -eq 255)) { return $true }
    $ua = [uint32][int]$actual
    $ue = [uint32][int]$expected
    return ($ua -eq $ue)
}

$passed = 0
$failed = 0
$rows = @()

Get-ChildItem "testcases\*.mj" | Sort-Object Name | ForEach-Object {
    $name = $_.BaseName
    $mj = $_.FullName
    $outPath = Join-Path $_.DirectoryName "$name.output"
    if (-not (Test-Path $outPath)) {
        $rows += [pscustomobject]@{ Test = $name; Result = "SKIP"; Detail = "no .output file" }
        continue
    }

    $raw = & java -cp $jcp cn.edu.nju.cs.Main $mj 2>&1
    $exit = $LASTEXITCODE
    $actualOut = Normalize-Text ($raw | Out-String)

    $expLines = @(Get-Content $outPath)
    $expExit = [int]$expLines[-1]
    $expOut = Normalize-Text (($expLines[0..($expLines.Length - 2)] -join "`n"))

    $okOut = ($actualOut -eq $expOut)
    $okEx = Exit-Match $exit $expExit
    if ($okOut -and $okEx) {
        $script:passed++
        $rows += [pscustomobject]@{ Test = $name; Result = "PASS"; Detail = "" }
    } else {
        $script:failed++
        $detail = @()
        if (-not $okOut) { $detail += "stdout differs" }
        if (-not $okEx) { $detail += "exit: got $exit expected $expExit" }
        $rows += [pscustomobject]@{ Test = $name; Result = "FAIL"; Detail = ($detail -join "; ") }
    }
}

$rows | Format-Table -AutoSize
Write-Host "Total PASS: $passed  FAIL: $failed"
if ($failed -gt 0) { exit 1 }
