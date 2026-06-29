package io.github.limuqy.mc.backup.backup;

import io.github.limuqy.mc.backup.config.BackupConfig;
import io.github.limuqy.mc.backup.database.VersionInfo;
import io.github.limuqy.mc.backup.database.VersionStatus;
import io.github.limuqy.mc.backup.i18n.I18n;
import io.github.limuqy.mc.backup.i18n.LangKeys;
import io.github.limuqy.mc.backup.thread.BackupThreadPool;

import java.util.List;

/**
 * 备份业务门面（无 Minecraft 依赖），供 Mod 命令与 CLI 共用。
 */
public class BackupService {
    private final BackupEngine backupEngine = BackupEngine.getInstance();
    private final BackupThreadPool threadPool = BackupThreadPool.getInstance();

    public String execute(String[] args) {
        if (args.length == 0) {
            return formatHelp();
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "help":
                return formatHelp();
            case "create":
                return formatResult(createBackup(args));
            case "list":
                return formatVersionList();
            case "delete":
                return formatResult(deleteVersion(args));
            case "export":
                return formatResult(exportVersion(args));
            case "migrate":
                return formatResult(migrateVersions(args));
            case "status":
                return formatStatus();
            case "clean":
                return formatResult(backupEngine.cleanAllBackupsOperation());
            default:
                return I18n.format(LangKeys.COMMAND_ERROR_UNKNOWN, subCommand);
        }
    }

    public OperationResult createBackup(String description, boolean isManual) {
        return backupEngine.createBackupOperation(description, isManual);
    }

    public OperationResult deleteVersion(String versionName) {
        return backupEngine.deleteVersionOperation(versionName);
    }

    public OperationResult exportVersion(String versionName) {
        return backupEngine.exportVersionOperation(versionName);
    }

    public OperationResult migrateVersions(int versionId) {
        return backupEngine.migrateVersionsOperation(versionId);
    }

    public List<VersionInfo> getVersionList() {
        return backupEngine.getVersionList();
    }

    public boolean isBackupInProgress() {
        return backupEngine.isBackupInProgress();
    }

    public void awaitIdle(long timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (!backupEngine.isBackupInProgress()
                && threadPool.getQueueSize() == 0
                && threadPool.getActiveThreads() == 0) {
                return;
            }
            Thread.sleep(200L);
        }
    }

    private OperationResult createBackup(String[] args) {
        String description = null;
        if (args.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) {
                    sb.append(' ');
                }
                sb.append(args[i]);
            }
            description = sb.toString();
        }
        return createBackup(description, true);
    }

    private OperationResult deleteVersion(String[] args) {
        VersionInfo version = getVersionByIndex(args);
        if (version == null) {
            if (args.length < 2) {
                return OperationResult.fail(LangKeys.COMMAND_ERROR_MISSING_INDEX, "delete");
            }
            return OperationResult.fail(LangKeys.COMMAND_ERROR_INVALID_INDEX, args[1]);
        }
        return deleteVersion(version.getVersionName());
    }

    private OperationResult exportVersion(String[] args) {
        VersionInfo version = getVersionByIndex(args);
        if (version == null) {
            if (args.length < 2) {
                return OperationResult.fail(LangKeys.COMMAND_ERROR_MISSING_INDEX, "export");
            }
            return OperationResult.fail(LangKeys.COMMAND_ERROR_INVALID_INDEX, args[1]);
        }
        return exportVersion(version.getVersionName());
    }

    private OperationResult migrateVersions(String[] args) {
        VersionInfo version = getVersionByIndex(args);
        if (version == null) {
            if (args.length < 2) {
                return OperationResult.fail(LangKeys.COMMAND_ERROR_MISSING_INDEX, "migrate");
            }
            return OperationResult.fail(LangKeys.COMMAND_ERROR_INVALID_INDEX, args[1]);
        }
        return migrateVersions(version.getId());
    }

    private VersionInfo getVersionByIndex(String[] args) {
        if (args.length < 2) {
            return null;
        }

        List<VersionInfo> versions = getVersionList();
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

    private String formatResult(OperationResult result) {
        return I18n.format(result.langKey(), result.args());
    }

    private String formatHelp() {
        return String.join("\n",
            I18n.format(LangKeys.COMMAND_HELP_TITLE),
            I18n.format(LangKeys.COMMAND_HELP_LINE_HELP),
            I18n.format(LangKeys.COMMAND_HELP_LINE_CREATE),
            I18n.format(LangKeys.COMMAND_HELP_LINE_LIST),
            I18n.format(LangKeys.COMMAND_HELP_LINE_DELETE),
            I18n.format(LangKeys.COMMAND_HELP_LINE_EXPORT),
            I18n.format(LangKeys.COMMAND_HELP_LINE_MIGRATE),
            I18n.format(LangKeys.COMMAND_HELP_LINE_STATUS),
            I18n.format(LangKeys.COMMAND_HELP_LINE_CONFIG),
            I18n.format(LangKeys.COMMAND_HELP_LINE_CLEAN)
        );
    }

    private String formatVersionList() {
        List<VersionInfo> versions = getVersionList();
        if (versions.isEmpty()) {
            return I18n.format(LangKeys.COMMAND_LIST_EMPTY);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(I18n.format(LangKeys.COMMAND_LIST_TITLE)).append('\n');
        sb.append(I18n.format(LangKeys.COMMAND_LIST_HEADER)).append('\n');
        sb.append(I18n.format(LangKeys.COMMAND_LIST_SEPARATOR));
        for (int i = 0; i < versions.size(); i++) {
            VersionInfo version = versions.get(i);
            sb.append('\n').append(I18n.format(
                LangKeys.COMMAND_LIST_ROW,
                i + 1,
                version.getVersionName(),
                I18n.format(version.getStatus() == VersionStatus.COMPLETED
                    ? LangKeys.VERSION_STATUS_COMPLETED
                    : LangKeys.VERSION_STATUS_IN_PROGRESS),
                I18n.format(version.isManual() ? LangKeys.VERSION_TYPE_MANUAL : LangKeys.VERSION_TYPE_AUTO),
                version.getDisplayDescription()
            ));
        }
        return sb.toString();
    }

    private String formatStatus() {
        int inProgressVersions = 0;
        for (VersionInfo v : getVersionList()) {
            if (v.getStatus() != VersionStatus.COMPLETED) {
                inProgressVersions++;
            }
        }

        String scanningLine = backupEngine.isBackupInProgress()
            ? I18n.format(LangKeys.COMMAND_STATUS_SCANNING, backupEngine.getProgress(), backupEngine.getTotalFiles())
            : I18n.format(LangKeys.COMMAND_STATUS_IDLE);

        return String.join("\n",
            I18n.format(LangKeys.COMMAND_STATUS_TITLE),
            scanningLine,
            I18n.format(LangKeys.COMMAND_STATUS_PENDING_BLOB, backupEngine.getPendingBlobCount()),
            I18n.format(LangKeys.COMMAND_STATUS_COMPRESSION_QUEUE, backupEngine.getCompressionQueueSize()),
            I18n.format(LangKeys.COMMAND_STATUS_IN_PROGRESS_VERSIONS, inProgressVersions),
            I18n.format(
                LangKeys.COMMAND_STATUS_THREAD_POOL,
                threadPool.getCompletedTasks(),
                threadPool.getTotalTasks(),
                threadPool.getActiveThreads(),
                threadPool.getQueueSize()
            )
        );
    }
}
