# 通过 RCON 验证 Instant Backup 全链路。
param(
    [ValidateSet("fabric", "forge", "neoforge")]
    [string]$Loader = "fabric",
    [string]$HostAddr = "127.0.0.1",
    [int]$Port = 25575,
    [string]$Password = "instantbackup",
    [int]$TimeoutSec = 120,
    [int]$PollIntervalSec = 5
)

$ErrorActionPreference = "Stop"
$Project = Split-Path -Parent $PSScriptRoot
$ServerRoot = Join-Path $Project "$Loader\run\server"
$LogFile = Join-Path $ServerRoot "logs\latest.log"
$BackupRoot = Join-Path $ServerRoot "backups"
$DataRoot = Join-Path $BackupRoot "data"
$DbFile = Join-Path $ServerRoot "config\instantbackup\backups.db"
$SocketTimeoutMs = 10000

function New-Failure {
    param([string]$Message)
    throw $Message
}

function Read-ExactBytes {
    param(
        [Parameter(Mandatory = $true)][System.IO.Stream]$Stream,
        [Parameter(Mandatory = $true)][int]$Length
    )

    $buffer = New-Object byte[] $Length
    $offset = 0
    while ($offset -lt $Length) {
        $read = $Stream.Read($buffer, $offset, $Length - $offset)
        if ($read -le 0) {
            New-Failure "RCON 连接已关闭，未读取到完整数据包"
        }
        $offset += $read
    }
    return $buffer
}

function Send-RconPacket {
    param(
        [Parameter(Mandatory = $true)][System.IO.Stream]$Stream,
        [Parameter(Mandatory = $true)][int]$RequestId,
        [Parameter(Mandatory = $true)][int]$Type,
        [Parameter(Mandatory = $true)][string]$Body
    )

    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body + [char]0 + [char]0)
    $header = [BitConverter]::GetBytes([int]$RequestId) + [BitConverter]::GetBytes([int]$Type)
    $payload = $header + $bodyBytes
    $length = [BitConverter]::GetBytes([int]$payload.Length)
    $Stream.Write($length, 0, $length.Length)
    $Stream.Write($payload, 0, $payload.Length)
}

function Read-RconPacket {
    param([Parameter(Mandatory = $true)][System.IO.Stream]$Stream)

    $lengthBytes = Read-ExactBytes -Stream $Stream -Length 4
    $length = [BitConverter]::ToInt32($lengthBytes, 0)
    if ($length -lt 10 -or $length -gt 4096) {
        New-Failure "RCON 数据包长度异常: $length"
    }

    $data = Read-ExactBytes -Stream $Stream -Length $length
    $requestId = [BitConverter]::ToInt32($data, 0)
    $type = [BitConverter]::ToInt32($data, 4)
    $textLength = $length - 10
    $text = ""
    if ($textLength -gt 0) {
        $text = [System.Text.Encoding]::UTF8.GetString($data, 8, $textLength)
    }

    return [PSCustomObject]@{
        RequestId = $requestId
        Type = $type
        Body = $text.Trim()
    }
}

function Send-RconCommand {
    param([Parameter(Mandatory = $true)][string]$Command)

    $client = $null
    $stream = $null
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $client.ReceiveTimeout = $SocketTimeoutMs
        $client.SendTimeout = $SocketTimeoutMs
        $client.Connect($HostAddr, $Port)
        $stream = $client.GetStream()
        $stream.ReadTimeout = $SocketTimeoutMs
        $stream.WriteTimeout = $SocketTimeoutMs

        Send-RconPacket -Stream $stream -RequestId 1 -Type 3 -Body $Password
        $authPacket = Read-RconPacket -Stream $stream
        if ($authPacket.RequestId -eq -1) {
            New-Failure "RCON 认证失败，请检查密码"
        }

        Send-RconPacket -Stream $stream -RequestId 2 -Type 2 -Body $Command
        $response = Read-RconPacket -Stream $stream
        return $response.Body
    } catch {
        New-Failure "RCON 命令失败 [$Command]: $($_.Exception.Message)"
    } finally {
        if ($null -ne $stream) {
            $stream.Dispose()
        }
        if ($null -ne $client) {
            $client.Close()
        }
    }
}

function Assert-CommandSucceeded {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string]$Response
    )

    if ([string]::IsNullOrWhiteSpace($Response)) {
        New-Failure "命令返回为空 [$Command]"
    }

    $failurePatterns = @("失败", "错误", "无效", "Exception", "error", "failed")
    foreach ($pattern in $failurePatterns) {
        if ($Response -match $pattern) {
            New-Failure "命令返回失败 [$Command]: $Response"
        }
    }
}

function Invoke-CheckedCommand {
    param([Parameter(Mandatory = $true)][string]$Command)

    $response = Send-RconCommand -Command $Command
    Assert-CommandSucceeded -Command $Command -Response $response
    return $response
}

function Wait-RconReady {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $lastError = ""
    while ((Get-Date) -lt $deadline) {
        try {
            [void](Send-RconCommand -Command "list")
            return $true
        } catch {
            $lastError = $_.Exception.Message
            Start-Sleep -Seconds $PollIntervalSec
        }
    }

    Write-Host "FAIL: RCON 未就绪。最后错误: $lastError"
    return $false
}

