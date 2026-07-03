# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Multi-loader Minecraft server backup mod (Fabric / Forge / NeoForge). Incremental blob-based backups with XXHash64 dedup, async ZSTD compression, chunk copy-on-write capture, and a stop-server CLI.

- **Mod ID:** `instantbackup`
- **Package root:** `io.github.limuqy.mc.backup`
- **Primary language:** Chinese (code comments, log messages, docs)

## Build commands

```bash
# Build all loaders for a specific MC version (REQUIRED - no default works without -Pmc_ver)
./gradlew build -Pmc_ver=1.20.1

# Single loader
./gradlew :fabric:build -Pmc_ver=1.20.1
./gradlew :forge:build -Pmc_ver=1.20.1
./gradlew :neoforge:build -Pmc_ver=1.20.1

# Standalone CLI JAR (no MC dependency)
./gradlew :cli:shadowJar
# Output: cli/build/libs/instantbackup-cli-<mod_version>-all.jar

# Dev server (auto-injects -Dinstantbackup.selftest=true)
./gradlew :fabric:runServer -Pmc_ver=1.20.1
./gradlew :forge:runServer -Pmc_ver=1.20.1
./gradlew :neoforge:runServer -Pmc_ver=1.20.1

# Build ALL versions (auto-selects correct JDK per version, Windows PowerShell)
powershell -File scripts/build-all.ps1
powershell -File scripts/build-all.ps1 -Versions 1.20.1,26.2  # specific versions
powershell -File scripts/build-all.ps1 -Clean                  # clean before build
```

Valid `mc_ver` anchors: `1.16.5`, `1.17.1`, `1.18.2`, `1.19.4`, `1.20.1`, `1.20.4`, `1.20.6`, `1.21.4`, `1.21.11`, `26.2` (see `versionProperties/`). Which loaders build for each version is controlled by `builds_for` in `versionProperties/<version>.properties` — not all loaders exist for all versions (e.g. 1.16.5 has no NeoForge).

