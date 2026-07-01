# 极速备份 模组需求文档

## 一、项目概述

**极速备份**（Instant Backup）是一个 Minecraft 存档增量备份模组，采用多加载器架构（Fabric / Forge / NeoForge），从单一代码库构建多个 MC 锚点版本。支持 Minecraft **1.16.5 – 26.2**（10 个锚点，Loader 组合见 `versionProperties/<version>.properties` 中的 `builds_for`）。模组提供基于 blob 去重的增量备份、区块 Copy-on-Write（COW）保护、异步 ZSTD 压缩，以及游戏内命令、停服 CLI 与一键脚本等多种操作方式。

**实现状态图例：** ✅ 已实现 · ⚠️ 部分实现 · ❌ 未实现

---

## 二、核心功能需求

### 2.1 增量备份系统

**需求描述：**
- 扫描世界目录，仅对发生变化的文件创建新 blob，减少备份时间和存储空间
- 使用 **XXHash64** 算法计算文件摘要（依赖 lz4-java / jpountz）
- 区块文件（`.mca`）默认仅对前 8KB 进行摘要（`chunk.full_hash=false`），可配置为全量 hash
- 非区块文件使用全量 XXHash64；分块读取（64KB buffer），避免大文件 OOM
- 相同 hash 的文件跨版本共享物理 blob，不重复存储

**实现状态：✅ 已实现**
- 核心逻辑：[`HashCalculator`](core/src/main/java/io/github/limuqy/mc/backup/hash/HashCalculator.java)、[`BackupEngine`](core/src/main/java/io/github/limuqy/mc/backup/backup/BackupEngine.java)

### 2.2 版本管理与存储

**需求描述：**
- 元数据支持多后端（**CSV 默认** / SQLite / MySQL 8），共用 schema v2 语义；详见 [元数据存储开发文档](metadata-storage.md)
- 物理文件按 blob 去重存储于 `<storage.path>/data/`，命名规则：`<相对路径>.<hash>[.zst]`
- CSV 元数据位于 `config/instantbackup/metadata/`；SQLite 位于 `config/instantbackup/backups.db`；MySQL 为远程库——均不随 `storage.path` 迁移
- 版本命名：`yyyyMMdd_HHmmss`（如 `20260628_143025`）
- 超出 `storage.max_versions` 的旧版本自动删除

**Blob 生命周期（`BlobState`）：**
| 状态 | 含义 |
|------|------|
| PENDING | 元数据已记录，物理内容仍依赖世界文件或 COW 捕获 |
| STAGED | 已复制为 raw 中间文件，等待压缩 |
| STORED | 已压缩为 `.zst` |

**版本状态（`VersionStatus`）：**
| 状态 | 含义 |
|------|------|
| IN_PROGRESS | 仍有 blob 未完成迁移/压缩 |
| COMPLETED | 所有 blob 均已 STORED |

**实现状态：✅ 已实现**
- 元数据存储：[`DatabaseManager`](core/src/main/java/io/github/limuqy/mc/backup/database/DatabaseManager.java) → [`MetadataStoreFactory`](core/src/main/java/io/github/limuqy/mc/backup/database/MetadataStoreFactory.java) 按配置创建后端
- CSV 默认：[`CsvMetadataStore`](core/src/main/java/io/github/limuqy/mc/backup/database/csv/CsvMetadataStore.java)
- SQLite：[`SqliteMetadataStore`](core/src/main/java/io/github/limuqy/mc/backup/database/sqlite/SqliteMetadataStore.java)（需 Minecraft SQLite JDBC 模组）
- MySQL 8：[`MysqlMetadataStore`](core/src/main/java/io/github/limuqy/mc/backup/database/mysql/MysqlMetadataStore.java)（Mod shade mysql-connector-j）
- 物理存储：[`BlobStore`](core/src/main/java/io/github/limuqy/mc/backup/storage/BlobStore.java)
- 启动恢复：服务器启动时 `recoverOnStartup()` 恢复 PENDING 映射并重入队 STAGED blob

### 2.3 区块文件保护机制（COW）

