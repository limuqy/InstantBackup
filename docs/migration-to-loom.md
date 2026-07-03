# 迁移到 Architectury Loom

## 背景

Unimined 在 dev 环境中无法正确处理 Fabric API 的 intermediary mixin refmap（`WorldRenderer` vs `LevelRenderer` 命名空间冲突），且文档不足、社区小。迁移到 Architectury Loom 获得更好的映射处理和长期维护性。

**策略**：先迁移 1.20.1（三 loader：Fabric / Forge / NeoForge），验证通过后再扩展到其他版本。保留 Manifold 预处理器。

## 需修改的文件

### 移除

- `buildSrc/src/main/groovy/unimined-common.gradle`
- `buildSrc/src/main/groovy/unimined-fabric.gradle`
- `buildSrc/src/main/groovy/unimined-forge.gradle`
- `buildSrc/src/main/groovy/unimined-neoforge.gradle`

### 新建

- `buildSrc/src/main/groovy/loom-common.gradle` — common 模块 Loom 配置
- `buildSrc/src/main/groovy/loom-fabric.gradle` — Fabric loader + runClient/runServer
- `buildSrc/src/main/groovy/loom-forge.gradle` — Forge loader 配置
- `buildSrc/src/main/groovy/loom-neoforge.gradle` — NeoForge loader 配置

### 重写

- `build.gradle`（根）— 插件声明：移除 unimined，添加 architectury-loom
- `buildSrc/src/main/groovy/minecraft.gradle` — 改用 Loom API 配置 mappings
- `buildSrc/src/main/groovy/root.gradle` — 调整 remapJar 接线
- `settings.gradle` — pluginManagement 仓库调整

### 小改

- `common/build.gradle` — 插件 id 从 `unimined-common` 改为 `loom-common`
- `fabric/build.gradle` — 插件 id 从 `unimined-fabric` 改为 `loom-fabric`
- `forge/build.gradle` — 插件 id 从 `unimined-forge` 改为 `loom-forge`
- `neoforge/build.gradle` — 插件 id 从 `unimined-neoforge` 改为 `loom-neoforge`
- `buildSrc/build.gradle` — 添加 architectury-loom 插件依赖

### 不变

- `core/build.gradle` — 纯 Java 库，不涉及 MC
- `cli/build.gradle` — 纯 Java，不涉及 MC
- `common.gradle` — Java + Manifold + JVMDowngrader + 资源扩展逻辑保留
- `library-java.gradle` — 保留
- `versionProperties/` — 保留
- 所有 Java 源码 — 不改
- `instantbackup.mixins.json` — 不改

## 实施步骤

### Step 1：更新 buildSrc 插件依赖

`buildSrc/build.gradle` 添加 architectury/fabric/forge maven 仓库和 `dev.architectury:architectury-loom` 依赖。

### Step 2：重写 minecraft.gradle

改用 `dev.architectury.loom` 插件，`loom.officialMojangMappings()` 配置映射；loader 模块通过 `sourceSets.main` 合并 `common` 源码，`jar` task 合并 `core`/`cli` 输出。

### Step 3-6：创建 loom-common/fabric/forge/neoforge.gradle

分别配置各 loader 的依赖坐标（`modImplementation`/`forge`/`neoForge`）与 `loom { }` block（如 Forge 的 `mixinConfig`）。

### Step 7：更新根 build.gradle

移除 `xyz.wagyourtail.unimined` 插件声明，添加 `dev.architectury.loom` version `1.10-SNAPSHOT` apply false。forgix 配置保持不变。

### Step 8：调整 root.gradle 中 remapJar 接线

按 Loom 产出的 task 名调整 `remapJar` 相关 finalizedBy 链。

### Step 9：更新各模块 build.gradle 插件 id

`unimined-*` → `loom-*`。

### Step 10：更新 settings.gradle

确保 pluginManagement 仓库包含 `https://maven.architectury.dev/`（已存在）。迁移初期暂保留 wagyourtail 仓库供 Manifold 使用，后续如确认不再需要可再清理。

## 验证

```bash
# 编译验证
./gradlew :fabric:build -Pmc_ver=1.20.1
./gradlew :forge:build -Pmc_ver=1.20.1
./gradlew :neoforge:build -Pmc_ver=1.20.1

# dev 运行验证（核心目标 — 修复 mixin 命名空间问题）
./gradlew :fabric:runClient -Pmc_ver=1.20.1
# 确认 latest.log 中无 WorldRenderer/InvalidInjectionException

# SelfTest 验证
./gradlew :fabric:runServer -Pmc_ver=1.20.1
# 确认 [SelfTest] PASS

# Forge/NeoForge 验证
./gradlew :forge:runClient -Pmc_ver=1.20.1
./gradlew :neoforge:runClient -Pmc_ver=1.20.1
```

## 后续扩展（本次不实施）

1. 验证 1.20.1 三 loader 全部通过后，逐版本验证其他 MC 版本
2. 旧版分支中的 1.16.5 Forge 可能需要特殊处理（MCP mappings vs mojmap），届时再评估
3. 清理不再需要的 wagyourtail maven 仓库
