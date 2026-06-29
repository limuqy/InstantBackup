# 极速备份 功能测试文档

> **验收范围：** 本文档用例以 **1.18.2 / 1.19.4 / 1.20.1 / 1.20.4 / 1.21.x** 锚点为验收对象。**1.16.5 不在项目验收范围内**，无需执行本文档回归测试。详见 [`requirements.md`](requirements.md) 开头说明。

## 一、测试环境准备

### 1.1 构建模组

```bash
# 构建所有加载器版本（1.20.1）
./gradlew build -Pmc_ver=1.20.1

# 仅构建 Fabric 版本
./gradlew :fabric:build -Pmc_ver=1.20.1

# 仅构建 NeoForge 版本
./gradlew :neoforge:build -Pmc_ver=1.20.1

# 仅构建 Forge 版本
./gradlew :forge:build -Pmc_ver=1.20.1

# 构建独立 CLI 工具（无需启动游戏）
./gradlew :cli:shadowJar
```

产物：`cli/build/libs/instantbackup-cli-<mod_version>-all.jar`

### 1.2 启动测试服务器

```bash
# Fabric
./gradlew :fabric:runServer -Pmc_ver=1.20.1

# NeoForge
./gradlew :neoforge:runServer -Pmc_ver=1.20.1

# Forge
./gradlew :forge:runServer -Pmc_ver=1.20.1
```

`runServer` 已注入 `-Dinstantbackup.selftest=true`，启动后会自动运行全链路自检（见 §1.4）。

### 1.3 目录结构验证

启动服务器后，验证以下目录结构（典型专用服，世界目录为 `./world`）：

```
<server>/
├── world/                      # 世界存档目录
│   ├── region/
│   ├── level.dat
│   └── ...
├── backups/                    # 备份目录（默认 storage.path=backups，相对服务器根目录）
│   ├── data/                   # blob 物理文件（去重存储）
│   │   └── world/region/r.0.0.mca.<hash>.zst   # 示例命名
│   └── InstantBackup_<版本名>.zip              # 导出 ZIP（如有）
└── config/
    └── instantbackup/
        ├── instantbackup.properties
        ├── backups.db          # SQLite 元数据（不随 storage.path 迁移）
        ├── InstantBackup.cmd   # Windows 一键脚本（script.enabled=true）
        └── InstantBackup.sh    # Linux/macOS 一键脚本
```

**Blob 命名规则：**
- 已压缩：`data/<相对路径>.<hash>.zst`（如 `data/world/region/r.0.0.mca.a1b2c3d4.zst`）
- 中间态 raw：`data/<相对路径>.<hash>`（压缩完成后删除）

跨盘符示例：在 `config/instantbackup/instantbackup.properties` 中设置 `storage.path=D:/minecraft-backups`，**重启服务器**后备份 blob 写入 D 盘，数据库仍在 `config/instantbackup/`。

### 1.4 开发环境自检（SelfTest）

**触发方式：** `runServer` 默认注入 `-Dinstantbackup.selftest=true`；或手动添加该 JVM 参数。

**验证步骤：**
1. 启动测试服务器，等待约 3 分钟（SelfTest 延迟 3s 后开始）
2. 查看日志：`fabric/run/server/logs/latest.log`（或对应 loader 的 run 目录）
3. 搜索 `[SelfTest]`

**预期结果：**
```
[SelfTest] === 开始全链路自检 ===
[SelfTest] create-1: 备份已开始: ...
[SelfTest] create-2: 备份已开始: ...
[SelfTest] 产物: zst=N, zip=M
[SelfTest] PASS: 全链路验证通过
```

**验证点：**
- 连续两次 create 成功
- `backups/data/` 下存在 `.zst` 文件
- 版本状态为「已完成」

### 1.5 RCON 全链路验证（可选）

Windows 环境可使用项目脚本：

```powershell
powershell -File scripts/verify_backup_rcon.ps1
```

---

## 二、核心功能测试

### 2.1 手动备份测试

**测试命令：**
```
/backup create
/backup create 主城建设完成
```

**预期结果：**
- 返回「备份已开始: 20260629_120000」格式的消息
- `backups/data/` 目录下出现 `.zst` blob 文件（非「每版本独立文件夹」）
- `config/instantbackup/backups.db` 中写入版本记录
- 版本初始状态可能为「进行中」，压缩完成后变为「已完成」

