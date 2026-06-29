package io.github.limuqy.mc.backup.i18n;

import io.github.limuqy.mc.backup.compat.ChatCompat;
import io.github.limuqy.mc.backup.config.BackupConfig;
import io.github.limuqy.mc.backup.database.VersionInfo;
import io.github.limuqy.mc.backup.database.VersionStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * 组合消息的工厂方法。
 */
public final class Messages {
    private Messages() {
    }

    public static Component help() {
        return joinLines(
            ChatCompat.translatable(LangKeys.COMMAND_HELP_TITLE),
            ChatCompat.translatable(LangKeys.COMMAND_HELP_LINE_HELP),
            ChatCompat.translatable(LangKeys.COMMAND_HELP_LINE_CREATE),
            ChatCompat.translatable(LangKeys.COMMAND_HELP_LINE_LIST),
            ChatCompat.translatable(LangKeys.COMMAND_HELP_LINE_DELETE),
            ChatCompat.translatable(LangKeys.COMMAND_HELP_LINE_EXPORT),
            ChatCompat.translatable(LangKeys.COMMAND_HELP_LINE_MIGRATE),
            ChatCompat.translatable(LangKeys.COMMAND_HELP_LINE_STATUS),
            ChatCompat.translatable(LangKeys.COMMAND_HELP_LINE_CONFIG),
            ChatCompat.translatable(LangKeys.COMMAND_HELP_LINE_CLEAN)
        );
    }

    public static Component unknownCommand(String subCommand) {
        return ChatCompat.translatable(LangKeys.COMMAND_ERROR_UNKNOWN, subCommand);
    }

    public static Component missingIndex(String command) {
        return ChatCompat.translatable(LangKeys.COMMAND_ERROR_MISSING_INDEX, command);
    }

    public static Component invalidIndex(String index) {
        return ChatCompat.translatable(LangKeys.COMMAND_ERROR_INVALID_INDEX, index);
    }

    public static Component versionList(List<VersionInfo> versions) {
        if (versions.isEmpty()) {
            return ChatCompat.translatable(LangKeys.COMMAND_LIST_EMPTY);
        }

        MutableComponent result = ChatCompat.empty()
            .append(ChatCompat.translatable(LangKeys.COMMAND_LIST_TITLE))
            .append("\n")
            .append(ChatCompat.translatable(LangKeys.COMMAND_LIST_HEADER))
            .append("\n")
            .append(ChatCompat.translatable(LangKeys.COMMAND_LIST_SEPARATOR));

        for (int i = 0; i < versions.size(); i++) {
            result.append("\n").append(versionRow(i + 1, versions.get(i)));
        }
        return result;
    }

    public static Component versionRow(int index, VersionInfo version) {
        return ChatCompat.translatable(
            LangKeys.COMMAND_LIST_ROW,
            index,
            version.getVersionName(),
            statusLabel(version),
            backupTypeLabel(version),
            version.getDisplayDescription()
        );
    }

    public static Component statusLabel(VersionInfo version) {
        return ChatCompat.translatable(
            version.getStatus() == VersionStatus.COMPLETED
                ? LangKeys.VERSION_STATUS_COMPLETED
                : LangKeys.VERSION_STATUS_IN_PROGRESS
        );
    }

    public static Component backupTypeLabel(VersionInfo version) {
        return ChatCompat.translatable(
            version.isManual() ? LangKeys.VERSION_TYPE_MANUAL : LangKeys.VERSION_TYPE_AUTO
        );
    }

    public static Component yesNo(boolean value) {
        return ChatCompat.translatable(value ? LangKeys.COMMON_YES : LangKeys.COMMON_NO);
    }

    public static Component enabledDisabled(boolean value) {
        return ChatCompat.translatable(value ? LangKeys.COMMON_ENABLED : LangKeys.COMMON_DISABLED);
    }

    public static Component status(
        boolean backupInProgress,
        int progress,
        int totalFiles,
        int pendingBlobCount,
        int compressionQueueSize,
        int inProgressVersions,
        long completedTasks,
        long totalTasks,
        int activeThreads,
        int queueSize
    ) {
        MutableComponent result = ChatCompat.empty()
            .append(ChatCompat.translatable(LangKeys.COMMAND_STATUS_TITLE))
            .append("\n");

        if (backupInProgress) {
            result.append(ChatCompat.translatable(LangKeys.COMMAND_STATUS_SCANNING, progress, totalFiles));
        } else {
            result.append(ChatCompat.translatable(LangKeys.COMMAND_STATUS_IDLE));
        }

        return result
            .append("\n")
            .append(ChatCompat.translatable(LangKeys.COMMAND_STATUS_PENDING_BLOB, pendingBlobCount))
            .append("\n")
            .append(ChatCompat.translatable(LangKeys.COMMAND_STATUS_COMPRESSION_QUEUE, compressionQueueSize))
            .append("\n")
            .append(ChatCompat.translatable(LangKeys.COMMAND_STATUS_IN_PROGRESS_VERSIONS, inProgressVersions))
            .append("\n")
            .append(ChatCompat.translatable(
                LangKeys.COMMAND_STATUS_THREAD_POOL,
                completedTasks,
                totalTasks,
                activeThreads,
                queueSize
            ));
    }

    public static Component configDisplay() {
        return joinLines(
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_TITLE),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_INTERVAL, BackupConfig.getBackupInterval()),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_ENABLED, enabledDisabled(BackupConfig.isEnabled())),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_ONLINE_ONLY, yesNo(BackupConfig.isOnlyWhenPlayersOnline())),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_EXCLUDE_BOTS, yesNo(BackupConfig.isExcludeCarpetBots())),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_DEFERRED_CHUNK, yesNo(BackupConfig.isDeferredChunkMigration())),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_CHUNK_FULL_HASH, yesNo(BackupConfig.isChunkFullHash())),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_COMPRESSION_LEVEL, BackupConfig.getCompressionLevel()),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_COMPRESSION_THREADS, BackupConfig.getCompressionThreads()),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_THREADS, BackupConfig.getThreadCount()),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_MAX_VERSIONS, BackupConfig.getMaxVersions()),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_STORAGE_PATH, BackupConfig.getStoragePath()),
            ChatCompat.translatable(LangKeys.COMMAND_CONFIG_LANGUAGE, BackupConfig.getLanguage())
        );
    }

    private static Component joinLines(Component... lines) {
        MutableComponent result = ChatCompat.empty();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                result.append("\n");
            }
            result.append(lines[i]);
        }
        return result;
    }
}
