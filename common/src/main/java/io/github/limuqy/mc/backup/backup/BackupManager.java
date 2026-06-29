package io.github.limuqy.mc.backup.backup;

import io.github.limuqy.mc.backup.compat.ChatCompat;
import io.github.limuqy.mc.backup.compat.ServerCompat;
import io.github.limuqy.mc.backup.database.VersionInfo;
import io.github.limuqy.mc.backup.thread.BackupThreadPool;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mod 侧备份管理器：委托 {@link BackupEngine}，并注入 Minecraft 世界刷盘与 COW。
 */
public class BackupManager {
    private static BackupManager instance;

    private final BackupEngine engine = BackupEngine.getInstance();
    private MinecraftServer server;

    private BackupManager() {
    }

    public static BackupManager getInstance() {
        if (instance == null) {
            instance = new BackupManager();
        }
        return instance;
    }

    public void init(Path worldPath, Path backupPath, MinecraftServer server) {
        this.server = server;
        engine.init(worldPath, backupPath, this::flushWorld);
    }

    public void recoverOnStartup() {
        engine.recoverOnStartup();
    }

    public boolean isBackupInProgress() {
        return engine.isBackupInProgress();
    }

    public int getProgress() {
        return engine.getProgress();
    }

    public int getTotalFiles() {
        return engine.getTotalFiles();
    }

    public int getPendingBlobCount() {
        return engine.getPendingBlobCount();
    }

    public int getCompressionQueueSize() {
        return engine.getCompressionQueueSize();
    }

    public Component createBackup(String description, boolean isManual) {
        return toComponent(engine.createBackupOperation(description, isManual));
    }

    public OperationResult createBackupOperation(String description, boolean isManual) {
        return engine.createBackupOperation(description, isManual);
    }

    public void onChunkSave(String relativePath) {
        engine.onChunkSave(relativePath);
    }

    public void onBlobStored(String blobKey) {
        engine.onBlobStored(blobKey);
    }

    public Component migrateVersions(int versionId) {
        return toComponent(engine.migrateVersionsOperation(versionId));
    }

    public OperationResult migrateVersionsOperation(int versionId) {
        return engine.migrateVersionsOperation(versionId);
    }

    public List<VersionInfo> getVersionList() {
        return engine.getVersionList();
    }

    public Component deleteVersion(String versionName) {
        return toComponent(engine.deleteVersionOperation(versionName));
    }

    public OperationResult deleteVersionOperation(String versionName) {
        return engine.deleteVersionOperation(versionName);
    }

    public Component cleanAllBackups() {
        return toComponent(engine.cleanAllBackupsOperation());
    }

    public OperationResult cleanAllBackupsOperation() {
        return engine.cleanAllBackupsOperation();
    }

    public void cleanOldDatabaseFiles() {
        engine.cleanOldDatabaseFiles();
    }

    public Component exportVersion(String versionName) {
        return toComponent(engine.exportVersionOperation(versionName));
    }

    public OperationResult exportVersionOperation(String versionName) {
        return engine.exportVersionOperation(versionName);
    }

    private void flushWorld() throws Exception {
        if (server == null) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        server.execute(() -> {
            try {
                ServerCompat.saveEverything(server, true, true, true);
            } catch (Exception e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error.get() != null) {
            throw error.get();
        }
    }

    private static Component toComponent(OperationResult result) {
        return ChatCompat.translatable(result.langKey(), result.args());
    }
}