**验证方法：**
```sql
-- 查看版本记录
SELECT id, version_name, description, file_count, status
FROM backup_versions ORDER BY timestamp DESC;

-- 查看文件记录
SELECT * FROM file_info
WHERE version_id = (SELECT MAX(id) FROM backup_versions);

-- 查看 blob 状态（0=PENDING, 1=STAGED, 2=STORED）
SELECT state, COUNT(*) FROM blobs GROUP BY state;
```

```bash
# 检查 blob 文件
ls backups/data/**/*.zst
```

### 2.2 增量备份测试

**测试步骤：**
1. 执行首次备份：`/backup create`
2. 等待备份完成（`/backup status` 显示空闲，版本状态「已完成」）
3. 修改世界中的方块（放置/破坏）
4. 执行第二次备份：`/backup create 增量测试`

**预期结果：**
- 首次备份记录所有扫描到的文件
- 第二次备份的 `file_info` 条目显著少于首次（仅变化文件）
- 未变化文件的 blob 被复用，不会重复 capture/压缩

**验证方法：**
```sql
-- 比较两次备份的文件数量
SELECT version_id, COUNT(*) AS file_count
FROM file_info
GROUP BY version_id
ORDER BY version_id DESC;

-- 验证 blob 去重：同一 blob_key 只应有一条 blobs 记录
SELECT blob_key, COUNT(*) FROM blobs GROUP BY blob_key HAVING COUNT(*) > 1;
-- 应返回空
```

### 2.3 版本列表测试

**测试命令：**
```
/backup list
```

**预期结果：**
```
=== 备份版本列表 ===
序号  版本名称              状态      类型     备注
----  --------------------  --------  -------  --------
1     20260629_120000       已完成    手动     主城建设完成
2     20260629_110000       已完成    自动     -
```

**验证点：**
- 序号从 1 开始，按时间倒序
- 状态（已完成/进行中）、类型（手动/自动）、备注正确显示

### 2.4 连续备份与自动迁移测试

**测试步骤：**
1. 执行备份：`/backup create 迁移测试-1`
2. 修改世界（放置方块，触发区块 COW）
3. 执行备份：`/backup create 迁移测试-2`

**预期结果：**
- 第二次 create 前日志出现「创建备份前自动迁移」
- 第一次备份的 PENDING blob 被自动封存为 STORED
- `/backup status` 中「待迁移 blob」仅反映最新备份中尚未迁移的区块

**回档说明：**
- 使用 `/backup export <序号>` 导出 ZIP，停服后手动替换世界目录

### 2.5 备份删除测试

**测试命令：**
```
/backup delete 1
```

**预期结果：**
- 返回「已删除版本: 20260629_120000」
- 数据库中该版本的 `file_info` 记录被删除
- 不再被任何版本引用的 blob 物理文件被 GC 删除；仍被其他版本引用的 blob 保留

**验证方法：**
```bash
# 检查备份目录（blob 可能部分保留）
ls backups/data/

# 检查数据库记录
sqlite3 config/instantbackup/backups.db "SELECT * FROM backup_versions;"
```

### 2.6 备份导出测试

**测试命令：**
```
/backup export 1
```

**预期结果：**
- 返回「导出已开始: InstantBackup_20260629_120000.zip」及保存路径提示
- `backups/` 目录下创建 `InstantBackup_<版本名>.zip`
- ZIP 内包含完整世界目录结构（解压后可手动回档）

**验证方法：**
```bash
# 检查导出文件
ls -la backups/InstantBackup_*.zip

# 解压验证
unzip -l backups/InstantBackup_20260629_120000.zip
```

### 2.7 状态查看测试

**测试命令：**
```
/backup status
```

**预期结果（空闲状态）：**
```
=== 任务状态 ===
扫描/创建: 空闲
待迁移 blob: 0
压缩队列: 0
进行中版本: 0
线程池 - 已完成: 5/5, 活跃: 0, 队列: 0
```

**预期结果（备份进行中）：**
```
=== 任务状态 ===
扫描/创建: 进行中 (150/1000 文件)
待迁移 blob: 3
压缩队列: 12
进行中版本: 1
线程池 - 已完成: 2/5, 活跃: 2, 队列: 1
```

### 2.8 配置查看/修改测试

**查看配置：**
```
/backup config
```

**预期结果（默认值）：**
```
=== 当前配置 ===
备份间隔: 3600 秒
定时备份: 启用
仅玩家在线时: 是
排除地毯假人: 是
区块延迟迁移: 是
区块全量 hash: 否
压缩等级: 19
压缩线程数: 2
备份线程数: 2
最大版本数: 30
存储路径: backups
消息语言: zh_cn
```

**修改配置：**
```
/backup config interval 1800
/backup config compression 5
/backup config max_versions 100
/backup config threads 4
/backup config language en_us
```

