package io.github.limuqy.mc.backup.command;

import io.github.limuqy.mc.backup.backup.BackupManager;
import io.github.limuqy.mc.backup.compat.ChatCompat;
import io.github.limuqy.mc.backup.config.BackupConfig;
import io.github.limuqy.mc.backup.database.VersionInfo;
import io.github.limuqy.mc.backup.database.VersionStatus;
import io.github.limuqy.mc.backup.i18n.LangKeys;
import io.github.limuqy.mc.backup.i18n.Messages;
import io.github.limuqy.mc.backup.i18n.ModI18n;
import io.github.limuqy.mc.backup.thread.BackupThreadPool;
import net.minecraft.network.chat.Component;

import java.util.List;

public class BackupCommandHandler {
    private static final BackupManager backupManager = BackupManager.getInstance();
    private static final BackupThreadPool threadPool = BackupThreadPool.getInstance();

    public static Component execute(String[] args) {
        if (args.length == 0) {
            return Messages.help();
        }

        String subCommand = args[0].toLowerCase();
        return switch (subCommand) {
            case "help" -> Messages.help();
            case "create" -> createBackup(args);
            case "list" -> listVersions();
            case "delete" -> deleteVersion(args);
            case "export" -> exportVersion(args);
            case "migrate" -> migrateVersions(args);
            case "status" -> showStatus();
            case "config" -> showConfig(args);
            case "clean" -> cleanBackups();
            default -> Messages.unknownCommand(subCommand);
        };
    }

    private static Component createBackup(String[] args) {
        String description = null;
        if (args.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(args[i]);
            }
            description = sb.toString();
        }
        return backupManager.createBackup(description, true);
    }

    private static Component listVersions() {
        return Messages.versionList(backupManager.getVersionList());
    }

    private static VersionInfo getVersionByIndex(String[] args) {
        if (args.length < 2) {
            return null;
        }

        List<VersionInfo> versions = backupManager.getVersionList();
        if (versions.isEmpty()) {
            return null;
        }

        try {
            int index = Integer.parseInt(args[1]);
            if (index < 1 || index > versions.size()) {
                return null;
            }
            return versions.get(index - 1);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Component deleteVersion(String[] args) {
        VersionInfo version = getVersionByIndex(args);
        if (version == null) {
            if (args.length < 2) {
                return Messages.missingIndex("delete");
            }
            return Messages.invalidIndex(args[1]);
        }
        return backupManager.deleteVersion(version.getVersionName());
    }

    private static Component exportVersion(String[] args) {
        VersionInfo version = getVersionByIndex(args);
        if (version == null) {
            if (args.length < 2) {
                return Messages.missingIndex("export");
            }
            return Messages.invalidIndex(args[1]);
        }
        return backupManager.exportVersion(version.getVersionName());
    }

    private static Component migrateVersions(String[] args) {
        VersionInfo version = getVersionByIndex(args);
        if (version == null) {
            if (args.length < 2) {
                return Messages.missingIndex("migrate");
            }
            return Messages.invalidIndex(args[1]);
        }
        return backupManager.migrateVersions(version.getId());
    }

    private static Component showStatus() {
        int inProgressVersions = 0;
        for (VersionInfo v : backupManager.getVersionList()) {
            if (v.getStatus() != VersionStatus.COMPLETED) {
                inProgressVersions++;
            }
        }

        return Messages.status(
            backupManager.isBackupInProgress(),
            backupManager.getProgress(),
            backupManager.getTotalFiles(),
            backupManager.getPendingBlobCount(),
            backupManager.getCompressionQueueSize(),
            inProgressVersions,
            threadPool.getCompletedTasks(),
            threadPool.getTotalTasks(),
            threadPool.getActiveThreads(),
            threadPool.getQueueSize()
        );
    }

    private static Component showConfig(String[] args) {
        if (args.length == 1) {
            return Messages.configDisplay();
        }

        if (args.length < 3) {
            return ChatCompat.translatable(LangKeys.COMMAND_CONFIG_USAGE);
        }

        String key = args[1].toLowerCase();
        String value = args[2];

        try {
            return switch (key) {
                case "interval" -> {
                    BackupConfig.setBackupInterval(Integer.parseInt(value));
                    yield ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_INTERVAL, value);
                }
                case "enabled" -> {
                    boolean enabled = Boolean.parseBoolean(value);
                    BackupConfig.setEnabled(enabled);
                    yield ChatCompat.translatable(
                        LangKeys.COMMAND_CONFIG_SET_ENABLED,
                        Messages.enabledDisabled(enabled)
                    );
                }
                case "online_only" -> {
                    boolean enabled = Boolean.parseBoolean(value);
                    BackupConfig.setOnlyWhenPlayersOnline(enabled);
                    yield ChatCompat.translatable(
                        LangKeys.COMMAND_CONFIG_SET_ONLINE_ONLY,
                        Messages.enabledDisabled(enabled)
                    );
                }
                case "exclude_bots" -> {
                    boolean enabled = Boolean.parseBoolean(value);
                    BackupConfig.setExcludeCarpetBots(enabled);
                    yield ChatCompat.translatable(
                        LangKeys.COMMAND_CONFIG_SET_EXCLUDE_BOTS,
                        Messages.enabledDisabled(enabled)
                    );
                }
                case "compression" -> {
                    int level = Integer.parseInt(value);
                    if (level < 1 || level > 22) {
                        yield ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_COMPRESSION_INVALID);
                    }
                    BackupConfig.setCompressionLevel(level);
                    yield ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_COMPRESSION, level);
                }
                case "threads" -> {
                    BackupConfig.setThreadCount(Integer.parseInt(value));
                    threadPool.updateThreadCount();
                    yield ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_THREADS, value);
                }
                case "max_versions" -> {
                    BackupConfig.setMaxVersions(Integer.parseInt(value));
                    yield ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_MAX_VERSIONS, value);
                }
                case "path" -> {
                    if (!BackupConfig.setStoragePath(value)) {
                        yield ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_PATH_INVALID);
                    }
                    yield ChatCompat.empty()
                        .append(ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_PATH, value))
                        .append("\n")
                        .append(ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_PATH_RESTART));
                }
                case "language" -> {
                    String normalized = ModI18n.normalizeLanguage(value);
                    if (normalized == null) {
                        yield ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_LANGUAGE_INVALID, value);
                    }
                    BackupConfig.setLanguage(normalized);
                    ModI18n.reload();
                    yield ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_LANGUAGE, normalized);
                }
                default -> ChatCompat.translatable(LangKeys.COMMAND_CONFIG_UNKNOWN_KEY, key);
            };
        } catch (NumberFormatException e) {
            return ChatCompat.translatable(LangKeys.COMMAND_CONFIG_INVALID_NUMBER, value);
        }
    }

    private static Component cleanBackups() {
        return backupManager.cleanAllBackups();
    }
}