**需求描述：**
- 通过 Mixin 在 `RegionFileStorage.write` HEAD 拦截区块保存
- 当区块文件即将被覆盖且已被备份标记时，先将原内容复制到备份区（COW），再允许游戏写入
- `backup.deferred_chunk_migration=true`（默认）：区块 blob 先标记 PENDING，在即将被覆盖或执行 migrate 时才物理复制
- `backup.deferred_chunk_migration=false`：备份扫描时立即 capture 区块文件
- Mixin 回调必须静默失败，不得中断 MC 正常保存流程

**实现状态：✅ 已实现**
- Mixin：[`ChunkSaveMixin`](common/src/main/java/io/github/limuqy/mc/backup/mixins/ChunkSaveMixin.java)

### 2.4 压缩与导出

**需求描述：**
- 新 blob 捕获后由 `CompressionQueue` 多线程异步 ZSTD 压缩（等级 1–22 可配）
- 导出指定版本为 ZIP，用于手动回档
- 导出命令：`/backup export <序号>`（使用列表序号，非版本名）
- 导出文件命名：`InstantBackup_<版本名>.zip`，保存于 `storage.path` 目录
- 创建新备份前自动 migrate 所有 PENDING blob；也可手动 `/backup migrate <序号>`

**实现状态：✅ 已实现**
- 压缩：[`CompressionQueue`](core/src/main/java/io/github/limuqy/mc/backup/compression/CompressionQueue.java)
- 导出：[`BackupEngine.exportVersionOperation()`](core/src/main/java/io/github/limuqy/mc/backup/backup/BackupEngine.java)

---

## 三、交互功能需求

### 3.1 命令系统

**需求描述：**

| 命令 | 说明 | 状态 |
|------|------|------|
| `/backup help` | 显示帮助信息 | ✅ |
| `/backup create [备注]` | 手动触发备份（create 前自动 migrate） | ✅ |
| `/backup list` | 查看备份版本列表 | ✅ |
| `/backup delete <序号>` | 删除指定版本（blob GC） | ✅ |
| `/backup export <序号>` | 导出指定版本 ZIP | ✅ |
| `/backup migrate <序号>` | 封存指定版本及更早版本的 PENDING blob | ✅ |
| `/backup status` | 查看任务进度 | ✅ |
| `/backup config [设置] [值]` | 查看/修改配置 | ✅ |
| `/backup clean` | 清理所有备份数据 | ✅ |

**版本列表显示格式：**
```
=== 备份版本列表 ===
序号  版本名称              状态      类型     备注
----  --------------------  --------  -------  --------
1     20260628_143025       已完成    手动     主城建设完成
2     20260628_120000       进行中    自动     -
```

**权限：** 所有命令需要 **OP level 2**（[`PermissionCompat`](common/src/main/java/io/github/limuqy/mc/backup/compat/PermissionCompat.java)）

**回档方式：** 使用 `/backup export <序号>` 导出 ZIP，停服后手动替换世界目录。

**实现状态：✅ 已实现**

### 3.2 定时备份

**需求描述：**
- 按 `backup.interval`（秒）间隔自动触发备份
- `backup.only_when_players_online=true` 时，无玩家在线则跳过
- `backup.exclude_carpet_bots=true` 时，排除 Carpet Mod 假人

**实现状态：✅ 已实现**

- 调度：[`BackupScheduler`](common/src/main/java/io/github/limuqy/mc/backup/scheduler/BackupScheduler.java)
- tick 钩子：[`ServerTickMixin`](common/src/main/java/io/github/limuqy/mc/backup/mixins/ServerTickMixin.java) → [`BackupTickHandler`](common/src/main/java/io/github/limuqy/mc/backup/scheduler/BackupTickHandler.java)
- 地毯假人：[`CarpetCompat`](common/src/main/java/io/github/limuqy/mc/backup/compat/CarpetCompat.java)（按 `EntityPlayerMPFake` 类名识别，无 Carpet 编译依赖）

### 3.3 权限管理

