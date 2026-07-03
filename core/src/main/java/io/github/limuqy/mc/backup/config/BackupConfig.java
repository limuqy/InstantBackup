package io.github.limuqy.mc.backup.config;

import io.github.limuqy.mc.backup.database.MetadataBackend;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public class BackupConfig {
    private static final String CONFIG_FILE = "instantbackup.properties";
    private static Properties properties = new Properties();
    private static Path configPath;

    // 默认值
    private static final int DEFAULT_BACKUP_INTERVAL = 3600;
    private static final boolean DEFAULT_ENABLED = true;
    private static final boolean DEFAULT_ONLY_WHEN_PLAYERS_ONLINE = true;
    private static final boolean DEFAULT_EXCLUDE_CARPET_BOTS = true;
    private static final boolean DEFAULT_COMPRESSION_ENABLED = true;
    private static final int DEFAULT_COMPRESSION_LEVEL = 19;
    private static final int DEFAULT_COMPRESSION_THREADS = 2;
    private static final boolean DEFAULT_DEFERRED_CHUNK_MIGRATION = true;
    private static final boolean DEFAULT_CHUNK_FULL_HASH = false;
    private static final int DEFAULT_THREAD_COUNT = 2;
    private static final int DEFAULT_MAX_VERSIONS = 30;
    private static final String DEFAULT_STORAGE_PATH = "backups";
    private static final String DEFAULT_LANGUAGE = "zh_cn";
    private static final boolean DEFAULT_SCRIPT_ENABLED = true;
    private static final String DEFAULT_RESTORE_SOURCE_DIR = "backups";

    // 元数据存储后端默认值
    private static final String DEFAULT_METADATA_TYPE = "csv";
    private static final String DEFAULT_MYSQL_HOST = "127.0.0.1";
    private static final int DEFAULT_MYSQL_PORT = 3306;
    private static final String DEFAULT_MYSQL_DATABASE = "instantbackup";
    private static final String DEFAULT_MYSQL_USERNAME = "backup";
    private static final String DEFAULT_MYSQL_PASSWORD = "";
    private static final String DEFAULT_MYSQL_TABLE_PREFIX = "ib_";
    private static final boolean DEFAULT_MYSQL_USE_SSL = false;
    private static final int DEFAULT_MYSQL_CONNECTION_TIMEOUT = 30;

    public static void init(Path configDir) {
        configPath = configDir.resolve(CONFIG_FILE);
        if (!Files.exists(configPath)) {
            generateDefaultConfig();
        }
        load();
    }

    /**
     * 生成带中文注释的默认配置文件
     */
    private static void generateDefaultConfig() {
        try {
            Files.createDirectories(configPath.getParent());
            String content = "# =============================================\n" +
                "# 极速备份 模组配置文件\n" +
                "# =============================================\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# 通用设置\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# 模组消息语言\n" +
                "# 可选值: zh_cn（中文）/ en_us（英文）\n" +
                "general.language=zh_cn\n" +
                "\n" +
                "# 是否在 config/instantbackup/ 生成一键备份脚本（InstantBackup.cmd / .sh）\n" +
                "script.enabled=true\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# 备份设置\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# 是否启用定时备份\n" +
                "# 可选值: true / false\n" +
                "backup.enabled=true\n" +
                "\n" +
                "# 定时备份间隔（单位：秒）\n" +
                "# 默认: 3600（1小时）\n" +
                "# 最小建议值: 60（1分钟）\n" +
                "backup.interval=3600\n" +
                "\n" +
                "# 是否只在玩家在线时进行定时备份\n" +
                "# 设为 true 可以在服务器空闲时跳过备份，节省资源\n" +
                "# 可选值: true / false\n" +
                "backup.only_when_players_online=true\n" +
                "\n" +
                "# 是否排除地毯假人（Carpet Mod 的假人玩家）\n" +
                "# 设为 true 时，只有真实玩家在线才算有玩家在线\n" +
                "# 可选值: true / false\n" +
                "backup.exclude_carpet_bots=true\n" +
                "\n" +
                "# 区块文件是否延迟迁移（COW）\n" +
                "# true = 区块在即将被覆盖时才复制到备份区；false = 备份时立即复制\n" +
                "backup.deferred_chunk_migration=true\n" +
                "\n" +
                "# 区块文件是否使用全量 hash（false 则仅用前 8KB + size）\n" +
                "chunk.full_hash=false\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# 压缩设置\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# 是否启用 ZSTD 压缩\n" +
                "# false = 保留 raw 文件不压缩，占用更多磁盘但节省 CPU\n" +
                "# 可选值: true / false\n" +
                "compression.enabled=true\n" +
                "\n" +
                "# ZSTD 压缩等级（范围：1-22）\n" +
                "# 异步压缩模式下推荐较高等级以换取更小体积\n" +
                "compression.level=19\n" +
                "\n" +
                "# 压缩消费者线程数\n" +
                "compression.threads=2\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# 性能设置\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# 备份线程池核心线程数\n" +
                "# 增加此值可以加快备份速度，但会占用更多 CPU 资源\n" +
                "# 建议值: CPU 核心数的一半，最小为 1\n" +
                "thread.count=2\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# 存储设置\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# 最大保留备份版本数\n" +
                "# 超过此数量的旧版本会被自动删除\n" +
                "# 设为 0 表示不限制\n" +
                "storage.max_versions=30\n" +
                "\n" +
                "# 备份存储路径\n" +
                "# 相对路径：相对于服务器根目录（默认 backups）\n" +
                "# 绝对路径：支持跨盘符，如 D:/minecraft-backups 或 \\\\nas\\share\\backups\n" +
                "storage.path=backups\n" +
                "\n" +
                "# 元数据存储后端\n" +
                "# 可选值: csv（默认，纯文本易备份）/ sqlite（嵌入式数据库）/ mysql（远程集中存储）\n" +
                "# 注意：切换后端不会自动迁移旧数据\n" +
                "storage.metadata.type=csv\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# MySQL 连接配置（仅当 storage.metadata.type=mysql 时生效）\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# MySQL 主机地址\n" +
                "storage.mysql.host=127.0.0.1\n" +
                "\n" +
                "# MySQL 端口\n" +
                "storage.mysql.port=3306\n" +
                "\n" +
                "# MySQL 数据库名（需预先创建）\n" +
                "storage.mysql.database=instantbackup\n" +
                "\n" +
                "# MySQL 用户名\n" +
                "storage.mysql.username=backup\n" +
                "\n" +
                "# MySQL 密码（建议非空；CLI 可用环境变量 INSTANTBACKUP_MYSQL_PASSWORD 覆盖）\n" +
                "storage.mysql.password=\n" +
                "\n" +
                "# MySQL 表名前缀（便于同库多实例）\n" +
                "storage.mysql.table_prefix=ib_\n" +
                "\n" +
                "# 是否启用 SSL 连接 MySQL\n" +
                "storage.mysql.use_ssl=false\n" +
                "\n" +
                "# MySQL 连接超时（单位：秒）\n" +
                "storage.mysql.connection_timeout=30\n";

            Files.write(configPath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void load() {
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                properties.load(is);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // 设置默认值（防止配置文件缺少某些项）
        setDefault("backup.interval", String.valueOf(DEFAULT_BACKUP_INTERVAL));
        setDefault("backup.enabled", String.valueOf(DEFAULT_ENABLED));
        setDefault("backup.only_when_players_online", String.valueOf(DEFAULT_ONLY_WHEN_PLAYERS_ONLINE));
        setDefault("backup.exclude_carpet_bots", String.valueOf(DEFAULT_EXCLUDE_CARPET_BOTS));
        setDefault("backup.deferred_chunk_migration", String.valueOf(DEFAULT_DEFERRED_CHUNK_MIGRATION));
        setDefault("chunk.full_hash", String.valueOf(DEFAULT_CHUNK_FULL_HASH));
        setDefault("compression.enabled", String.valueOf(DEFAULT_COMPRESSION_ENABLED));
        setDefault("compression.level", String.valueOf(DEFAULT_COMPRESSION_LEVEL));
        setDefault("compression.threads", String.valueOf(DEFAULT_COMPRESSION_THREADS));
        setDefault("thread.count", String.valueOf(DEFAULT_THREAD_COUNT));
        setDefault("storage.max_versions", String.valueOf(DEFAULT_MAX_VERSIONS));
        setDefault("storage.path", DEFAULT_STORAGE_PATH);
        setDefault("general.language", DEFAULT_LANGUAGE);
        setDefault("script.enabled", String.valueOf(DEFAULT_SCRIPT_ENABLED));
        setDefault("script.restore_source_dir", DEFAULT_RESTORE_SOURCE_DIR);
        setDefault("storage.metadata.type", DEFAULT_METADATA_TYPE);
        setDefault("storage.mysql.host", DEFAULT_MYSQL_HOST);
        setDefault("storage.mysql.port", String.valueOf(DEFAULT_MYSQL_PORT));
        setDefault("storage.mysql.database", DEFAULT_MYSQL_DATABASE);
        setDefault("storage.mysql.username", DEFAULT_MYSQL_USERNAME);
        setDefault("storage.mysql.password", DEFAULT_MYSQL_PASSWORD);
        setDefault("storage.mysql.table_prefix", DEFAULT_MYSQL_TABLE_PREFIX);
        setDefault("storage.mysql.use_ssl", String.valueOf(DEFAULT_MYSQL_USE_SSL));
        setDefault("storage.mysql.connection_timeout", String.valueOf(DEFAULT_MYSQL_CONNECTION_TIMEOUT));
    }

    private static void setDefault(String key, String value) {
        properties.putIfAbsent(key, value);
    }

    /**
     * 保存配置文件（保留中文注释）
     */
    public static void save() {
        try {
            Files.createDirectories(configPath.getParent());
            String content = "# =============================================\n" +
                "# 极速备份 模组配置文件\n" +
                "# =============================================\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# 通用设置\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# 模组消息语言\n" +
                "# 可选值: zh_cn（中文）/ en_us（英文）\n" +
                "general.language=" + properties.getProperty("general.language") + "\n" +
                "\n" +
                "# 是否在 config/instantbackup/ 生成一键备份脚本\n" +
                "script.enabled=" + properties.getProperty("script.enabled") + "\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# 备份设置\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# 是否启用定时备份\n" +
                "# 可选值: true / false\n" +
                "backup.enabled=" + properties.getProperty("backup.enabled") + "\n" +
                "\n" +
                "# 定时备份间隔（单位：秒）\n" +
                "# 默认: 3600（1小时）\n" +
                "# 最小建议值: 60（1分钟）\n" +
                "backup.interval=" + properties.getProperty("backup.interval") + "\n" +
                "\n" +
                "# 是否只在玩家在线时进行定时备份\n" +
                "# 设为 true 可以在服务器空闲时跳过备份，节省资源\n" +
                "# 可选值: true / false\n" +
                "backup.only_when_players_online=" + properties.getProperty("backup.only_when_players_online") + "\n" +
                "\n" +
                "# 是否排除地毯假人（Carpet Mod 的假人玩家）\n" +
                "# 设为 true 时，只有真实玩家在线才算有玩家在线\n" +
                "# 可选值: true / false\n" +
                "backup.exclude_carpet_bots=" + properties.getProperty("backup.exclude_carpet_bots") + "\n" +
                "\n" +
                "# 区块文件是否延迟迁移（COW）\n" +
                "backup.deferred_chunk_migration=" + properties.getProperty("backup.deferred_chunk_migration") + "\n" +
                "\n" +
                "# 区块文件是否使用全量 hash\n" +
                "chunk.full_hash=" + properties.getProperty("chunk.full_hash") + "\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# 压缩设置\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# 是否启用 ZSTD 压缩\n" +
                "compression.enabled=" + properties.getProperty("compression.enabled") + "\n" +
                "\n" +
                "# ZSTD 压缩等级（范围：1-22）\n" +
                "compression.level=" + properties.getProperty("compression.level") + "\n" +
                "\n" +
                "# 压缩消费者线程数\n" +
                "compression.threads=" + properties.getProperty("compression.threads") + "\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# 性能设置\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# 备份线程池核心线程数\n" +
                "# 增加此值可以加快备份速度，但会占用更多 CPU 资源\n" +
                "# 建议值: CPU 核心数的一半，最小为 1\n" +
                "thread.count=" + properties.getProperty("thread.count") + "\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# 存储设置\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# 最大保留备份版本数\n" +
                "# 超过此数量的旧版本会被自动删除\n" +
                "# 设为 0 表示不限制\n" +
                "storage.max_versions=" + properties.getProperty("storage.max_versions") + "\n" +
                "\n" +
                "# 备份存储路径\n" +
                "# 相对路径：相对于服务器根目录；绝对路径支持跨盘符\n" +
                "storage.path=" + properties.getProperty("storage.path") + "\n" +
                "\n" +
                "# 元数据存储后端\n" +
                "# 可选值: csv（默认）/ sqlite / mysql\n" +
                "storage.metadata.type=" + properties.getProperty("storage.metadata.type") + "\n" +
                "\n" +
                "# -------------------------------------------\n" +
                "# MySQL 连接配置（仅当 storage.metadata.type=mysql 时生效）\n" +
                "# -------------------------------------------\n" +
                "\n" +
                "# MySQL 主机地址\n" +
                "storage.mysql.host=" + properties.getProperty("storage.mysql.host") + "\n" +
                "\n" +
                "# MySQL 端口\n" +
                "storage.mysql.port=" + properties.getProperty("storage.mysql.port") + "\n" +
                "\n" +
                "# MySQL 数据库名\n" +
                "storage.mysql.database=" + properties.getProperty("storage.mysql.database") + "\n" +
                "\n" +
                "# MySQL 用户名\n" +
                "storage.mysql.username=" + properties.getProperty("storage.mysql.username") + "\n" +
                "\n" +
                "# MySQL 密码\n" +
                "storage.mysql.password=" + properties.getProperty("storage.mysql.password") + "\n" +
                "\n" +
                "# MySQL 表名前缀\n" +
                "storage.mysql.table_prefix=" + properties.getProperty("storage.mysql.table_prefix") + "\n" +
                "\n" +
                "# 是否启用 SSL 连接 MySQL\n" +
                "storage.mysql.use_ssl=" + properties.getProperty("storage.mysql.use_ssl") + "\n" +
                "\n" +
                "# MySQL 连接超时（单位：秒）\n" +
                "storage.mysql.connection_timeout=" + properties.getProperty("storage.mysql.connection_timeout") + "\n";

            Files.write(configPath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void reload() {
        properties.clear();
        load();
        io.github.limuqy.mc.backup.i18n.I18n.reload();
    }

    // Getter 方法（带 null 安全保护）
    public static int getBackupInterval() {
        String val = properties.getProperty("backup.interval");
        return val != null ? Integer.parseInt(val) : DEFAULT_BACKUP_INTERVAL;
    }

    public static boolean isEnabled() {
        String val = properties.getProperty("backup.enabled");
        return val != null ? Boolean.parseBoolean(val) : DEFAULT_ENABLED;
    }

    public static boolean isOnlyWhenPlayersOnline() {
        String val = properties.getProperty("backup.only_when_players_online");
        return val != null ? Boolean.parseBoolean(val) : DEFAULT_ONLY_WHEN_PLAYERS_ONLINE;
    }

    public static boolean isExcludeCarpetBots() {
        String val = properties.getProperty("backup.exclude_carpet_bots");
        return val != null ? Boolean.parseBoolean(val) : DEFAULT_EXCLUDE_CARPET_BOTS;
    }

    public static boolean isCompressionEnabled() {
        String val = properties.getProperty("compression.enabled");
        return val != null ? Boolean.parseBoolean(val) : DEFAULT_COMPRESSION_ENABLED;
    }

    public static int getCompressionLevel() {
        String val = properties.getProperty("compression.level");
        return val != null ? Integer.parseInt(val) : DEFAULT_COMPRESSION_LEVEL;
    }

    public static boolean isDeferredChunkMigration() {
        String val = properties.getProperty("backup.deferred_chunk_migration");
        return val != null ? Boolean.parseBoolean(val) : DEFAULT_DEFERRED_CHUNK_MIGRATION;
    }

    public static boolean isChunkFullHash() {
        String val = properties.getProperty("chunk.full_hash");
        return val != null ? Boolean.parseBoolean(val) : DEFAULT_CHUNK_FULL_HASH;
    }

    public static int getCompressionThreads() {
        String val = properties.getProperty("compression.threads");
        return val != null ? Integer.parseInt(val) : DEFAULT_COMPRESSION_THREADS;
    }

    public static int getThreadCount() {
        String val = properties.getProperty("thread.count");
        return val != null ? Integer.parseInt(val) : DEFAULT_THREAD_COUNT;
    }

    public static int getMaxVersions() {
        String val = properties.getProperty("storage.max_versions");
        return val != null ? Integer.parseInt(val) : DEFAULT_MAX_VERSIONS;
    }

    public static String getStoragePath() {
        String val = properties.getProperty("storage.path");
        return val != null ? val : DEFAULT_STORAGE_PATH;
    }

    public static String getLanguage() {
        String val = properties.getProperty("general.language");
        return val != null ? val : DEFAULT_LANGUAGE;
    }

    public static boolean isScriptEnabled() {
        String val = properties.getProperty("script.enabled");
        return val != null ? Boolean.parseBoolean(val) : DEFAULT_SCRIPT_ENABLED;
    }

    // 元数据存储后端 Getter
    public static MetadataBackend getMetadataBackend() {
        String val = properties.getProperty("storage.metadata.type");
        return MetadataBackend.fromConfig(val != null ? val : DEFAULT_METADATA_TYPE);
    }

    public static String getMysqlHost() {
        String val = properties.getProperty("storage.mysql.host");
        return val != null ? val : DEFAULT_MYSQL_HOST;
    }

    public static int getMysqlPort() {
        String val = properties.getProperty("storage.mysql.port");
        return val != null ? Integer.parseInt(val) : DEFAULT_MYSQL_PORT;
    }

    public static String getMysqlDatabase() {
        String val = properties.getProperty("storage.mysql.database");
        return val != null ? val : DEFAULT_MYSQL_DATABASE;
    }

    public static String getMysqlUsername() {
        String val = properties.getProperty("storage.mysql.username");
        return val != null ? val : DEFAULT_MYSQL_USERNAME;
    }

    public static String getMysqlPassword() {
        // 环境变量优先
        String envPassword = System.getenv("INSTANTBACKUP_MYSQL_PASSWORD");
        if (envPassword != null && !envPassword.isEmpty()) {
            return envPassword;
        }
        String val = properties.getProperty("storage.mysql.password");
        return val != null ? val : DEFAULT_MYSQL_PASSWORD;
    }

    public static String getMysqlTablePrefix() {
        String val = properties.getProperty("storage.mysql.table_prefix");
        return val != null ? val : DEFAULT_MYSQL_TABLE_PREFIX;
    }

    public static boolean isMysqlUseSsl() {
        String val = properties.getProperty("storage.mysql.use_ssl");
        return val != null ? Boolean.parseBoolean(val) : DEFAULT_MYSQL_USE_SSL;
    }

    public static int getMysqlConnectionTimeout() {
        String val = properties.getProperty("storage.mysql.connection_timeout");
        return val != null ? Integer.parseInt(val) : DEFAULT_MYSQL_CONNECTION_TIMEOUT;
    }

    // Setter 方法
    public static void setBackupInterval(int interval) {
        properties.setProperty("backup.interval", String.valueOf(interval));
        save();
    }

    public static void setEnabled(boolean enabled) {
        properties.setProperty("backup.enabled", String.valueOf(enabled));
        save();
    }

    public static void setOnlyWhenPlayersOnline(boolean only) {
        properties.setProperty("backup.only_when_players_online", String.valueOf(only));
        save();
    }

    public static void setExcludeCarpetBots(boolean exclude) {
        properties.setProperty("backup.exclude_carpet_bots", String.valueOf(exclude));
        save();
    }

    public static void setCompressionLevel(int level) {
        properties.setProperty("compression.level", String.valueOf(level));
        save();
    }

    public static void setThreadCount(int count) {
        properties.setProperty("thread.count", String.valueOf(count));
        save();
    }

    public static void setMaxVersions(int max) {
        properties.setProperty("storage.max_versions", String.valueOf(max));
        save();
    }

    public static boolean setStoragePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        String normalized = path.trim();
        properties.setProperty("storage.path", normalized);
        save();
        try {
            Path probe = Paths.get(normalized);
            Path target = probe.isAbsolute() ? probe : Paths.get(".").resolve(probe);
            Files.createDirectories(target);
        } catch (IOException e) {
            io.github.limuqy.mc.backup.logging.ModLog.warn(
                "[Instant Backup] 无法创建或访问备份目录 {}: {}", normalized, e.getMessage());
        }
        return true;
    }

    public static void setDeferredChunkMigration(boolean enabled) {
        properties.setProperty("backup.deferred_chunk_migration", String.valueOf(enabled));
        save();
    }

    public static void setLanguage(String language) {
        properties.setProperty("general.language", language);
        save();
    }

    public static Properties getProperties() {
        return properties;
    }

    // 元数据存储后端 Setter
    public static void setMetadataBackend(MetadataBackend backend) {
        properties.setProperty("storage.metadata.type", backend.getConfigValue());
        save();
    }

    public static void setMysqlHost(String host) {
        properties.setProperty("storage.mysql.host", host);
        save();
    }

    public static void setMysqlPort(int port) {
        properties.setProperty("storage.mysql.port", String.valueOf(port));
        save();
    }

    public static void setMysqlDatabase(String database) {
        properties.setProperty("storage.mysql.database", database);
        save();
    }

    public static void setMysqlUsername(String username) {
        properties.setProperty("storage.mysql.username", username);
        save();
    }

    public static void setMysqlPassword(String password) {
        properties.setProperty("storage.mysql.password", password);
        save();
    }

    public static void setMysqlTablePrefix(String prefix) {
        properties.setProperty("storage.mysql.table_prefix", prefix);
        save();
    }

    public static void setMysqlUseSsl(boolean useSsl) {
        properties.setProperty("storage.mysql.use_ssl", String.valueOf(useSsl));
        save();
    }

    public static void setMysqlConnectionTimeout(int timeout) {
        properties.setProperty("storage.mysql.connection_timeout", String.valueOf(timeout));
        save();
    }
}
