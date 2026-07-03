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
        switch (subCommand) {
            case "help": return Messages.help();
            case "create": return createBackup(args);
            case "list": return listVersions();
            case "delete": return deleteVersion(args);
            case "export": return exportVersion(args);
            case "status": return showStatus();
            case "config": return showConfig(args);
            case "clean": return cleanBackups();
            default: return Messages.unknownCommand(subCommand);
        }
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
            Component result;
            switch (key) {
                case "interval":
                    BackupConfig.setBackupInterval(Integer.parseInt(value));
                    result = ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_INTERVAL, value);
                    break;
                case "enabled": {
                    boolean enabled = Boolean.parseBoolean(value);
                    BackupConfig.setEnabled(enabled);
                    result = ChatCompat.translatable(
                        LangKeys.COMMAND_CONFIG_SET_ENABLED,
                        Messages.enabledDisabled(enabled)
                    );
                    break;
                }
                case "online_only": {
                    boolean enabled = Boolean.parseBoolean(value);
                    BackupConfig.setOnlyWhenPlayersOnline(enabled);
                    result = ChatCompat.translatable(
                        LangKeys.COMMAND_CONFIG_SET_ONLINE_ONLY,
                        Messages.enabledDisabled(enabled)
                    );
                    break;
                }
                case "exclude_bots": {
                    boolean enabled = Boolean.parseBoolean(value);
                    BackupConfig.setExcludeCarpetBots(enabled);
                    result = ChatCompat.translatable(
                        LangKeys.COMMAND_CONFIG_SET_EXCLUDE_BOTS,
                        Messages.enabledDisabled(enabled)
                    );
                    break;
                }
                case "compression": {
                    int level = Integer.parseInt(value);
                    if (level < 1 || level > 22) {
                        result = ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_COMPRESSION_INVALID);
                    } else {
                        BackupConfig.setCompressionLevel(level);
                        result = ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_COMPRESSION, level);
                    }
                    break;
                }
                case "threads":
                    BackupConfig.setThreadCount(Integer.parseInt(value));
                    threadPool.updateThreadCount();
                    result = ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_THREADS, value);
                    break;
                case "max_versions":
                    BackupConfig.setMaxVersions(Integer.parseInt(value));
                    result = ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_MAX_VERSIONS, value);
                    break;
                case "path": {
                    if (!BackupConfig.setStoragePath(value)) {
                        result = ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_PATH_INVALID);
                    } else {
                        result = ChatCompat.empty()
                            .append(ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_PATH, value))
                            .append("\n")
                            .append(ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_PATH_RESTART));
                    }
                    break;
                }
                case "language": {
                    String normalized = ModI18n.normalizeLanguage(value);
                    if (normalized == null) {
                        result = ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_LANGUAGE_INVALID, value);
                    } else {
                        BackupConfig.setLanguage(normalized);
                        ModI18n.reload();
                        result = ChatCompat.translatable(LangKeys.COMMAND_CONFIG_SET_LANGUAGE, normalized);
                    }
                    break;
                }
                default:
                    result = ChatCompat.translatable(LangKeys.COMMAND_CONFIG_UNKNOWN_KEY, key);
                    break;
            }
            return result;
        } catch (NumberFormatException e) {
            return ChatCompat.translatable(LangKeys.COMMAND_CONFIG_INVALID_NUMBER, value);
        }
    }

    private static Component cleanBackups() {
        return backupManager.cleanAllBackups();
    }
}