**预期结果：**
- 返回修改成功消息
- `instantbackup.properties` 即时更新
- 新配置立即生效（`path` 除外，需重启）

**修改存储路径：**
```
/backup config path D:/test-backups
```
- 返回成功消息及「修改备份路径后需重启服务器才能生效」提示

**仅配置文件可改（无 config 子命令）：**
- `backup.deferred_chunk_migration`
- `chunk.full_hash`
- `compression.threads`
- `script.enabled`

### 2.9 清理数据测试

**测试命令：**
```
/backup clean
```

**预期结果：**
- 返回「已清理所有备份数据」及备份目录路径
- `backups/data/` 被清空
- `config/instantbackup/backups.db` 中的版本/blob/file_info 记录被清空

**验证方法：**
```bash
ls -la backups/data/
sqlite3 config/instantbackup/backups.db "SELECT COUNT(*) FROM backup_versions;"
sqlite3 config/instantbackup/backups.db "SELECT COUNT(*) FROM blobs;"
```

### 2.10 migrate 命令测试

**测试步骤：**
1. 执行备份：`/backup create migrate测试`
2. 修改世界方块（产生 PENDING blob，不立即执行第二次 create）
3. 确认 `/backup status` 中「待迁移 blob > 0`
4. 执行：`/backup migrate 1`

**预期结果：**
- 返回「封存完成: 已迁移 N 个文件」（或含跳过计数）
- `/backup status` 中待迁移 blob 减少
- 对应 blob 状态变为 STORED

**验证方法：**
```sql
SELECT state, COUNT(*) FROM blobs GROUP BY state;
```

---

## 三、定时备份测试

### 3.1 定时备份触发测试

**测试配置：**
```
/backup config interval 60
/backup config enabled true
/backup config online_only false
```

**测试步骤：**
1. 修改配置为 60 秒间隔
2. 等待 60 秒
3. 检查是否自动创建备份

**预期结果：**
- 每 60 秒自动创建备份
- 版本类型显示为「自动」
- 备注显示为「-」
- 日志可见 `[Instant Backup] 备份已创建: ...`（日志前缀为英文标识，游戏内消息为「极速备份」）

### 3.2 玩家在线检测测试

**测试配置：**
```
/backup config online_only true
/backup config exclude_bots true
```

**预期结果：**
- 无玩家在线时跳过备份
- 有玩家在线时正常备份

### 3.3 地毯假人排除测试

**测试条件：**
- 安装 Carpet Mod
- 有地毯假人在线

**预期结果：**
- 仅有地毯假人在线时跳过备份
- 有真实玩家在线时正常备份

---

## 四、增量备份与区块保护测试

### 4.1 区块文件变化检测

**测试步骤：**
1. 执行备份：`/backup create`
2. 在世界中移动（加载新区块）
3. 执行备份：`/backup create`

**预期结果：**
- 新加载的区块文件被记录
- 已有区块仅在修改后才产生新 blob

### 4.2 区块保护机制测试

**测试步骤：**
1. 执行备份：`/backup create`
2. 在备份进行中时修改方块
3. 等待备份完成，导出 ZIP 并检查区块内容

**预期结果：**
- COW 保护备份时刻的区块内容
- 修改后的区块在后续备份中被正确记录

### 4.3 延迟迁移 vs 立即捕获

**测试 A（默认 `backup.deferred_chunk_migration=true`）：**
1. 执行 create 后检查 blobs 表
2. 未修改的区块 blob 可能为 PENDING
3. 修改方块触发 COW 或执行 migrate 后变为 STORED

**测试 B（CLI 离线，`deferred_chunk_migration=false`）：**
1. 停服后运行 CLI `create`
2. 所有区块 blob 应在备份时立即 capture，无 PENDING

---

## 五、多加载器测试

### 5.1 Fabric 加载器测试

```bash
./gradlew :fabric:runClient -Pmc_ver=1.20.1
./gradlew :fabric:runServer -Pmc_ver=1.20.1
```

### 5.2 NeoForge 加载器测试

```bash
./gradlew :neoforge:runClient -Pmc_ver=1.20.1
./gradlew :neoforge:runServer -Pmc_ver=1.20.1
```

### 5.3 Forge 加载器测试

```bash
./gradlew :forge:runClient -Pmc_ver=1.20.1
./gradlew :forge:runServer -Pmc_ver=1.20.1
```

**验证点：**
- 模组正确加载
- 命令正确注册
- 备份功能正常工作
- 目录结构正确

---

## 六、性能测试

### 6.1 大文件备份测试

**测试条件：**
- 世界大小 > 1GB
- 包含大量区块文件

**测试指标：**
- 备份时间 < 5 分钟（视硬件而定）
- 内存占用合理（无 OOM）
- TPS 影响 < 2

### 6.2 长时间运行测试

**测试条件：**
- 服务器运行 > 24 小时
- 定时备份间隔 1 小时

**测试指标：**
- 无内存泄漏
- 手动 create 仍正常工作
- 数据库大小稳定

### 6.3 并发备份测试

**测试步骤：**
1. 执行手动备份：`/backup create`
2. 在备份进行中时再次执行：`/backup create`

**预期结果：**
- 返回「备份正在进行中...」
- 不会产生冲突或重复版本

---

## 七、异常处理测试

### 7.1 磁盘空间不足测试

**测试方法：**
- 填满磁盘空间
- 执行备份

**预期结果：**
- 返回错误消息
- 不会产生损坏数据（版本可能保持 IN_PROGRESS）

### 7.2 数据库损坏测试

**测试方法：**
- 手动损坏 `config/instantbackup/backups.db` 文件
- 启动服务器

**预期结果：**
- 模组尝试加载；schema 不兼容时重建 v2 表结构
- 记录错误日志，不阻止服务器启动

### 7.3 无效版本操作测试

**测试命令：**
```
/backup delete 999
/backup export 999
/backup migrate 999
```

**预期结果：**
- 返回「无效的版本序号: 999」
- 提示使用 `/backup list` 查看可用版本

### 7.4 session.lock 冲突（CLI）

**测试方法：**
- 服务器运行中尝试 CLI `create`

**预期结果：**
- CLI 检测到 `world/session.lock`，拒绝创建并提示停服

---

## 八、测试报告模板

### 8.1 测试结果汇总

| 测试项 | 预期结果 | 实际结果 | 状态 |
|--------|----------|----------|------|
| SelfTest 自检 | PASS | | |
| 手动备份 | blob + DB 写入 | | |
| 增量备份 | 仅备份变化文件 | | |
| 版本列表 | 正确显示 | | |
| 自动 migrate | create 前封存 | | |
| migrate 命令 | PENDING → STORED | | |
| 导出回档 | ZIP 可解压替换世界 | | |
| 备份删除 | 记录删除 + blob GC | | |
| 备份导出 | ZIP 创建 | | |
| 状态查看 | 含 pending/队列字段 | | |
| 配置修改 | 即时生效 | | |
| 清理数据 | 全部清除 | | |
| 定时备份 | 自动触发 | | |
| 玩家检测 | 正确判断 | | |
| 地毯假人 | 正确排除 | | |
| 区块保护 | 数据一致 | | |
| CLI 离线备份 | 与 mod 共用 DB | | |
| 并发备份 | 拒绝重复 | | |

### 8.2 问题记录

| 问题描述 | 重现步骤 | 严重程度 | 状态 |
|----------|----------|----------|------|
| | | | |

### 8.3 测试环境信息

- 操作系统：
- Java 版本：
- Minecraft 版本：
- 加载器（Fabric/Forge/NeoForge）：
- 模组版本：
- 测试日期：
- 测试人员：

---

## 九、附录

### 9.1 常用 SQL 查询

```sql
-- 查看所有版本
SELECT id, version_name, description, file_count, status, is_manual
FROM backup_versions ORDER BY timestamp DESC;