function Test-BackupIdleStatus {
    param([Parameter(Mandatory = $true)][string]$Status)

    return (
        (($Status -match "进行中版本:\s*0") -or ($Status -match "In-progress versions:\s*0")) -and
        (($Status -match "待迁移 blob:\s*0") -or ($Status -match "Pending blobs:\s*0")) -and
        (($Status -match "压缩队列:\s*0") -or ($Status -match "Compression queue:\s*0"))
    )
}

function Wait-BackupIdle {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $lastStatus = ""
    while ((Get-Date) -lt $deadline) {
        $lastStatus = Invoke-CheckedCommand -Command "backup status"
        if (Test-BackupIdleStatus -Status $lastStatus) {
            Write-Host "   压缩与迁移已完成"
            return $lastStatus
        }
        Start-Sleep -Seconds $PollIntervalSec
    }

    New-Failure "等待备份空闲超时，最后状态:`n$lastStatus"
}

function Wait-LogContains {
    param(
        [Parameter(Mandatory = $true)][string]$Pattern,
        [Parameter(Mandatory = $true)][int]$SincePosition,
        [string[]]$FailurePatterns = @()
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $LogFile) {
            $content = Get-Content -Path $LogFile -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
            if ($content.Length -ge $SincePosition) {
                $chunk = $content.Substring($SincePosition)
                if ($chunk -match [regex]::Escape($Pattern)) {
                    return $true
                }
                foreach ($failurePattern in $FailurePatterns) {
                    if ($chunk -match [regex]::Escape($failurePattern)) {
                        New-Failure "日志出现失败信息: $failurePattern"
                    }
                }
            }
        }
        Start-Sleep -Seconds $PollIntervalSec
    }
    return $false
}

function Get-LogPosition {
    if (-not (Test-Path $LogFile)) {
        return 0
    }
    $content = Get-Content -Path $LogFile -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    if ($null -eq $content) {
        return 0
    }
    return $content.Length
}

function Count-Files {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Filter,
        [switch]$Recurse
    )

    if (-not (Test-Path $Path)) {
        return 0
    }
    return @(Get-ChildItem -Path $Path -Filter $Filter -Recurse:$Recurse -File -ErrorAction SilentlyContinue).Count
}

try {
    Write-Host "=== Instant Backup 全链路验证 ==="
    Write-Host "  Loader: $Loader"
    Write-Host "  RCON: ${HostAddr}:$Port"
    Write-Host "  Server: $ServerRoot"
    Write-Host "  Log: $LogFile"
    Write-Host "  Backup: $BackupRoot"

    if (-not (Wait-RconReady)) {
        exit 1
    }

    Write-Host "1. /backup create"
    $createResponse = Invoke-CheckedCommand -Command "backup create e2e-test"
    Write-Host $createResponse

    Write-Host "2. 等待扫描与压缩完成..."
    $status = Wait-BackupIdle
    Write-Host $status

    Write-Host "3. /backup list"
    $listResponse = Invoke-CheckedCommand -Command "backup list"
    Write-Host $listResponse

    Write-Host "4. /backup migrate 1"
    $migrateResponse = Invoke-CheckedCommand -Command "backup migrate 1"
    Write-Host $migrateResponse

    Write-Host "5. /backup export 1"
    $logPosition = Get-LogPosition
    $exportResponse = Invoke-CheckedCommand -Command "backup export 1"
    Write-Host $exportResponse

    Write-Host "6. 等待导出 zip..."
    if (-not (Wait-LogContains -Pattern "导出完成" -SincePosition $logPosition -FailurePatterns @("导出失败", "Export failed"))) {
        New-Failure "等待导出完成日志超时: $LogFile"
    }

    Write-Host "7. 最终 status"
    $finalStatus = Wait-BackupIdle
    Write-Host $finalStatus

    $zstCount = Count-Files -Path $DataRoot -Filter "*.zst" -Recurse
    $zipCount = Count-Files -Path $BackupRoot -Filter "InstantBackup_*.zip"
    $dbExists = Test-Path $DbFile
    $exampleBlob = $null
    if (Test-Path $DataRoot) {
        $exampleBlob = Get-ChildItem -Path $DataRoot -Recurse -Filter "*.zst" -File -ErrorAction SilentlyContinue | Select-Object -First 1
    }

    Write-Host ""
    Write-Host "=== 文件系统检查 ==="
    Write-Host "  backups.db: $DbFile"
    Write-Host "  backups.db 存在: $dbExists"
    Write-Host "  data/*.zst 数量: $zstCount"
    Write-Host "  导出 zip 数量: $zipCount"
    if ($null -ne $exampleBlob) {
        Write-Host "  示例 blob: $($exampleBlob.FullName.Substring($BackupRoot.Length + 1))"
    }

    if ($dbExists -and $zstCount -gt 0 -and $zipCount -gt 0) {
        Write-Host "PASS: 全链路验证通过"
        exit 0
    }

    Write-Host "FAIL: 备份产物不完整"
    exit 1
} catch {
    Write-Host "FAIL: $($_.Exception.Message)"
    exit 1
}
