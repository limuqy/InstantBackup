package io.github.limuqy.mc.backup;

import io.github.limuqy.mc.backup.backup.BackupEngine;
import io.github.limuqy.mc.backup.backup.BackupManager;
import io.github.limuqy.mc.backup.compat.ServerCompat;
import io.github.limuqy.mc.backup.config.BackupConfig;
import io.github.limuqy.mc.backup.config.BackupPaths;
import io.github.limuqy.mc.backup.database.DatabaseManager;
import io.github.limuqy.mc.backup.i18n.ModI18n;
import io.github.limuqy.mc.backup.scheduler.BackupScheduler;
import io.github.limuqy.mc.backup.script.ScriptGenerator;
import io.github.limuqy.mc.backup.thread.BackupThreadPool;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * 极速备份（Instant Backup）模组核心类
 * 参考 fastback 项目的 Mod/ModImpl 模式，使用单例持有 LoaderHelper 和服务器实例
 */
public class ExampleMod {
    public static final String MOD_ID = "instantbackup";

    private static ExampleMod instance;
    private final LoaderHelper loaderHelper;

    private MinecraftServer server;
    private Path configDir;
    private Path worldPath;
    private Path backupPath;

    private ExampleMod(LoaderHelper loaderHelper) {
        this.loaderHelper = loaderHelper;
    }

    /**
     * 早期初始化：在模组加载时调用
     * 此时服务器尚未启动，主要用于注册命令等不需要服务器实例的操作
     *
     * @param loaderHelper 平台辅助器
     */
    public static void initializeForServer(LoaderHelper loaderHelper) {
        if (instance != null) {
            throw new IllegalStateException("ExampleMod already initialized");
        }
        instance = new ExampleMod(loaderHelper);
        System.out.println("[Instant Backup] 模组已加载，等待服务器启动...");
    }

    /**
     * 获取模组单例实例
     */
    public static ExampleMod mod() {
        if (instance == null) {
            throw new IllegalStateException("ExampleMod not yet initialized");
        }
        return instance;
    }

    /**
     * 服务器启动完成后的初始化
     * 此时世界路径和服务器目录已就绪
     *
     * @param server MinecraftServer 实例
     */
    public void onServerStarting(MinecraftServer server) {
        this.server = server;
        this.worldPath = server.getWorldPath(LevelResource.ROOT);
        this.configDir = ServerCompat.getServerDirectory(server).resolve("config").resolve(MOD_ID);

        // 初始化配置
        BackupConfig.init(configDir);
        ModI18n.reload();

        // 备份目录由 storage.path 配置决定（支持绝对路径跨盘符）
        Path serverRoot = ServerCompat.getServerDirectory(server).toAbsolutePath().normalize();
        this.backupPath = BackupPaths.resolveBackupPath(serverRoot, BackupConfig.getStoragePath());

        // 数据库文件和配置文件同级: config/backups.db
        DatabaseManager dbManager = DatabaseManager.getInstance();
        if (!dbManager.init(configDir)) {
            System.err.println("[Instant Backup] 数据库初始化失败，备份功能将不可用");
            return;
        }

        // 初始化备份管理器
        BackupManager backupManager = BackupManager.getInstance();
        backupManager.init(worldPath, backupPath, server);

        // 清理可能存在的旧数据库文件（目录结构变更时的脏数据）
        backupManager.cleanOldDatabaseFiles();

        // 初始化定时调度器
        BackupScheduler.getInstance();

        // 初始化线程池
        BackupThreadPool.getInstance();

        System.out.println("[Instant Backup] 服务器已启动，模组已初始化");
        System.out.println("[Instant Backup] 配置目录: " + configDir);
        System.out.println("[Instant Backup] 世界路径: " + worldPath);
        System.out.println("[Instant Backup] 备份路径: " + backupPath);

        ScriptGenerator.generate(configDir, loaderHelper.getModRootPath(), serverRoot);
    }

    /**
     * 服务器关闭时清理资源
     */
    public void onServerStopping() {
        BackupScheduler.getInstance().reset();
        BackupEngine.getInstance().shutdown();
        System.out.println("[Instant Backup] 服务器已停止");
    }

    /**
     * 获取 LoaderHelper
     */
    public LoaderHelper getLoaderHelper() {
        return loaderHelper;
    }

    /**
     * 获取服务器实例
     */
    public MinecraftServer getServer() {
        return server;
    }

    public Path getConfigDir() {
        return configDir;
    }

    public Path getWorldPath() {
        return worldPath;
    }

    public Path getBackupPath() {
        return backupPath;
    }
}
