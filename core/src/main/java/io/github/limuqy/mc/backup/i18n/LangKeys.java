package io.github.limuqy.mc.backup.i18n;

/**
 * 翻译键常量，前缀统一为 instantbackup.
 */
public final class LangKeys {
    private LangKeys() {
    }

    // 通用
    public static final String COMMON_YES = "instantbackup.common.yes";
    public static final String COMMON_NO = "instantbackup.common.no";
    public static final String COMMON_ENABLED = "instantbackup.common.enabled";
    public static final String COMMON_DISABLED = "instantbackup.common.disabled";

    // 命令异常
    public static final String COMMAND_EXEC_FAILED = "instantbackup.command.exec_failed";

    // 命令帮助
    public static final String COMMAND_HELP_TITLE = "instantbackup.command.help.title";
    public static final String COMMAND_HELP_LINE_HELP = "instantbackup.command.help.line.help";
    public static final String COMMAND_HELP_LINE_CREATE = "instantbackup.command.help.line.create";
    public static final String COMMAND_HELP_LINE_LIST = "instantbackup.command.help.line.list";
    public static final String COMMAND_HELP_LINE_DELETE = "instantbackup.command.help.line.delete";
    public static final String COMMAND_HELP_LINE_EXPORT = "instantbackup.command.help.line.export";
    public static final String COMMAND_HELP_LINE_MIGRATE = "instantbackup.command.help.line.migrate";
    public static final String COMMAND_HELP_LINE_STATUS = "instantbackup.command.help.line.status";
    public static final String COMMAND_HELP_LINE_CONFIG = "instantbackup.command.help.line.config";
    public static final String COMMAND_HELP_LINE_CLEAN = "instantbackup.command.help.line.clean";

    // 命令错误
    public static final String COMMAND_ERROR_UNKNOWN = "instantbackup.command.error.unknown";
    public static final String COMMAND_ERROR_MISSING_INDEX = "instantbackup.command.error.missing_index";
    public static final String COMMAND_ERROR_INVALID_INDEX = "instantbackup.command.error.invalid_index";

    // 版本列表
    public static final String COMMAND_LIST_EMPTY = "instantbackup.command.list.empty";
    public static final String COMMAND_LIST_TITLE = "instantbackup.command.list.title";
    public static final String COMMAND_LIST_HEADER = "instantbackup.command.list.header";
    public static final String COMMAND_LIST_SEPARATOR = "instantbackup.command.list.separator";
    public static final String COMMAND_LIST_ROW = "instantbackup.command.list.row";

    // 任务状态
    public static final String COMMAND_STATUS_TITLE = "instantbackup.command.status.title";
    public static final String COMMAND_STATUS_SCANNING = "instantbackup.command.status.scanning";
    public static final String COMMAND_STATUS_IDLE = "instantbackup.command.status.idle";
    public static final String COMMAND_STATUS_PENDING_BLOB = "instantbackup.command.status.pending_blob";
    public static final String COMMAND_STATUS_COMPRESSION_QUEUE = "instantbackup.command.status.compression_queue";
    public static final String COMMAND_STATUS_IN_PROGRESS_VERSIONS = "instantbackup.command.status.in_progress_versions";
    public static final String COMMAND_STATUS_THREAD_POOL = "instantbackup.command.status.thread_pool";

    // 配置查看
    public static final String COMMAND_CONFIG_TITLE = "instantbackup.command.config.title";
    public static final String COMMAND_CONFIG_INTERVAL = "instantbackup.command.config.interval";
    public static final String COMMAND_CONFIG_ENABLED = "instantbackup.command.config.enabled";
    public static final String COMMAND_CONFIG_ONLINE_ONLY = "instantbackup.command.config.online_only";
    public static final String COMMAND_CONFIG_EXCLUDE_BOTS = "instantbackup.command.config.exclude_bots";
    public static final String COMMAND_CONFIG_DEFERRED_CHUNK = "instantbackup.command.config.deferred_chunk";
    public static final String COMMAND_CONFIG_CHUNK_FULL_HASH = "instantbackup.command.config.chunk_full_hash";
    public static final String COMMAND_CONFIG_COMPRESSION_LEVEL = "instantbackup.command.config.compression_level";
    public static final String COMMAND_CONFIG_COMPRESSION_THREADS = "instantbackup.command.config.compression_threads";
    public static final String COMMAND_CONFIG_THREADS = "instantbackup.command.config.threads";
    public static final String COMMAND_CONFIG_MAX_VERSIONS = "instantbackup.command.config.max_versions";
    public static final String COMMAND_CONFIG_STORAGE_PATH = "instantbackup.command.config.storage_path";
    public static final String COMMAND_CONFIG_LANGUAGE = "instantbackup.command.config.language";
    public static final String COMMAND_CONFIG_USAGE = "instantbackup.command.config.usage";

