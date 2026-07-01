---
name: instantbackup-mod
description: >-
  Develops Instant Backup Minecraft mod (Fabric/Forge/NeoForge multi-loader).
  Covers common vs loader-specific code, LoaderHelper pattern, Mixins, backup
  pipeline, Gradle/Unimined builds, and server testing. Use when working in
  InstantBackup, adding mod features, fixing backup/Mixin bugs, or building
  and testing across loaders.
---

# Instant Backup Mod 开发

## 核心原则

1. **共享逻辑放 `common/`，平台差异放各 loader 模块**
2. **Loader 无关 API 通过 `LoaderHelper` 抽象**，不在 common 中直接引用 Fabric/Forge API
3. **Mixin 必须静默失败**，不得中断 MC 正常保存/加载流程
4. **备份任务异步执行**，关键路径用 `catch (Throwable)` 而非仅 `catch (Exception)`
5. **代码注释使用中文**；日志前缀统一 `[Instant Backup]`

## 模块布局

| 模块 | 职责 |
|------|------|
| `common/` | 备份逻辑、数据库、命令、Mixin、配置 |
| `fabric/` | `ExampleModFabric`、`FabricLoaderHelper` |
| `forge/` | `ExampleModForge`、`ForgeLoaderHelper` |
| `neoforge/` | `ExampleModNeoForge`、`NeoForgeLoaderHelper` |

包根：`io.github.limuqy.mc.backup` · Mod ID：`instantbackup`（`ExampleMod.MOD_ID`）

## 新增功能放哪里

```
需要 MinecraftServer / 世界路径？
  → common/，通过 ExampleMod.mod() 获取

需要 Fabric/Forge 特有 API？
  → 先在 LoaderHelper 加方法，再在三个 *LoaderHelper 实现

Hook 游戏内部类（如 RegionFileStorage）？
  → common/.../mixins/，并更新 instantbackup.mixins.json

Brigadier 命令？
  → CommandRegistry（注册）+ BackupCommandHandler（逻辑）
```

## 生命周期

```
Loader 入口 onInitialize/@Mod 构造
  → ExampleMod.initializeForServer(new XxxLoaderHelper())
  → 注册命令回调 / Forge 事件

SERVER_STARTING
  → ExampleMod.mod().onServerStarting(server)
  → BackupConfig / DatabaseManager / BackupManager 初始化

SERVER_STARTED（开发环境）
  → -Dinstantbackup.selftest=true 时运行 BackupSelfTest

SERVER_STOPPING
  → ExampleMod.mod().onServerStopping()
```

## 路径约定

| 资源 | 路径 |
|------|------|
| 配置 | `<server>/config/instantbackup/instantbackup.properties` |
| 数据库 | `<server>/config/instantbackup/backups.db` |
| 备份数据 | `<server>/backups/`（默认，由 `storage.path` 配置，支持绝对路径跨盘符） |
| Blob 存储 | `<backupPath>/data/` |
| 一键脚本 | `<server>/config/instantbackup/InstantBackup.cmd` / `InstantBackup.sh`（服务器启动时自动生成） |

## 构建与测试

**环境要求：** JDK 25+（编译 26.2 目标所需；低版本锚点由 JVMDowngrader 降级）。

```bash
# Mod 构建
./gradlew build "-Pmc_ver=1.20.1"

# 独立 CLI JAR（cron/CI；日常推荐用配置目录脚本）
./gradlew :cli:shadowJar

# 停服后：双击 config/instantbackup/InstantBackup.cmd 进入交互式 REPL
```

```powershell
# 全锚点构建（本地）
$versions = @("1.16.5","1.17.1","1.18.2","1.19.4","1.20.1","1.20.4","1.20.6","1.21.4","1.21.11","26.2")
foreach ($v in $versions) { ./gradlew build "-Pmc_ver=$v" }

# 启动测试服务器（runServer 已注入 selftest JVM 参数）
./gradlew :fabric:runServer "-Pmc_ver=1.20.1"
./gradlew :forge:runServer "-Pmc_ver=1.20.1"
./gradlew :neoforge:runServer "-Pmc_ver=1.20.1"

# 单 loader 构建
./gradlew :fabric:build "-Pmc_ver=1.20.1"
```

验证备份功能时优先查看 `fabric/run/server/logs/latest.log` 中的 `[SelfTest]` 输出。

RCON 全链路验证（Windows）：

```powershell
powershell -File scripts/verify_backup_rcon.ps1
```

## Mixin 规范

- 类放在 `io.github.limuqy.mc.backup.mixins`
- 新增 Mixin 必须写入 `common/src/main/resources/instantbackup.mixins.json`
- `@Inject` 回调内用 try/catch 包裹，**禁止抛出异常**
- 参考 `ChunkSaveMixin`：在 `RegionFileStorage.write` HEAD 触发 COW

## 多版本代码

### 锚点版本与 Loader

| 锚点 | Java | Loader |
|------|------|--------|
| 1.16.5 | 8 | fabric, forge |
| 1.17.1 | 16 | fabric, forge |
| 1.18.2 / 1.19.4 | 17 | fabric, forge |
| 1.20.1 | 17 | fabric, forge, neoforge |
| 1.20.4 / 1.20.6 | 21 | fabric, forge, neoforge |
| 1.21.4 / 1.21.11 | 21 | fabric, forge, neoforge |
| 26.2 | 25 | fabric, forge, neoforge |

有效 `mc_ver`：`1.16.5`, `1.17.1`, `1.18.2`, `1.19.4`, `1.20.1`, `1.20.4`, `1.20.6`, `1.21.4`, `1.21.11`, `26.2`（对应 `versionProperties/<version>.properties`）

配置：`versionProperties/<version>.properties`（`builds_for`、`compatible_mc_versions`、`forge_loader_version_range`）

### Manifold 预处理

- 常量由 [root.gradle](buildSrc/src/main/groovy/root.gradle) 生成（如 `MC_1_16_5=0`、`MC_VER=3`）
- 同一源文件内分支：`#if MC_VER <= MC_1_18_2` … `#else` … `#endif`
- **业务逻辑不放 `#if`**；版本差异沉入 `compat/` 或 loader 入口

### compat 兼容层（common/.../compat/）

| 类 | 用途 |
|----|------|
| `ChatCompat` | `Component.translatable` / `empty`（1.16 用 TextComponent/TranslatableComponent） |
| `CommandCompat` | `sendSuccess` Supplier 重载差异 |
| `PermissionCompat` | `hasPermission` / `hasPermissionLevel` |
| `ServerCompat` | `saveEverything` / `saveAllChunks` |
| `ModLog` | 1.16 Log4j ↔ 1.19+ SLF4J |

新增 MC API 调用时，先检查是否需加入 compat 层，避免在业务类散落 `#if`。

### 编译产物

- **每个锚点单独 `-Pmc_ver` 构建一个 JAR**（Forgix 合并同版本多 Loader）
- 字节码降级：JVMDowngrader → `java_version` 目标

## 修改后检查清单

- [ ] 改动涉及构建时，至少抽测 `1.16.5`、`1.20.1`、`1.21.11`（或 `26.2`）的 `./gradlew build "-Pmc_ver=X"` 通过
- [ ] 改动在 common 中未引入 loader 专属 import
- [ ] 三个 loader 入口生命周期保持一致
- [ ] 异步任务异常可被日志捕获
- [ ] 若改命令：同步更新 `BackupCommandHandler` 与 `CommandRegistry`

## 详细参考

- 架构与备份流水线：[reference.md](reference.md)
- 功能测试步骤：[docs/functional-testing.md](docs/functional-testing.md)
