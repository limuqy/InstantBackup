#!/usr/bin/env python3
"""通过 RCON 向 Fabric 测试服发送 /backup 命令并检查日志与备份目录。"""
import struct
import socket
import sys
import time
from pathlib import Path

HOST = "127.0.0.1"
PORT = 25575
PASSWORD = "instantbackup"
PROJECT = Path(__file__).resolve().parents[1]
LOG_FILE = PROJECT / "fabric" / "run" / "server" / "logs" / "latest.log"
BACKUP_ROOT = PROJECT / "fabric" / "run" / "server" / "backups"
DATA_ROOT = BACKUP_ROOT / "data"


def rcon_command(command: str) -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(30)
    sock.connect((HOST, PORT))

    def send_packet(req_id: int, req_type: int, payload: str) -> bytes:
        body = struct.pack("<ii", req_id, req_type) + payload.encode("utf-8") + b"\x00\x00"
        return struct.pack("<i", len(body)) + body

    def recv_packet() -> tuple[int, int, str]:
        raw_len = sock.recv(4)
        if len(raw_len) < 4:
            return 0, 0, ""
        (length,) = struct.unpack("<i", raw_len)
        data = b""
        while len(data) < length:
            chunk = sock.recv(length - len(data))
            if not chunk:
                break
            data += chunk
        req_id, req_type = struct.unpack("<ii", data[:8])
        text = data[8:-2].decode("utf-8", errors="replace")
        return req_id, req_type, text

    sock.sendall(send_packet(1, 3, PASSWORD))
    recv_packet()
    sock.sendall(send_packet(2, 2, command))
    _, _, response = recv_packet()
    sock.close()
    return response.strip()


def tail_log(since_pos: int) -> tuple[int, str]:
    if not LOG_FILE.exists():
        return since_pos, ""
    text = LOG_FILE.read_text(encoding="utf-8", errors="replace")
    return len(text), text[since_pos:]


def wait_for_log(pattern: str, timeout: float = 120.0) -> bool:
    pos = len(LOG_FILE.read_text(encoding="utf-8", errors="replace")) if LOG_FILE.exists() else 0
    deadline = time.time() + timeout
    while time.time() < deadline:
        pos, chunk = tail_log(pos)
        if pattern in chunk:
            return True
        time.sleep(1)
    return False


def main() -> int:
    print("=== Instant Backup 全链路验证 ===")

    # 等待 RCON 就绪
    for _ in range(60):
        try:
            rcon_command("list")
            break
        except OSError:
            time.sleep(2)
    else:
        print("FAIL: RCON 未就绪")
        return 1

    print("1. /backup create 全链路测试")
    print(rcon_command("backup create 全链路测试"))

    print("2. 等待扫描完成...")
    time.sleep(8)
    status = rcon_command("backup status")
    print(status)

    print("3. /backup list")
    print(rcon_command("backup list"))

    print("4. 等待压缩队列消化（最多 90s）...")
    for i in range(18):
        status = rcon_command("backup status")
        if "进行中版本: 0" in status and "待迁移 blob: 0" in status and "压缩队列: 0" in status:
            print("   压缩与迁移已完成")
            break
        time.sleep(5)
    else:
        print("WARN: 超时，当前状态:\n" + status)

    print("5. /backup migrate 1（封存）")
    print(rcon_command("backup migrate 1"))

    print("6. /backup export 1")
    export_resp = rcon_command("backup export 1")
    print(export_resp)

    print("7. 等待导出 zip...")
    wait_for_log("导出完成", timeout=60)

    print("8. /backup status（最终）")
    print(rcon_command("backup status"))

    # 检查备份目录
    zst_files = list(DATA_ROOT.rglob("*.zst")) if DATA_ROOT.exists() else []
    zip_files = list(BACKUP_ROOT.glob("InstantBackup_*.zip"))
    db_file = PROJECT / "fabric" / "run" / "server" / "config" / "instantbackup" / "backups.db"

    print("\n=== 文件系统检查 ===")
    print(f"  backups.db 存在: {db_file.exists()}")
    print(f"  data/*.zst 数量: {len(zst_files)}")
    print(f"  导出 zip 数量: {len(zip_files)}")
    if zst_files:
        print(f"  示例 blob: {zst_files[0].relative_to(BACKUP_ROOT)}")

    ok = db_file.exists() and len(zst_files) > 0
    print("\n" + ("PASS: 全链路验证通过" if ok else "FAIL: 备份产物不完整"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
