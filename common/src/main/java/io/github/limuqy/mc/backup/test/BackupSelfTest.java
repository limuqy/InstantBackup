package io.github.limuqy.mc.backup.test;

import io.github.limuqy.mc.backup.backup.BackupManager;
import io.github.limuqy.mc.backup.command.BackupCommandHandler;
import io.github.limuqy.mc.backup.database.VersionInfo;
import io.github.limuqy.mc.backup.database.VersionStatus;
import io.github.limuqy.mc.backup.thread.BackupThreadPool;
import net.minecraft.network.chat.Component;
import io.github.limuqy.mc.backup.compat.ModLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 开发环境自检：通过 -Dinstantbackup.selftest=true 启用
 */
public final class BackupSelfTest {
    private BackupSelfTest() {
    }

    public static void run(Path backupPath) {
        BackupThreadPool.getInstance().submitTask(() -> {
            try {
                Thread.sleep(3000);
                ModLog.info("[SelfTest] === 开始全链路自检 ===");

                Component create1 = BackupCommandHandler.execute(new String[]{"create", "selftest-1"});
                ModLog.info("[SelfTest] create-1: {}", create1.getString().replace('\n', ' '));

                waitForBackupIdle(120_000);

                int pendingAfterFirst = BackupManager.getInstance().getPendingBlobCount();
                ModLog.info("[SelfTest] 首次备份后待迁移 blob: {}", pendingAfterFirst);

                Component create2 = BackupCommandHandler.execute(new String[]{"create", "selftest-2"});
                ModLog.info("[SelfTest] create-2: {}", create2.getString().replace('\n', ' '));

                waitForBackupIdle(120_000);

                Component status = BackupCommandHandler.execute(new String[]{"status"});
                ModLog.info("[SelfTest] status:\n{}", status.getString());

                Component list = BackupCommandHandler.execute(new String[]{"list"});
                ModLog.info("[SelfTest] list:\n{}", list.getString());

                List<VersionInfo> versions = BackupManager.getInstance().getVersionList();
                if (versions.size() < 2) {
                    ModLog.error("[SelfTest] FAIL: 连续备份后版本数不足");
                    return;
                }

                Component export = BackupCommandHandler.execute(new String[]{"export", "1"});
                ModLog.info("[SelfTest] export: {}", export.getString().replace('\n', ' '));

                Thread.sleep(15_000);

                Path dataRoot = backupPath.resolve("data");
                long zstCount = 0;
                if (Files.exists(dataRoot)) {
                    try (Stream<Path> walk = Files.walk(dataRoot)) {
                        zstCount = walk.filter(p -> p.toString().endsWith(".zst")).count();
                    }
                }
                long zipCount = 0;
                if (Files.exists(backupPath)) {
                    try (Stream<Path> files = Files.list(backupPath)) {
                        zipCount = files.filter(p -> p.getFileName().toString().startsWith("InstantBackup_")
                            && p.getFileName().toString().endsWith(".zip")).count();
                    }
                }

                versions = BackupManager.getInstance().getVersionList();
                boolean versionCompleted = !versions.isEmpty()
                    && versions.get(0).getStatus() == VersionStatus.COMPLETED;

                boolean pass = zstCount > 0 && versionCompleted && versions.size() >= 2;
                ModLog.info("[SelfTest] 产物: zst={}, zip={}", zstCount, zipCount);
                ModLog.info("[SelfTest] {}", pass ? "PASS: 全链路验证通过" : "FAIL: 验证未通过");
            } catch (Exception e) {
                ModLog.error("[SelfTest] FAIL: 自检异常", e);
            }
        });
    }

    private static void waitForBackupIdle(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!BackupManager.getInstance().isBackupInProgress()
                && BackupManager.getInstance().getCompressionQueueSize() == 0) {
                return;
            }
            Thread.sleep(2000);
        }
    }
}
