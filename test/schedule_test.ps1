$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$jdkRoot = $env:JAVA_HOME
if (-not $jdkRoot) {
    $jdkRoot = (Get-ChildItem "$env:LOCALAPPDATA\AndroidRemoteBuild" -Directory -Filter 'jdk-*' | Select-Object -First 1).FullName
}
if (-not $jdkRoot) { throw 'Set JAVA_HOME to JDK 17.' }
$testOutput = Join-Path ([IO.Path]::GetTempPath()) ('schedule-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testOutput | Out-Null
& "$jdkRoot\bin\javac.exe" -d $testOutput "$repoRoot\android-companion\app\src\main\java\io\github\soyucy\androidremote\companion\ScheduleWindow.java" "$PSScriptRoot\ScheduleWindowTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Schedule test compilation failed' }
& "$jdkRoot\bin\java.exe" -cp $testOutput io.github.soyucy.androidremote.companion.ScheduleWindowTest
if ($LASTEXITCODE -ne 0) { throw 'Schedule test failed' }
