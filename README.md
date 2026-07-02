# 极速备份

<p align="center">
  <img src="docs/logo.svg" alt="极速备份 Instant Backup" width="128"/>
</p>

<p align="center">
  <a href="README.en.md">English</a> · <strong>简体中文</strong>
</p>

<p align="center">
  <strong>极速备份</strong> · Instant Backup<br/>
  <sub>面向 Minecraft 专用服务器的增量存档备份模组</sub>
</p>

<p align="center">
  Blob 去重 · 区块 COW · 异步 ZSTD · 定时备份 · 停服 CLI
</p>

> 仓库地址：[github.com/limuqy/InstantBackup](https://github.com/limuqy/InstantBackup)

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.16.5–26.2-green.svg)](https://www.minecraft.net/)
[![Loaders](https://img.shields.io/badge/Loaders-Fabric%20%7C%20Forge%20%7C%20NeoForge-orange.svg)](#支持的版本)

---

## 特性一览

| 能力 | 说明 |
|------|------|
| **增量备份** | 基于 XXHash64 检测文件变化，仅备份新增或变更内容 |
| **Blob 去重** | 相同内容跨备份版本共享物理文件，显著节省磁盘空间 |
| **区块 COW** | Mixin 拦截区块写入，在覆盖前捕获原始 region 数据 |
| **异步压缩** | 独立线程池 ZSTD 压缩，不阻塞游戏主线程 |
| **定时备份** | 可配置间隔、玩家在线检测、Carpet 假人排除 |
| **游戏内命令** | `/backup create` / `list` / `export` / `delete` 等完整子命令 |
| **停服 CLI** | 服务器关闭后仍可管理备份，与游戏内共用同一数据库 |
| **一键脚本** | 自动生成 `InstantBackup.cmd` / `.sh`，双击进入交互式 REPL |
| **多加载器** | 单一代码库构建 Fabric / Forge / NeoForge |
| **多版本** | Manifold 预处理 + compat 层，支持多个 MC 锚点版本 |
| **国际化** | 内置 `zh_cn`（默认）与 `en_us` |

---

## 支持的版本

| Minecraft | Java | Fabric | Forge | NeoForge |
|-----------|:----:|:------:|:-----:|:--------:|
| 1.16.5 | 8 | ✅ | ✅ | — |
| 1.17.1 | 16 | ✅ | ✅ | — |
| 1.18.2 | 17 | ✅ | ✅ | — |
| 1.19.4 | 17 | ✅ | ✅ | — |
| 1.20.1 | 17 | ✅ | ✅ | ✅ |
| 1.20.4 | 21 | ✅ | ✅ | ✅ |
| 1.20.6 | 21 | ✅ | ✅ | ✅ |
| 1.21.4 | 21 | ✅ | ✅ | ✅ |
| 1.21.11 | 21 | ✅ | ✅ | ✅ |
| 26.2 | 25 | ✅ | ✅ | ✅ |

> 各锚点的 `compatible_mc_version_range` 声明同系列补丁版兼容范围（如 1.20.1 锚点为 `[1.20,1.21)`，覆盖 1.20–1.20.4）。Loader 组合由 `versionProperties/<version>.properties` 中的 `builds_for` 决定。

构建指定版本：

```bash
./gradlew build -Pmc_ver=1.20.1
# 完整锚点列表见 versionProperties/
```

---

## 安装

1. 从 [Releases](https://github.com/limuqy/InstantBackup/releases) 下载对应 **Minecraft 版本** 与 **加载器** 的 JAR（Forgix 合并后的统一包）。
2. 将 JAR 放入服务器 `mods/` 目录。
3. 启动服务器；模组会在 `config/instantbackup/` 自动生成配置文件。

**依赖：** Fabric 端需要安装对应版本的 Fabric API（开发构建已包含）。

---

## 快速开始

### 手动备份

```
/backup create 主城建设完成
/backup list
/backup status
```

### 导出与回档

模组**不提供**游戏内一键回档，推荐流程为：

1. `/backup export <序号>` — 导出指定版本为 ZIP
2. **停服**
3. 解压 ZIP，用备份内容**替换**世界目录
4. 启动服务器

导出文件默认保存为 `backups/InstantBackup_<版本名>.zip`。

### 定时备份

默认每 **3600 秒**自动备份一次。常用配置：

```
/backup config interval 1800      # 间隔 30 分钟
/backup config enabled true       # 启用定时备份
/backup config online_only true   # 仅玩家在线时备份
/backup config exclude_bots true  # 排除 Carpet 假人
```

---

## 命令参考

所有命令需要 **OP 等级 2**。

| 命令 | 说明 |
|------|------|
| `/backup help` | 显示帮助 |
| `/backup create [备注]` | 手动触发备份（create 前自动 migrate PENDING blob） |
| `/backup list` | 查看备份版本列表 |
| `/backup delete <序号>` | 删除指定版本（含 blob GC） |
| `/backup export <序号>` | 导出指定版本为 ZIP |
| `/backup migrate <序号>` | 封存指定版本及更早版本的 PENDING blob |
| `/backup status` | 查看备份/压缩任务进度 |
| `/backup config [键] [值]` | 查看或修改配置 |
| `/backup clean` | 清理所有备份数据 |

列表示例：

```
=== 备份版本列表 ===
序号  版本名称              状态      类型     备注
----  --------------------  --------  -------  --------
1     20260628_143025       已完成    手动     主城建设完成
2     20260628_120000       进行中    自动     -
```

---

## 配置

配置文件：`config/instantbackup/instantbackup.properties`

| 配置键 | 默认值 | 命令可改 | 说明 |
|--------|--------|:--------:|------|
| `general.language` | `zh_cn` | ✅ | 消息语言（`zh_cn` / `en_us`） |
| `backup.enabled` | `true` | ✅ | 是否启用定时备份 |
| `backup.interval` | `3600` | ✅ | 定时备份间隔（秒） |
| `backup.only_when_players_online` | `true` | ✅ | 仅玩家在线时定时备份 |
| `backup.exclude_carpet_bots` | `true` | ✅ | 排除 Carpet Mod 假人 |
| `compression.level` | `19` | ✅ | ZSTD 压缩等级（1–22） |
| `thread.count` | `2` | ✅ | 备份线程池核心数 |
| `storage.max_versions` | `30` | ✅ | 最大保留版本数（0 = 不限） |
| `storage.path` | `backups` | ✅ | 备份存储路径（**改后需重启**） |
| `backup.deferred_chunk_migration` | `true` | ❌ | 区块延迟 COW |
| `chunk.full_hash` | `false` | ❌ | 区块文件全量 hash |
| `script.enabled` | `true` | ❌ | 是否生成停服一键脚本 |

跨盘符示例：在配置文件中设置 `storage.path=D:/minecraft-backups` 并重启，blob 写入 D 盘，数据库仍位于 `config/instantbackup/backups.db`。

---

## 停服 CLI 与一键脚本

服务器启动时，若 `script.enabled=true`，会在 `config/instantbackup/` 生成：

- **Windows：** `InstantBackup.cmd`
- **Linux / macOS：** `InstantBackup.sh`

停服后双击脚本（或执行 CLI JAR）即可进入交互式 REPL，执行 `create`、`list`、`export` 等操作，与游戏内共用 `backups.db` 与 blob 存储。

独立 CLI 构建：

```bash
./gradlew :cli:shadowJar
# 产物：cli/build/libs/instantbackup-cli-<version>-all.jar
```

单次命令模式（适合 cron / CI）：

```bash
java -jar instantbackup-cli-1.0.0-all.jar --server-dir /path/to/server create "cron-backup"
```

---

## 目录结构

```
<server>/
├── world/                          # 世界存档
├── backups/                        # 备份目录（默认 storage.path）
│   ├── data/                       # blob 物理文件（去重 + ZSTD）
│   └── InstantBackup_<版本名>.zip  # 导出的 ZIP
└── config/instantbackup/
    ├── instantbackup.properties    # 配置文件
    ├── backups.db                  # SQLite 元数据
    ├── InstantBackup.cmd           # Windows 停服脚本
    └── InstantBackup.sh            # Unix 停服脚本
```

**Blob 命名：** `data/<相对路径>.<hash>.zst`

---

## 工作原理

```
触发（/backup create | 定时调度 | CLI）
  → 刷盘（saveEverything）
  → migrate 所有 PENDING blob
  → 扫描世界目录 → XXHash64 增量检测
  → 新 blob：capture → STAGED → ZSTD 压缩 → STORED
  → 区块 COW（Mixin）补充 PENDING blob
  → 写入 SQLite → 版本标记 COMPLETED
```

**Blob 生命周期：**

| 状态 | 含义 |
|------|------|
| `PENDING` | 元数据已记录，物理内容仍依赖世界文件或 COW |
| `STAGED` | 已复制为 raw 中间文件，等待压缩 |
| `STORED` | 已压缩为 `.zst` |

**定时备份：** 每服务器 tick 由 `ServerTickMixin` 驱动 `BackupScheduler`；Carpet 假人通过类名 `carpet.patches.EntityPlayerMPFake` 识别，无需 Carpet 作为编译依赖。

---

## 从源码构建

**环境要求：** JDK 25+（编译 26.2 目标所需；低版本锚点由 JVMDowngrader 降级字节码）。Gradle Wrapper 已包含。

```bash
# 构建默认版本（gradle.properties 中 mc_ver=1.20.1）
./gradlew build

# 构建指定 MC 版本
./gradlew build -Pmc_ver=1.21.11

# 仅构建 Fabric
./gradlew :fabric:build -Pmc_ver=1.20.1

# 启动测试服务器（自动注入 SelfTest）
./gradlew :fabric:runServer -Pmc_ver=1.20.1
```

开发环境自检：日志中搜索 `[SelfTest] PASS`（`runServer` 默认注入 `-Dinstantbackup.selftest=true`）。

### 项目结构

```
InstantBackup/
├── core/           # 平台无关备份引擎（BackupEngine、SQLite、ZSTD）
├── common/         # Minecraft 集成（命令、Mixin、调度器）
├── fabric/         # Fabric 入口
├── forge/          # Forge 入口
├── neoforge/       # NeoForge 入口
├── cli/            # 停服离线 CLI
├── buildSrc/       # Gradle 插件（Unimined、Manifold、Forgix）
└── versionProperties/  # 各 MC 锚点版本配置
```

---

## 文档

| 文档 | 说明 |
|------|------|
| [docs/requirements.md](docs/requirements.md) | 功能需求与实现状态 |
| [docs/functional-testing.md](docs/functional-testing.md) | 功能测试用例 |

---

## 许可证

本项目采用 [GNU General Public License v3.0 or later](LICENSE) 开源。

---

## 作者

**limuqy** — [GitHub](https://github.com/limuqy)

如有问题或建议，欢迎在 [Issues](https://github.com/limuqy/InstantBackup/issues) 中反馈。
