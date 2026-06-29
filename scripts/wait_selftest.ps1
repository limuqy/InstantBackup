# 等待指定日志中出现 SelfTest 结果
param(
    [Parameter(Mandatory = $true)][string]$LogFile,
    [int]$TimeoutSec = 600
)

$deadline = (Get-Date).AddSeconds($TimeoutSec)
while ((Get-Date) -lt $deadline) {
    if (Test-Path $LogFile) {
        $content = Get-Content $LogFile -Raw -ErrorAction SilentlyContinue
        if ($content -match '\[SelfTest\] PASS') {
            Write-Host "PASS"
            exit 0
        }
        if ($content -match '\[SelfTest\] FAIL') {
            Write-Host "FAIL"
            exit 1
        }
    }
    Start-Sleep -Seconds 5
}
Write-Host "TIMEOUT"
exit 2