-- 查看版本文件
SELECT * FROM file_info WHERE version_id = ?;

-- 统计备份大小
SELECT version_name, total_size, file_count, status
FROM backup_versions ORDER BY timestamp DESC;

-- 查看 blob 状态分布
SELECT state, COUNT(*) FROM blobs GROUP BY state;
-- 0=PENDING, 1=STAGED, 2=STORED

-- 查看 PENDING blob
SELECT blob_key, file_path, file_hash FROM blobs WHERE state = 0;

-- 统计 blob 去重效果
SELECT COUNT(DISTINCT blob_key) AS unique_blobs,
       COUNT(*) AS total_file_refs
FROM file_info;

-- 清空数据库（慎用，建议用 /backup clean）
DELETE FROM file_info;
DELETE FROM blobs;
DELETE FROM backup_versions;
```

### 9.2 配置文件说明

| 配置项 | 默认值 | 命令可改 | 说明 |
|--------|--------|----------|------|
| `general.language` | `zh_cn` | `config language` | 消息语言（zh_cn / en_us） |
| `script.enabled` | `true` | 否 | 是否生成 InstantBackup.cmd / .sh |
| `backup.enabled` | `true` | `config enabled` | 是否启用定时备份 |
| `backup.interval` | `3600` | `config interval` | 备份间隔（秒） |
| `backup.only_when_players_online` | `true` | `config online_only` | 仅玩家在线时备份 |
| `backup.exclude_carpet_bots` | `true` | `config exclude_bots` | 排除地毯假人 |
| `backup.deferred_chunk_migration` | `true` | 否 | 区块延迟 COW |
| `chunk.full_hash` | `false` | 否 | 区块全量 hash |
| `compression.level` | `19` | `config compression` | ZSTD 压缩等级 (1–22) |
| `compression.threads` | `2` | 否 | 压缩消费者线程数 |
| `thread.count` | `2` | `config threads` | 备份线程池核心数 |
| `storage.max_versions` | `30` | `config max_versions` | 最大保留版本数（0=不限） |
| `storage.path` | `backups` | `config path` | 备份存储路径（改后需重启） |

### 9.3 命令参考

**游戏内命令（需 OP level 2）：**

| 命令 | 说明 |
|------|------|
| `/backup help` | 显示帮助信息 |
| `/backup create [备注]` | 创建备份（自动 migrate pending blob） |
| `/backup list` | 查看版本列表 |
| `/backup delete <序号>` | 删除指定版本 |
| `/backup export <序号>` | 导出指定版本 ZIP（手动回档） |
| `/backup migrate <序号>` | 封存指定版本及更早版本的 PENDING blob |
| `/backup status` | 查看任务状态 |
| `/backup config [设置] [值]` | 查看/修改配置 |
| `/backup clean` | 清理所有备份数据 |

**CLI 命令（停服后，无 `config` 子命令）：**

| 命令 | 说明 |
|------|------|
| `help` | 显示帮助 |
| `create [备注]` | 创建备份 |
| `list` | 查看版本列表 |
| `delete <序号>` | 删除版本 |
| `export <序号>` | 导出 ZIP |
| `migrate <序号>` | 封存 PENDING blob |
| `status` | 查看状态 |
| `clean` | 清理所有数据 |
| `stop` / `exit` / `quit` | 退出 REPL |

> CLI help 文案中提及 `config`，但 `BackupService` 尚未实现该子命令；修改配置请编辑 `instantbackup.properties` 或使用游戏内 `/backup config`。

---

## 十、一键脚本与 CLI 离线测试

### 10.1 配置目录一键脚本（推荐）

服务器**至少启动一次**后，会在 `config/instantbackup/` 生成：

| 文件 | 用途 |
|------|------|
| `InstantBackup.cmd` | Windows 双击打开交互式备份控制台 |
| `InstantBackup.sh` | Linux/macOS 运行（首次需 `chmod +x InstantBackup.sh`） |

脚本自动注入当前服务器目录与 mod JAR 路径，**无需手动指定 mods 目录**。

**使用步骤：**

1. 启动并关闭服务器（或正常运行一次以生成/更新脚本）
2. **创建备份前请先停服**（避免 `session.lock`）
3. 双击 `InstantBackup.cmd`（或在 shell 中运行 `.sh`）
4. 在 REPL 中输入命令，例如 `list`、`create 备注`、`export 1`
5. 输入 `stop` / `end` / `exit` / `quit` 退出

可通过配置 `script.enabled=false` 关闭脚本生成。

### 10.2 独立 CLI JAR（高级 / 自动化）

```powershell
./gradlew :cli:shadowJar
java -jar cli/build/libs/instantbackup-cli-<mod_version>-all.jar --server-dir D:\mc-server --interactive
java -jar cli/build/libs/instantbackup-cli-<mod_version>-all.jar --server-dir D:\mc-server list
```

单次命令模式（适合 cron / CI）：

```powershell
java -jar cli/build/libs/instantbackup-cli-<mod_version>-all.jar --server-dir D:\mc-server export 1
```

### 10.3 离线创建备份

**前提：** 服务器已完全停止（不存在 `world/session.lock`）。

在 REPL 或单次命令中执行 `create`：

```
> create 计划任务备份
```

**预期：**
- 与游戏内 `/backup create` 共用 `config/instantbackup/backups.db` 与 `backups/data/` blob 存储
- CLI 自动设置 `deferred_chunk_migration=false`，备份时立即 capture 区块

### 10.4 跨盘符路径验证

1. 设置 `storage.path=D:/test-backups` 并**重启服务器**
2. 执行 `create` 或 `/backup create`
3. 确认 `D:/test-backups/data/` 有 blob 文件，`config/instantbackup/backups.db` 有版本记录