**需求描述（未来增强）：**
- 集成 LuckPerms，按权限节点控制各命令
- 默认权限组仅允许查看，管理员可执行所有操作

**实现状态：❌ 未实现**
- 当前仅 OP level 2 门槛

### 3.4 国际化

**需求描述：**
- 模组消息支持 `zh_cn`（默认）与 `en_us`
- 通过 `general.language` 或 `/backup config language <语言>` 切换

**实现状态：✅ 已实现**

### 3.5 停服 CLI 与一键脚本

**需求描述：**
- 服务器启动时在 `config/instantbackup/` 生成 `InstantBackup.cmd` / `.sh`
- 独立 CLI JAR（`./gradlew :cli:shadowJar`）支持 REPL 与单次命令模式
- 停服后执行 `create` / `list` / `export` 等，与游戏内共用 `backups.db` 与 blob 存储
- CLI 离线模式强制 `deferred_chunk_migration=false`

**实现状态：✅ 已实现**
- CLI：[`CliMain`](cli/src/main/java/io/github/limuqy/mc/backup/cli/CliMain.java)、[`CliBootstrap`](cli/src/main/java/io/github/limuqy/mc/backup/cli/CliBootstrap.java)
- 脚本：[`ScriptGenerator`](common/src/main/java/io/github/limuqy/mc/backup/script/ScriptGenerator.java)
- **注意：** CLI help 文案提及 `config` 子命令，但 `BackupService` 尚未实现

---

## 四、性能与配置需求

### 4.1 线程池管理

**需求描述：**
- 备份/导出任务使用 `BackupThreadPool`（可配 `thread.count`）
- ZSTD 压缩使用独立的 `CompressionQueue`（可配 `compression.threads`）
- 所有 I/O 密集型操作异步执行，不阻塞游戏主线程

**实现状态：✅ 已实现**

### 4.2 配置文件

配置文件：`config/instantbackup/instantbackup.properties`（Properties 格式，带中文注释）

| 配置键 | 默认值 | 命令可改 | 说明 |
|--------|--------|----------|------|
| `general.language` | `zh_cn` | `config language` | 消息语言（zh_cn / en_us） |
| `script.enabled` | `true` | 否 | 是否生成一键脚本 |
| `backup.enabled` | `true` | `config enabled` | 是否启用定时备份 |
| `backup.interval` | `3600` | `config interval` | 定时备份间隔（秒） |
| `backup.only_when_players_online` | `true` | `config online_only` | 仅玩家在线时定时备份 |
| `backup.exclude_carpet_bots` | `true` | `config exclude_bots` | 排除地毯假人 |
| `backup.deferred_chunk_migration` | `true` | 否 | 区块延迟 COW |
| `chunk.full_hash` | `false` | 否 | 区块全量 hash |
| `compression.level` | `19` | `config compression` | ZSTD 等级（1–22） |
| `compression.threads` | `2` | 否 | 压缩消费者线程数 |
| `thread.count` | `2` | `config threads` | 备份线程池核心数 |
| `storage.max_versions` | `30` | `config max_versions` | 最大保留版本数（0=不限） |
| `storage.path` | `backups` | `config path` | 备份存储路径（改后需重启） |

**实现状态：✅ 已实现**
- 热更新：除 `storage.path`、`deferred_chunk_migration`、`chunk.full_hash`、`compression.threads`、`script.enabled` 外，其余可通过命令或文件即时生效

---

## 五、技术架构

### 5.1 模块结构

```
core/                          # 平台无关备份引擎
├── backup/                    # BackupEngine, BackupService
├── database/                  # DatabaseManager, schema v2
├── storage/                   # BlobStore
├── hash/                      # HashCalculator (XXHash64)
├── compression/               # CompressionQueue, ZstdCompressor
├── config/                    # BackupConfig
├── thread/                    # BackupThreadPool
└── i18n/                      # LangKeys, ModI18n

common/                        # Minecraft 集成层
├── backup/                    # BackupManager（Mod 侧门面）
├── command/                   # BackupCommandHandler
├── mixins/                    # ChunkSaveMixin, ServerTickMixin
├── scheduler/                 # BackupScheduler
├── script/                    # ScriptGenerator
├── compat/                    # 多版本 API 兼容层
├── test/                      # BackupSelfTest
└── i18n/                      # Messages

fabric/ | forge/ | neoforge/   # LoaderHelper + 入口点
cli/                           # 停服离线 REPL / 单次命令
```

