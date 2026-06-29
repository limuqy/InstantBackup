# 通过 RCON 验证 Instant Backup 全链路
$ErrorActionPreference = "Stop"
$HostAddr = "127.0.0.1"
$Port = 25575
$Password = "instantbackup"
$Project = Split-Path -Parent $PSScriptRoot
$LogFile = Join-Path $Project "fabric\run\server\logs\latest.log"
$BackupRoot = Join-Path $Project "fabric\run\server\backups"
$DataRoot = Join-Path $BackupRoot "data"
$DbFile = Join-Path $Project "fabric\run\server\config\instantbackup\backups.db"

function Send-RconCommand {
    param([string]$Command)
    $client = New-Object System.Net.Sockets.TcpClient
    $client.ReceiveTimeout = 10000
    $client.SendTimeout = 10000
    $client.Connect($HostAddr, $Port)
    $stream = $client.GetStream()
    $stream.ReadTimeout = 10000

    function Send-Packet([int]$RequestId, [int]$Type, [string]$Body) {
        $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body + [char]0 + [char]0)
        $header = [BitConverter]::GetBytes([int]$RequestId) + [BitConverter]::GetBytes([int]$Type)
        $payload = $header + $bodyBytes
        $length = [BitConverter]::GetBytes([int]$payload.Length)
        $stream.Write($length, 0, 4)
        $stream.Write($payload, 0, $payload.Length)
    }

    function Read-Packet {
        $lenBuf = New-Object byte[] 4
        $got = 0
        while ($got -lt 4) {
            $n = $stream.Read($lenBuf, $got, 4 - $got)
            if ($n -le 0) { return "" }
            $got += $n
        }
        $length = [BitConverter]::ToInt32($lenBuf, 0)
        if ($length -lt 10) { return "" }
        $data = New-Object byte[] $length
        $read = 0
        while ($read -lt $length) {
            $n = $stream.Read($data, $read, $length - $read)
            if ($n -le 0) { return "" }
            $read += $n
        }
        $textLen = $length - 10
        if ($textLen -le 0) { return "" }
        return [System.Text.Encoding]::UTF8.GetString($data, 8, $textLen).Trim()
    }

    Send-Packet -RequestId 1 -Type 3 -Body $Password
    [void](Read-Packet)
    Send-Packet -RequestId 2 -Type 2 -Body $Command

    $response = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt 8; $i++) {
        $part = Read-Packet
        if ([string]::IsNullOrEmpty($part)) { break }
        [void]$response.AppendLine($part)
    }

    $stream.Close()
    $client.Close()
    return $response.ToString().Trim()
}

Write-Host "=== Instant Backup 全链路验证 ==="

$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        [void](Send-RconCommand "list")
        $ready = $true
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $ready) {
    Write-Host "FAIL: RCON 未就绪"
    exit 1
}

Write-Host "1. /backup create"
Write-Host (Send-RconCommand "backup create e2e-test")

Write-Host "2. 等待扫描..."
Start-Sleep -Seconds 10
Write-Host (Send-RconCommand "backup status")

Write-Host "3. /backup list"
Write-Host (Send-RconCommand "backup list")

Write-Host "4. 等待压缩完成..."
$finalStatus = ""
for ($i = 0; $i -lt 24; $i++) {
    $finalStatus = Send-RconCommand "backup status"
    if ($finalStatus -match "进行中版本: 0" -and $finalStatus -match "待迁移 blob: 0" -and $finalStatus -match "压缩队列: 0") {
        Write-Host "   压缩与迁移已完成"
        break
    }
    Start-Sleep -Seconds 5
}

Write-Host "5. /backup migrate 1"
Write-Host (Send-RconCommand "backup migrate 1")

Write-Host "6. /backup export 1"
Write-Host (Send-RconCommand "backup export 1")

Write-Host "7. 等待导出..."
Start-Sleep -Seconds 20

Write-Host "8. 最终 status"
Write-Host (Send-RconCommand "backup status")

$zstCount = 0
if (Test-Path $DataRoot) {
    $zstCount = (Get-ChildItem -Path $DataRoot -Recurse -Filter "*.zst" -ErrorAction SilentlyContinue).Count
}
$zipCount = 0
if (Test-Path $BackupRoot) {
    $zipCount = (Get-ChildItem -Path $BackupRoot -Filter "InstantBackup_*.zip" -ErrorAction SilentlyContinue).Count
}

Write-Host ""
Write-Host "=== 文件系统检查 ==="
Write-Host "  backups.db 存在: $(Test-Path $DbFile)"
Write-Host "  data/*.zst 数量: $zstCount"
Write-Host "  导出 zip 数量: $zipCount"

if ((Test-Path $DbFile) -and $zstCount -gt 0) {
    Write-Host "PASS: 全链路验证通过"
    exit 0
} else {
    Write-Host "FAIL: 备份产物不完整"
    exit 1
}
