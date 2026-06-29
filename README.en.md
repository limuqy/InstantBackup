# Instant Backup

<p align="center">
  <img src="docs/logo.svg" alt="Instant Backup / 极速备份" width="128"/>
</p>

<p align="center">
  <strong>English</strong> · <a href="README.md">简体中文</a>
</p>

<p align="center">
  <strong>Instant Backup</strong> · <a href="README.md">极速备份</a><br/>
  <sub>Incremental world backup mod for Minecraft dedicated servers</sub>
</p>

<p align="center">
  Blob dedup · Chunk COW · Async ZSTD · Scheduled backup · Offline CLI
</p>

> Repository: [github.com/limuqy/InstantBackup](https://github.com/limuqy/InstantBackup)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.18.2–1.21.x-green.svg)](https://www.minecraft.net/)
[![Loaders](https://img.shields.io/badge/Loaders-Fabric%20%7C%20Forge%20%7C%20NeoForge-orange.svg)](#supported-versions)

---

## Features

| Feature | Description |
|---------|-------------|
| **Incremental backup** | XXHash64 change detection; only new or modified files are backed up |
| **Blob deduplication** | Identical content shared across backup versions to save disk space |
| **Chunk COW** | Mixin hooks chunk writes and captures original region data before overwrite |
| **Async compression** | Dedicated ZSTD thread pool; does not block the game main thread |
| **Scheduled backup** | Configurable interval, online-player check, Carpet bot exclusion |
| **In-game commands** | Full `/backup` subcommands: `create`, `list`, `export`, `delete`, etc. |
| **Offline CLI** | Manage backups after server shutdown; shares the same database as in-game |
| **One-click scripts** | Auto-generated `InstantBackup.cmd` / `.sh` for interactive REPL |
| **Multi-loader** | Single codebase for Fabric, Forge, and NeoForge |
| **Multi-version** | Manifold preprocessing + compat layer for multiple MC anchor versions |
| **i18n** | Built-in `zh_cn` (default) and `en_us` |

---

## Supported Versions

### Recommended & QA scope

The following anchor versions are **recommended for production use and QA**:

| Minecraft | Fabric | Forge | NeoForge |
|-----------|:------:|:-----:|:--------:|
| 1.18.2 | ✅ | ✅ | — |
| 1.19.4 | ✅ | ✅ | — |
| 1.20.1 | ✅ | ✅ | ✅ |
| 1.20.4 | ✅ | ✅ | ✅ |
| 1.21.1 | ✅ | ✅ | ✅ |
| 1.21.4 | ✅ | ✅ | ✅ |

> **Note:** 1.16.5 still has build support and compat layers, but is **outside the current QA scope**. Loader combinations per version are defined in `versionProperties/<version>.properties` (`builds_for`).

Build a specific version:

```bash
./gradlew build -Pmc_ver=1.20.1
```

---

## Installation

1. Download the JAR for your **Minecraft version** and **mod loader** from [Releases](https://github.com/limuqy/InstantBackup/releases) (Forgix merged universal jar).
2. Place the JAR in the server `mods/` folder.
3. Start the server; config files are generated under `config/instantbackup/`.

**Dependencies:** Fabric servers require the matching Fabric API (included in dev builds).

---

## Quick Start

### Manual backup

```
/backup create main base done
/backup list
/backup status
```

### Export & rollback

There is **no in-game one-click restore**. Recommended workflow:

1. `/backup export <index>` — export the chosen version as a ZIP
2. **Stop the server**
3. Extract the ZIP and **replace** the world directory with backup contents
4. Start the server

Exports are saved as `backups/InstantBackup_<version_name>.zip` by default.

### Scheduled backup

Default interval is **3600 seconds** (1 hour). Common settings:

```
/backup config interval 1800      # every 30 minutes
/backup config enabled true       # enable scheduled backup
/backup config online_only true   # backup only when players are online
/backup config exclude_bots true  # exclude Carpet fake players
```

---

## Commands

All commands require **OP level 2**.

| Command | Description |
|---------|-------------|
| `/backup help` | Show help |
| `/backup create [note]` | Trigger manual backup (auto-migrates PENDING blobs before create) |
| `/backup list` | List backup versions |
| `/backup delete <index>` | Delete a version (includes blob GC) |
| `/backup export <index>` | Export a version as ZIP |
| `/backup migrate <index>` | Seal PENDING blobs for the version and all older ones |
| `/backup status` | Show backup/compression task progress |
| `/backup config [key] [value]` | View or change config |
| `/backup clean` | Remove all backup data |

List example:

```
=== Backup Versions ===
#   Version Name          Status     Type     Note
--  --------------------  ---------  -------  --------
1   20260628_143025       Completed  Manual   main base done
2   20260628_120000       In Progress Auto    -
```

---

## Configuration

Config file: `config/instantbackup/instantbackup.properties`

| Key | Default | Via command | Description |
|-----|---------|:-----------:|-------------|
| `general.language` | `zh_cn` | ✅ | Message language (`zh_cn` / `en_us`) |
| `backup.enabled` | `true` | ✅ | Enable scheduled backup |
| `backup.interval` | `3600` | ✅ | Scheduled backup interval (seconds) |
| `backup.only_when_players_online` | `true` | ✅ | Skip scheduled backup when no players online |
| `backup.exclude_carpet_bots` | `true` | ✅ | Exclude Carpet Mod fake players |
| `compression.level` | `19` | ✅ | ZSTD level (1–22) |
| `thread.count` | `2` | ✅ | Backup thread pool size |
| `storage.max_versions` | `30` | ✅ | Max retained versions (0 = unlimited) |
| `storage.path` | `backups` | ✅ | Backup storage path (**restart required**) |
| `backup.deferred_chunk_migration` | `true` | ❌ | Deferred chunk COW |
| `chunk.full_hash` | `false` | ❌ | Full hash for chunk files |
| `script.enabled` | `true` | ❌ | Generate offline launcher scripts |

Cross-drive example: set `storage.path=D:/minecraft-backups` and restart — blobs go to D: drive; the database stays at `config/instantbackup/backups.db`.

---

## Offline CLI & Launcher Scripts

When `script.enabled=true`, the server generates on startup:

- **Windows:** `InstantBackup.cmd`
- **Linux / macOS:** `InstantBackup.sh`

After shutdown, run the script (or the CLI JAR) for an interactive REPL with `create`, `list`, `export`, etc. — same `backups.db` and blob storage as in-game.

Build standalone CLI:

```bash
./gradlew :cli:shadowJar
# Output: cli/build/libs/instantbackup-cli-<version>-all.jar
```

One-shot mode (cron / CI):

```bash
java -jar instantbackup-cli-1.0.0-all.jar --server-dir /path/to/server create "cron-backup"
```

---

## Directory Layout

```
<server>/
├── world/                              # World save
├── backups/                            # Backup root (default storage.path)
│   ├── data/                           # Blob files (dedup + ZSTD)
│   └── InstantBackup_<version>.zip     # Exported ZIPs
└── config/instantbackup/
    ├── instantbackup.properties        # Config
    ├── backups.db                      # SQLite metadata
    ├── InstantBackup.cmd               # Windows offline script
    └── InstantBackup.sh                # Unix offline script
```

**Blob naming:** `data/<relative_path>.<hash>.zst`

---

## How It Works

```
Trigger (/backup create | scheduler | CLI)
  → Flush world (saveEverything)
  → Migrate all PENDING blobs
  → Scan world directory → XXHash64 incremental detection
  → New blobs: capture → STAGED → ZSTD compress → STORED
  → Chunk COW (Mixin) fills in PENDING blobs
  → Write SQLite → mark version COMPLETED
```

**Blob lifecycle:**

| State | Meaning |
|-------|---------|
| `PENDING` | Metadata recorded; physical content still in world or COW buffer |
| `STAGED` | Copied to raw intermediate file, awaiting compression |
| `STORED` | Compressed as `.zst` |

**Scheduled backup:** `ServerTickMixin` drives `BackupScheduler` each server tick. Carpet bots are detected by class name `carpet.patches.EntityPlayerMPFake` without a compile-time Carpet dependency.

---

## Building from Source

**Requirements:** JDK 17+ (some anchors downgrade bytecode to Java 8 / 17). Gradle Wrapper included.

```bash
# Build default version (mc_ver=1.20.1 in gradle.properties)
./gradlew build

# Build a specific MC version
./gradlew build -Pmc_ver=1.21.1

# Fabric only
./gradlew :fabric:build -Pmc_ver=1.20.1

# Run test server (SelfTest auto-enabled)
./gradlew :fabric:runServer -Pmc_ver=1.20.1
```

Dev self-test: search logs for `[SelfTest] PASS` (`runServer` injects `-Dinstantbackup.selftest=true` by default).

### Project layout

```
InstantBackup/
├── core/               # Platform-agnostic engine (BackupEngine, SQLite, ZSTD)
├── common/             # Minecraft integration (commands, Mixins, scheduler)
├── fabric/             # Fabric entrypoint
├── forge/              # Forge entrypoint
├── neoforge/           # NeoForge entrypoint
├── cli/                # Offline CLI
├── buildSrc/           # Gradle plugins (Unimined, Manifold, Forgix)
└── versionProperties/  # Per-version MC anchor configs
```

---

## Documentation

| Document | Description |
|----------|-------------|
| [docs/requirements.md](docs/requirements.md) | Requirements & implementation status (Chinese) |
| [docs/functional-testing.md](docs/functional-testing.md) | Functional test cases (Chinese) |

---

## License

This project is licensed under the [MIT License](LICENSE).

---

## Author

**limuqy** — [GitHub](https://github.com/limuqy)

Questions and suggestions welcome in [Issues](https://github.com/limuqy/InstantBackup/issues).
