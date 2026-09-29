param([switch]$ParallelInstall, [switch]$Tests)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    if (-not $env:JAVA_HOME) {
        $jdk = Get-ChildItem "$env:LOCALAPPDATA\AndroidRemoteBuild" -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    }
    if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    & npm.cmd ci --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    $gradleCmd = Get-Command gradle.bat -ErrorAction SilentlyContinue
    $gradleExe = if ($gradleCmd) { $gradleCmd.Source } else { Join-Path $env:LOCALAPPDATA 'AndroidRemoteBuild\gradle-8.9\bin\gradle.bat' }
    if (-not (Test-Path -LiteralPath $gradleExe)) { throw 'Install Gradle 8.9 and JDK 17, and set JAVA_HOME / ANDROID_HOME.' }
    $buildArgs = @('-p','android-companion','assembleDebug','lintDebug','--console=plain')
    if ($ParallelInstall) { $buildArgs += '-PparallelInstall' }
    if ($Tests) { $buildArgs += 'assembleDebugAndroidTest' }
    & $gradleExe @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
} finally { Pop-Location }
