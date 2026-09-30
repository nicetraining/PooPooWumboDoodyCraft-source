# Runs the build and the GameTests and saves everything to check-log.txt
# Usage (from the jurblet folder):  .\check.ps1
$log = Join-Path $PSScriptRoot "check-log.txt"
Set-Location $PSScriptRoot
"=== check started $(Get-Date) ===" | Out-File $log -Encoding utf8

"=== .\gradlew.bat build ===" | Tee-Object -FilePath $log -Append
& .\gradlew.bat build 2>&1 | Tee-Object -FilePath $log -Append
$buildExit = $LASTEXITCODE

"" | Tee-Object -FilePath $log -Append
"=== .\gradlew.bat runGameTestServer ===" | Tee-Object -FilePath $log -Append
& .\gradlew.bat runGameTestServer 2>&1 | Tee-Object -FilePath $log -Append
$testExit = $LASTEXITCODE

$summary = "=== done: build exit code $buildExit, gametest exit code $testExit ==="
$summary | Tee-Object -FilePath $log -Append
Write-Host "`nFull log saved to: $log"