### 5.2 依赖库

| 库 | 用途 |
|----|------|
| lz4-java (jpountz XXHash) | 文件变化检测哈希 |
| CSV（内置） | 默认元数据存储，无 JDBC |
| SQLite JDBC | 可选；Mod 依赖 Minecraft SQLite JDBC 模组，CLI 内嵌 xerial |
| mysql-connector-j | 可选 MySQL 8；Mod/CLI shade |
| zstd-jni | ZSTD 压缩；打包时仅保留 win/linux 的 x86/amd64/aarch64 原生库 |

### 5.3 数据库设计（schema v2）

```sql
CREATE TABLE schema_meta (
    version INTEGER NOT NULL
);

CREATE TABLE backup_versions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    version_name TEXT UNIQUE NOT NULL,       -- yyyyMMdd_HHmmss
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    description TEXT,
    file_count INTEGER DEFAULT 0,
    total_size BIGINT DEFAULT 0,
    is_manual BOOLEAN DEFAULT 0,
    status INTEGER DEFAULT 0                 -- 0=IN_PROGRESS, 1=COMPLETED
);

CREATE TABLE blobs (
    blob_key TEXT PRIMARY KEY,               -- path + hash 组合键
    file_path TEXT NOT NULL,
    file_hash TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    is_chunk BOOLEAN DEFAULT 0,
    state INTEGER NOT NULL,                  -- 0=PENDING, 1=STAGED, 2=STORED
    compressed_size BIGINT DEFAULT 0
);

CREATE TABLE file_info (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    version_id INTEGER NOT NULL,
    file_path TEXT NOT NULL,
    file_hash TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    is_chunk BOOLEAN DEFAULT 0,
    blob_key TEXT NOT NULL,
    FOREIGN KEY (version_id) REFERENCES backup_versions(id) ON DELETE CASCADE
);
```

### 5.4 备份流水线

```
触发（/backup create | CLI | 定时调度）
  → saveEverything（刷盘）
  → migrate PENDING blobs
  → 扫描世界目录 → XXHash64 增量检测
  → 新 blob: capture → STAGED → CompressionQueue → STORED
  → 区块 COW（ChunkSaveMixin）补充 PENDING blob
  → 写入元数据存储（CSV / SQLite / MySQL）→ 版本 COMPLETED
```

---

## 六、实现状态汇总

| 功能 | 状态 |
|------|------|
| 增量备份 + blob 去重 + COW | ✅ |
| 多元数据存储 schema v2 + 版本管理 | ✅ |
| 命令（create/list/delete/export/migrate/status/config/clean） | ✅ |
| ZSTD 异步压缩 + 双线程池 | ✅ |
| 配置热更新 + 一键脚本 + CLI | ✅ |
| i18n (zh_cn/en_us) + 启动恢复 | ✅ |
| 开发环境 SelfTest | ✅ |
| 定时备份 + 地毯假人检测 | ✅ |
| LuckPerms 细粒度权限 | ❌ |
| Boss Bar 进度显示 | ❌ |
| CLI `config` 子命令 | ❌ |

---

## 七、风险与挑战

1. **Mixin 兼容性**：不同 Minecraft 版本的区块保存机制可能不同；已通过 Manifold + compat 层支持多锚点版本
2. **性能影响**：大量文件的 hash 计算与压缩占用 CPU/磁盘 I/O；通过异步线程池缓解
3. **数据一致性**：PENDING blob 依赖 COW 时序；create 前自动 migrate 降低风险
4. **跨版本支持**：多加载器 + 多 MC 锚点增加测试和维护成本
5. **字节码降级**：低版本锚点（Java 8 / 16 / 17）需 JVMDowngrader 将编译产物降级至目标 Java 版本