Requires JDK 25+ (to compile for 26.2's Java 25 target). Gradle Wrapper is included.

## Testing

There is no unit test suite — verification is done via in-game self-test and RCON-driven integration scripts.

```bash
# Start dev server and watch for [SelfTest] PASS in logs
./gradlew :fabric:runServer -Pmc_ver=1.20.1
# Check: fabric/run/server/logs/latest.log

# RCON-based full integration test (Windows PowerShell)
powershell -File scripts/verify_backup_rcon.ps1 -Loader fabric

# Wait for SelfTest result in a log file (useful for automation/CI-style runs)
powershell -File scripts/wait_selftest.ps1 -LogFile <path> -TimeoutSec 600

# CLI smoke test
./gradlew :cli:shadowJar
java -jar cli/build/libs/instantbackup-cli-*-all.jar --server-dir <server_root> list
java -jar cli/build/libs/instantbackup-cli-*-all.jar --server-dir <server_root> create smoke_test
```

`runServer` injects `-Dinstantbackup.selftest=true` automatically. `BackupSelfTest` runs on `SERVER_STARTED`: create → wait idle → list → migrate → verify `backups/` directory structure.

To test a specific metadata backend, set `storage.metadata.type` in `instantbackup.properties` before starting (`csv` default, `sqlite` requires the Minecraft SQLite JDBC mod in dev/`run/mods`, `mysql` requires a reachable MySQL 8 instance — see `docs/metadata-storage.md`).

## Architecture

### Module layout and dependency direction

| Module | Role | MC dependency |
|--------|------|:-------------:|
| `core/` | Platform-agnostic backup engine (SQLite/CSV/MySQL metadata, ZSTD compression, blob store, config, i18n) | No |
| `common/` | MC integration: commands, mixins, scheduler, compat layer | Yes |
| `fabric/` | Fabric entrypoint + `FabricLoaderHelper` | Yes |
| `forge/` | Forge entrypoint + `ForgeLoaderHelper` | Yes |
| `neoforge/` | NeoForge entrypoint + `NeoForgeLoaderHelper` | Yes |
| `cli/` | Offline stop-server CLI, shares `core/` (SQLite/CSV storage) with the in-game system | No |
| `buildSrc/` | Gradle convention plugins (Unimined, Manifold, JVMDowngrader, Forgix) | — |

Key rule: `core/` must never import Minecraft classes. `common/` must never import Fabric/Forge/NeoForge classes — go through the `LoaderHelper` interface instead. Loader modules compile `common/` and `core/` source directly (`minecraft.gradle` adds them as source sets on the loader's `compileJava` task, not just jar dependencies), so loader jars include all common code.

### Core singletons (in `common`/`core`)

| Class | Responsibility |
|-------|-----------------|
| `ExampleMod` | Mod entry state: server instance, paths, `LoaderHelper`. Accessed via `ExampleMod.mod()`. **Null before `SERVER_STARTING`** — mixins and early init must guard against this |
| `BackupManager` | Backup/migrate/export main logic |
| `DatabaseManager` | Metadata façade singleton; delegates to a `MetadataStore` implementation (SQLite/CSV/MySQL) |
| `BlobStore` | Reads/writes blob files under the backup storage directory |
| `CompressionQueue` | Background ZSTD compression queue |
| `BackupThreadPool` | Shared async task pool |
| `BackupScheduler` | Scheduled/periodic backup triggering |
| `BackupConfig` | Reads/writes `instantbackup.properties` |

### Backup pipeline

```
/backup create  or scheduled trigger
  → BackupManager.createBackup()
  → scan world directory, diff against hash cache (incremental)
  → new/changed files → BlobStore write + CompressionQueue (Zstd)
  → DatabaseManager records backup_versions / file_info / blobs

Chunk save (copy-on-write)
  → ChunkSaveMixin.beforeChunkWrite()  (injected at RegionFileStorage.write, HEAD)
  → BackupManager.onChunkSave(relativePath)
  → on first write, copies the original region content into pendingCaptureMap before it's overwritten
```

Blob lifecycle (`BlobState`): `PENDING` (registered, physical content still depends on world/COW) → `STAGED` (raw copied, awaiting compression) → `STORED` (`.zst` written). `backup_versions.status` is `IN_PROGRESS` until all its blobs are `STORED`, then `COMPLETED`.

### LoaderHelper abstraction

Platform differences are isolated behind the `LoaderHelper` interface, implemented per-loader as `FabricLoaderHelper` / `ForgeLoaderHelper` / `NeoForgeLoaderHelper`:

```java
String getModVersion();
Path getConfigDir();
void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher);
List<String> getModList();
```

Command registration is delegated uniformly to `CommandRegistry.register(dispatcher)`.

### Compat layer

`common/.../compat/` wraps MC API differences across versions: `ChatCompat`, `CommandCompat`, `PermissionCompat`, `ServerCompat`, `ModLog`, `CarpetCompat`. Check here first when adding any MC API call that differs between anchors.

### Multi-version code (Manifold preprocessor)

Version branching uses Manifold `#if` directives with constants generated into `build.properties` by `root.gradle` (e.g. `MC_1_20_1=4`, `MC_VER=4` when building with `-Pmc_ver=1.20.1`):

```java
#if MC_VER <= MC_1_18_2
    // old API
#else
    // new API
#endif
```

Rules:
- `#if` blocks are for **API signature differences only**, not business logic
- Version-specific MC API differences belong in `compat/` classes instead
- `build.properties` is auto-generated and gitignored — never edit manually; changing MC version requires a rebuild

### Mixin rules

- All `@Inject` callbacks must be wrapped in try/catch with `catch (Throwable)` (not `catch (Exception)`) — mixins must never crash the game
- New mixins must be registered in `common/src/main/resources/instantbackup.mixins.json`
- `ExampleMod.mod()` is unavailable before `SERVER_STARTING`; mixin bodies must handle that

### Gradle plugin chain (buildSrc)

`root.gradle` → `common.gradle` (Java + Manifold + JVMDowngrader + resource `${}` expansion) → `minecraft.gradle` (Unimined MC + MojMap) → `unimined-*.gradle` (per-loader deps + `runClient`/`runServer`).

- `settings.gradle` dynamically includes subprojects based on `builds_for` in `versionProperties/<version>.properties`
- `common.gradle` expands `${version}`, `${mod_id}`, `${java_version}`, etc. in `pack.mcmeta`, `fabric.mod.json`, `META-INF/mods.toml`, `META-INF/neoforge.mods.toml`, and `*.mixins.json` — don't hardcode version strings in those files
- **Forgix** merges multi-loader outputs into a single JAR per MC version when `builds_for` lists more than one loader
- **JVMDowngrader** downgrades compiled bytecode to match `java_version` from the version properties (Java 8 for 1.16.5, Java 16 for 1.17.1, Java 17 for 1.18.2–1.21.11, Java 25 for 26.2)

### Metadata storage backends

`storage.metadata.type` selects the backend behind `DatabaseManager` (façade) → `MetadataStoreFactory` → `MetadataStore` impl. All three share the same logical schema (schema v2: `backup_versions`, `blobs`, `file_info`, `schema_meta`) — switching backends does **not** migrate existing data.

| Backend | Storage | Notes |
|---------|---------|-------|
| `csv` (default) | `config/instantbackup/metadata/*.csv` | Zero extra deps beyond OpenCSV (shaded); atomic write via temp file + `ATOMIC_MOVE` |
| `sqlite` | `config/instantbackup/backups.db` | Mod does **not** shade the JDBC driver — requires the separate "Minecraft SQLite JDBC" mod at runtime; CLI shadowJar embeds `org.xerial:sqlite-jdbc` itself |
| `mysql` | Remote MySQL 8 (`storage.mysql.*` config) | Mod shades `mysql-connector-j`; blob binaries are never stored in MySQL, only metadata; tables prefixed with `storage.mysql.table_prefix` (default `ib_`) |

Physical blob storage (`storage.path/data/`, ZSTD-compressed) is always local/filesystem-based regardless of metadata backend. See `docs/metadata-storage.md` for full schema, DDL, and CLI/env-var details (e.g. `INSTANTBACKUP_MYSQL_PASSWORD`).

### Commands

All commands require OP level 2 (`hasPermission(2)`): `/backup create [note]`, `list`, `delete <n>`, `export <n>`, `migrate <n>`, `status`, `config [key value]`, `clean`, `help`.

## File conventions

- Blob naming: `data/<relative-path>.<xxhash64>.zst`
- Config: `config/instantbackup/instantbackup.properties`
- Metadata: `config/instantbackup/metadata/` (CSV, default) or `config/instantbackup/backups.db` (SQLite) or remote MySQL
- Backups: `backups/` (configurable via `storage.path`, supports absolute cross-drive paths)

## Common pitfalls

1. **`ExampleMod.mod()` is null before `SERVER_STARTING`** — mixin callbacks and early init code must guard against this
2. **Windows `session.lock`** — may be locked by a running server; scanning must skip such files, not fail
3. **Don't import loader APIs in `common/`** — add to the `LoaderHelper` interface instead
4. **Don't put business logic in `#if` blocks** — use `compat/` classes
5. **Resource files use `${}` expansion** — don't hardcode version strings in mod metadata files
6. **`build.properties` is gitignored and auto-generated** — changing MC version requires a rebuild, not a manual edit
7. **Java module limits** — avoid libraries that need `--add-opens`; prefer standard library equivalents (e.g. `CRC32C`)
8. **Log visibility** — SLF4J output doesn't always reach the in-game chat; log critical errors with `System.err.println` too
