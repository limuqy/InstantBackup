# Instant Backup 架构参考

## 备份流水线

```
/backup create 或定时调度
  → BackupManager.createBackup()
  → 扫描 world 目录，对比 hash 缓存（增量）
  → 新/变更文件 → BlobStore 写入 + CompressionQueue（Zstd）
  → DatabaseManager 记录 backup_versions / file_info / blobs

区块保存（COW）
  → ChunkSaveMixin.beforeChunkWrite()
  → BackupManager.onChunkSave(relativePath)
  → 首次写入前复制原始 region 内容到 pendingCaptureMap
```

## 核心单例

| 类 | 职责 |
|----|------|
| `ExampleMod` | 模组入口状态：server、路径、LoaderHelper |
| `BackupManager` | 备份/迁移/导出主逻辑 |
| `DatabaseManager` | SQLite（backups.db） |
| `BlobStore` | 备份目录下的 blob 文件读写 |
| `CompressionQueue` | 后台 Zstd 压缩队列 |
| `BackupThreadPool` | 通用异步任务池 |
| `BackupScheduler` | 定时备份调度 |
| `BackupConfig` | instantbackup.properties 读写 |

## LoaderHelper 接口

```java
String getModVersion();
Path getConfigDir();
void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher);
List<String> getModList();
```

命令注册统一委托 `CommandRegistry.register(dispatcher)`。

## 命令一览

| 命令 | 说明 |
|------|------|
| `/backup create [备注]` | 手动备份（create 前自动迁移 pending blob） |
| `/backup list` | 版本列表 |
| `/backup delete <序号>` | 删除 |
| `/backup export <序号>` | 导出 ZIP（手动回档） |
| `/backup migrate <序号>` | 迁移 blob |
| `/backup status` | 进度/队列状态 |
| `/backup config [key value]` | 查看或修改配置 |
| `/backup clean` | 清理 |
| `/backup help` | 帮助 |

权限要求：OP 等级 2（`hasPermission(2)`）。

## Gradle 插件链（buildSrc）

```
common.gradle      → Java + Manifold + JVMDowngrader + 资源 expand
minecraft.gradle   → Unimined MC + MojMap
unimined-*.gradle  → 各 loader 依赖与 runClient/runServer
```

`settings.gradle` 按 `versionProperties` 中 `builds_for` 动态 include 子模块。

## 数据库表（简要）

- `backup_versions` — 版本元数据（名称、时间、file_count、total_size、status）
- `file_info` — 每个版本包含的文件路径与 hash
- `blobs` — 去重 blob 存储索引

完整性：`backup_versions.file_count` 应等于对应 `file_info` 行数。

## 已知陷阱

1. **Windows 文件锁**：`session.lock` 等文件可能无法读取，扫描应跳过而非失败
2. **Java 模块限制**：避免依赖需 `--add-opens` 的库；优先标准库（如 `CRC32C`）
3. **日志可见性**：SLF4J 不一定出现在游戏聊天；关键错误可同时 `System.err.println`
4. **Mixin 时机**：`ExampleMod.mod()` 在 SERVER_STARTING 前不可用，Mixin 内需 try/catch

## 开发自检

`runServer` 默认注入 `-Dinstantbackup.selftest=true`（见 `unimined-*.gradle`）。

`BackupSelfTest` 流程：create → 等待 idle → list → migrate → 校验 backups 目录结构。