    // 配置写入反馈
    public static final String COMMAND_CONFIG_SET_INTERVAL = "instantbackup.command.config.set.interval";
    public static final String COMMAND_CONFIG_SET_ENABLED = "instantbackup.command.config.set.enabled";
    public static final String COMMAND_CONFIG_SET_ONLINE_ONLY = "instantbackup.command.config.set.online_only";
    public static final String COMMAND_CONFIG_SET_EXCLUDE_BOTS = "instantbackup.command.config.set.exclude_bots";
    public static final String COMMAND_CONFIG_SET_COMPRESSION_INVALID = "instantbackup.command.config.set.compression_invalid";
    public static final String COMMAND_CONFIG_SET_COMPRESSION = "instantbackup.command.config.set.compression";
    public static final String COMMAND_CONFIG_SET_THREADS = "instantbackup.command.config.set.threads";
    public static final String COMMAND_CONFIG_SET_MAX_VERSIONS = "instantbackup.command.config.set.max_versions";
    public static final String COMMAND_CONFIG_SET_PATH = "instantbackup.command.config.set.path";
    public static final String COMMAND_CONFIG_SET_PATH_RESTART = "instantbackup.command.config.set.path_restart";
    public static final String COMMAND_CONFIG_SET_PATH_INVALID = "instantbackup.command.config.set.path_invalid";
    public static final String COMMAND_CONFIG_SET_LANGUAGE = "instantbackup.command.config.set.language";
    public static final String COMMAND_CONFIG_SET_LANGUAGE_INVALID = "instantbackup.command.config.set.language_invalid";
    public static final String COMMAND_CONFIG_UNKNOWN_KEY = "instantbackup.command.config.unknown_key";
    public static final String COMMAND_CONFIG_INVALID_NUMBER = "instantbackup.command.config.invalid_number";

    // 版本标签
    public static final String VERSION_TYPE_MANUAL = "instantbackup.version.type.manual";
    public static final String VERSION_TYPE_AUTO = "instantbackup.version.type.auto";
    public static final String VERSION_STATUS_COMPLETED = "instantbackup.version.status.completed";
    public static final String VERSION_STATUS_IN_PROGRESS = "instantbackup.version.status.in_progress";

    // 备份操作
    public static final String BACKUP_IN_PROGRESS = "instantbackup.backup.in_progress";
    public static final String BACKUP_STARTED = "instantbackup.backup.started";
    public static final String BACKUP_VERSION_NOT_EXISTS = "instantbackup.backup.version_not_exists";
    public static final String BACKUP_VERSION_NOT_FOUND = "instantbackup.backup.version_not_found";
    public static final String BACKUP_MIGRATE_DONE = "instantbackup.backup.migrate.done";
    public static final String BACKUP_MIGRATE_DONE_WITH_SKIPPED = "instantbackup.backup.migrate.done_with_skipped";
    public static final String BACKUP_MIGRATE_FAILED = "instantbackup.backup.migrate.failed";
    public static final String BACKUP_DELETE_DONE = "instantbackup.backup.delete.done";
    public static final String BACKUP_DELETE_FAILED = "instantbackup.backup.delete.failed";
    public static final String BACKUP_CLEAN_DONE = "instantbackup.backup.clean.done";
    public static final String BACKUP_CLEAN_FAILED = "instantbackup.backup.clean.failed";
    public static final String BACKUP_EXPORT_STARTED = "instantbackup.backup.export.started";
    public static final String BACKUP_EXPORT_FAILED = "instantbackup.backup.export.failed";
}
