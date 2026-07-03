package io.github.limuqy.mc.backup.backup;

import io.github.limuqy.mc.backup.compression.CompressionQueue;
import io.github.limuqy.mc.backup.config.BackupConfig;
import io.github.limuqy.mc.backup.database.*;
import io.github.limuqy.mc.backup.hash.HashCalculator;
import io.github.limuqy.mc.backup.i18n.LangKeys;
import io.github.limuqy.mc.backup.logging.ModLog;
import io.github.limuqy.mc.backup.storage.BlobStore;
import io.github.limuqy.mc.backup.thread.BackupThreadPool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 备份引擎核心逻辑（无 Minecraft 依赖）。
 */
public class BackupEngine {
    private static BackupEngine instance;

    private final DatabaseManager dbManager = DatabaseManager.getInstance();
    private final BackupThreadPool threadPool = BackupThreadPool.getInstance();
    private final CompressionQueue compressionQueue = CompressionQueue.getInstance();

    private final Map<String, String> fileHashCache = new ConcurrentHashMap<>();
    private final Map<String, Long> fileSizeCache = new ConcurrentHashMap<>();
    private final Map<String, String> pendingCaptureMap = new ConcurrentHashMap<>();

    private final AtomicBoolean backupInProgress = new AtomicBoolean(false);
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicInteger totalFiles = new AtomicInteger(0);

    private Path worldPath;
    private Path backupPath;
    private BlobStore blobStore;
    private WorldFlushCallback worldFlush = () -> ModLog.warn("[Instant Backup] 未配置世界刷盘，跳过 flush");

    private BackupEngine() {
    }

    public static BackupEngine getInstance() {
        if (instance == null) {
            instance = new BackupEngine();
        }
        return instance;
    }

    public void init(Path worldPath, Path backupPath, WorldFlushCallback worldFlush) {
        this.worldPath = worldPath;
        this.backupPath = backupPath;
        if (worldFlush != null) {
            this.worldFlush = worldFlush;
        }
        this.blobStore = new BlobStore(backupPath);

        if (dbManager.isConnected()) {
            loadFileCaches();
            recoverOnStartup();
        } else {
            ModLog.warn("[Instant Backup] 数据库未连接，跳过加载缓存");
        }

        compressionQueue.start(blobStore);
    }

    private void loadFileCaches() {
        try {
            VersionInfo latestVersion = dbManager.getLatestVersion();
            if (latestVersion != null) {
                for (FileInfo file : dbManager.getFilesByVersionId(latestVersion.getId())) {
                    fileHashCache.put(file.getFilePath(), file.getFileHash());
                    fileSizeCache.put(file.getFilePath(), file.getFileSize());
                }
            }
        } catch (Exception e) {
            ModLog.error("加载文件哈希缓存失败", e);
        }
    }

    public void recoverOnStartup() {
        try {
            pendingCaptureMap.clear();
            for (BlobInfo blob : dbManager.getBlobsByState(BlobState.PENDING)) {
                pendingCaptureMap.put(blob.getFilePath(), blob.getBlobKey());
            }
            compressionQueue.requeueStagedBlobs();
            if (!BackupConfig.isCompressionEnabled()) {
                compressionQueue.drainPendingSync();
            }
            for (VersionInfo version : dbManager.getAllVersions()) {
                dbManager.refreshVersionCompletion(version.getId());
            }
            ModLog.info("[Instant Backup] 启动恢复完成，待捕获: {}, 压缩队列: {}",
                pendingCaptureMap.size(), compressionQueue.getQueueSize());
        } catch (Exception e) {
            ModLog.error("[Instant Backup] 启动恢复失败", e);
        }
    }

    public boolean isBackupInProgress() {
        return backupInProgress.get();
    }

    public int getProgress() {
        return progress.get();
    }

    public int getTotalFiles() {
        return totalFiles.get();
    }

    public int getPendingBlobCount() {
        try {
            return dbManager.countBlobsByState(BlobState.PENDING);
        } catch (Exception e) {
            return pendingCaptureMap.size();
        }
    }

    public int getCompressionQueueSize() {
        return compressionQueue.getQueueSize();
    }

    public Path getBackupPath() {
        return backupPath;
    }

    public OperationResult createBackupOperation(String description, boolean isManual) {
        if (!backupInProgress.compareAndSet(false, true)) {
            return OperationResult.fail(LangKeys.BACKUP_IN_PROGRESS);
        }

        String versionName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        long backupStartTime = System.currentTimeMillis();

        threadPool.submitTask(() -> {
            try {
                int[] migrateStats = migrateAllPendingBlobs();
                if (migrateStats[0] + migrateStats[1] > 0) {
                    ModLog.info("[Instant Backup] 创建备份前自动迁移: 捕获 {} 个, 跳过 {} 个",
                        migrateStats[0], migrateStats[1]);
                }

                worldFlush.flushWorld();

                List<FileInfo> allFiles = scanAllFiles();
                totalFiles.set(allFiles.size());
                progress.set(0);

                VersionInfo version = new VersionInfo(versionName, description, isManual);
                version.setStatus(VersionStatus.IN_PROGRESS);
                int versionId = dbManager.insertVersion(version);

                long totalSize = 0;
                for (FileInfo fileInfo : allFiles) {
                    fileInfo.setVersionId(versionId);
                    processFileForBackup(fileInfo);
                    totalSize += fileInfo.getFileSize();
                    fileHashCache.put(fileInfo.getFilePath(), fileInfo.getFileHash());
                    fileSizeCache.put(fileInfo.getFilePath(), fileInfo.getFileSize());
                    progress.incrementAndGet();
                }

                dbManager.insertFiles(allFiles);
                dbManager.updateVersionStats(versionId, allFiles.size(), totalSize);
                dbManager.refreshVersionCompletion(versionId);
                cleanOldVersions();

                long elapsedMs = System.currentTimeMillis() - backupStartTime;
                ModLog.info("[Instant Backup] 备份已创建: {} ({} 文件, 耗时 {} ms)", versionName, allFiles.size(), elapsedMs);

                // 异步执行 migrate，迁移新增的 pending blobs
                long postMigrateStartTime = System.currentTimeMillis();
                threadPool.submitTask(() -> {
                    try {
                        int[] postMigrateStats = migrateAllPendingBlobs();
                        if (postMigrateStats[0] + postMigrateStats[1] > 0) {
                            long postElapsedMs = System.currentTimeMillis() - postMigrateStartTime;
                            ModLog.info("[Instant Backup] 备份后自动迁移: 捕获 {} 个, 跳过 {} 个, 耗时 {} ms",
                                postMigrateStats[0], postMigrateStats[1], postElapsedMs);
                        }
                    } catch (Exception e) {
                        ModLog.error("[Instant Backup] 备份后自动迁移失败", e);
                    }
                });
            } catch (Throwable e) {
                ModLog.error("[Instant Backup] 备份任务执行失败", e);
            } finally {
                backupInProgress.set(false);
                progress.set(0);
                totalFiles.set(0);
            }
        });

        return OperationResult.ok(LangKeys.BACKUP_STARTED, versionName);
    }

    private List<FileInfo> scanAllFiles() throws IOException {
        List<FileInfo> files = new ArrayList<>();
        Files.walkFileTree(worldPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    String relativePath = normalizePath(worldPath.relativize(file));
                    long size = attrs.size();
                    boolean isChunk = HashCalculator.isChunkFile(file.getFileName().toString());
                    String hash = resolveFileHash(file, relativePath, size, isChunk);

                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setFilePath(relativePath);
                    fileInfo.setFileHash(hash);
                    fileInfo.setFileSize(size);
                    fileInfo.setChunk(isChunk);
                    fileInfo.refreshBlobKey();
                    files.add(fileInfo);
                } catch (IOException e) {
                    ModLog.debug("[Instant Backup] 跳过无法读取的文件: {}", file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private String resolveFileHash(Path file, String relativePath, long size, boolean isChunk) throws IOException {
        String cachedHash = fileHashCache.get(relativePath);
        Long cachedSize = fileSizeCache.get(relativePath);
        if (cachedHash != null && cachedSize != null && cachedSize == size) {
            return cachedHash;
        }
        return HashCalculator.calculateFileHash(file, isChunk, BackupConfig.isChunkFullHash());
    }

    private void processFileForBackup(FileInfo fileInfo) throws Exception {
        String blobKey = fileInfo.getBlobKey();
        BlobInfo existing = dbManager.getBlob(blobKey);

        if (existing != null) {
            if (existing.getState() == BlobState.PENDING) {
                registerPendingCapture(existing);
            }
            return;
        }

        boolean deferChunk = fileInfo.isChunk() && BackupConfig.isDeferredChunkMigration();
        BlobInfo blob = new BlobInfo(
            fileInfo.getFilePath(),
            fileInfo.getFileHash(),
            fileInfo.getFileSize(),
            fileInfo.isChunk(),
            deferChunk ? BlobState.PENDING : BlobState.STAGED
        );
        dbManager.upsertBlob(blob);

        if (deferChunk) {
            registerPendingCapture(blob);
        } else {
            captureBlob(blob);
        }
    }

    private void registerPendingCapture(BlobInfo blob) {
        pendingCaptureMap.put(blob.getFilePath(), blob.getBlobKey());
    }

    private void captureBlob(BlobInfo blob) throws Exception {
        Path source = worldPath.resolve(blob.getFilePath());
        if (!Files.exists(source)) {
            ModLog.warn("[Instant Backup] 源文件不存在，跳过捕获: {}", blob.getFilePath());
            return;
        }
        pendingCaptureMap.remove(blob.getFilePath());
        if (BackupConfig.isCompressionEnabled()) {
            if (BackupConfig.isCompressionAsync()) {
                blobStore.captureToRaw(source, blob.getFilePath(), blob.getFileHash());
                dbManager.updateBlobState(blob.getBlobKey(), BlobState.STAGED, 0);
                compressionQueue.enqueue(blob.getBlobKey());
            } else {
                long storedSize = blobStore.captureAndCompressDirect(source, blob.getFilePath(), blob.getFileHash());
                dbManager.updateBlobState(blob.getBlobKey(), BlobState.STORED, storedSize);
                onBlobStored(blob.getBlobKey());
                ModLog.debug("[Instant Backup] 同步压缩完成: {} ({} bytes)", blob.getFilePath(), storedSize);
            }
        } else {
            blobStore.captureToRaw(source, blob.getFilePath(), blob.getFileHash());
            long storedSize = blobStore.finalizeWithoutCompression(blob);
            dbManager.updateBlobState(blob.getBlobKey(), BlobState.STORED, storedSize);
            onBlobStored(blob.getBlobKey());
        }
    }

    public void onChunkSave(String relativePath) {
        if (worldPath == null) {
            return;
        }
        relativePath = normalizePath(relativePath);
        String blobKey = pendingCaptureMap.get(relativePath);
        if (blobKey == null) {
            return;
        }
        if (!pendingCaptureMap.remove(relativePath, blobKey)) {
            return;
        }
        try {
            BlobInfo blob = dbManager.getBlob(blobKey);
            if (blob == null || blob.getState() != BlobState.PENDING) {
                return;
            }
            Path source = worldPath.resolve(relativePath);
            if (!Files.exists(source)) {
                pendingCaptureMap.put(relativePath, blobKey);
                return;
            }
            captureBlob(blob);
        } catch (Exception e) {
            pendingCaptureMap.put(relativePath, blobKey);
            ModLog.error("[Instant Backup] 区块 COW 捕获失败: {}", relativePath, e);
        }
    }

    public void onBlobStored(String blobKey) {
        try {
            for (VersionInfo version : dbManager.getAllVersions()) {
                if (version.getStatus() != VersionStatus.COMPLETED) {
                    dbManager.refreshVersionCompletion(version.getId());
                }
            }
        } catch (SQLException e) {
            ModLog.error("[Instant Backup] 刷新版本状态失败: {}", blobKey, e);
        }
    }

    public OperationResult migrateVersionsOperation(int versionId) {
        long startTime = System.currentTimeMillis();
        try {
            List<Integer> versionIds = dbManager.getVersionIdsUpTo(versionId);
            if (versionIds.isEmpty()) {
                return OperationResult.fail(LangKeys.BACKUP_VERSION_NOT_EXISTS);
            }
            int[] stats = migratePendingBlobsForVersions(versionIds);
            long elapsedMs = System.currentTimeMillis() - startTime;
            ModLog.info("[Instant Backup] 迁移完成: 捕获 {} 个文件, 跳过 {} 个, 耗时 {} ms",
                stats[0], stats[1], elapsedMs);
            if (stats[1] > 0) {
                return OperationResult.ok(LangKeys.BACKUP_MIGRATE_DONE_WITH_SKIPPED, stats[0], stats[1]);
            }
            return OperationResult.ok(LangKeys.BACKUP_MIGRATE_DONE, stats[0]);
        } catch (Exception e) {
            ModLog.error("[Instant Backup] 封存失败", e);
            return OperationResult.fail(LangKeys.BACKUP_MIGRATE_FAILED, e.getMessage());
        }
    }

    private int[] migrateAllPendingBlobs() throws Exception {
        List<BlobInfo> pending = dbManager.getBlobsByState(BlobState.PENDING);
        int[] stats = migratePendingBlobList(pending);
        for (VersionInfo version : dbManager.getAllVersions()) {
            dbManager.refreshVersionCompletion(version.getId());
        }
        return stats;
    }

    private int[] migratePendingBlobsForVersions(List<Integer> versionIds) throws Exception {
        List<BlobInfo> pending = dbManager.getPendingBlobsForVersions(versionIds);
        int[] stats = migratePendingBlobList(pending);
        for (int id : versionIds) {
            dbManager.refreshVersionCompletion(id);
        }
        return stats;
    }

    private int[] migratePendingBlobList(List<BlobInfo> pending) throws Exception {
        int captured = 0;
        int skipped = 0;
        for (BlobInfo blob : pending) {
            Path source = worldPath.resolve(blob.getFilePath());
            if (!Files.exists(source)) {
                skipped++;
                continue;
            }
            captureBlob(blob);
            captured++;
        }
        return new int[] { captured, skipped };
    }

    public List<VersionInfo> getVersionList() {
        try {
            return dbManager.getAllVersions();
        } catch (Exception e) {
            ModLog.error("获取版本列表失败", e);
            return new ArrayList<>();
        }
    }

    public OperationResult deleteVersionOperation(String versionName) {
        long startTime = System.currentTimeMillis();
        try {
            VersionInfo version = dbManager.getVersionByName(versionName);
            if (version == null) {
                return OperationResult.fail(LangKeys.BACKUP_VERSION_NOT_FOUND, versionName);
            }

            dbManager.deleteFilesByVersionId(version.getId());
            dbManager.deleteVersion(version.getId());

            List<BlobInfo> removed = dbManager.gcUnreferencedBlobs();
            for (BlobInfo blob : removed) {
                blobStore.deleteBlobFiles(blob);
                pendingCaptureMap.remove(blob.getFilePath(), blob.getBlobKey());
            }

            long elapsedMs = System.currentTimeMillis() - startTime;
            ModLog.info("[Instant Backup] 删除完成: {} (清理 {} 个 blob, 耗时 {} ms)", versionName, removed.size(), elapsedMs);
            return OperationResult.ok(LangKeys.BACKUP_DELETE_DONE, versionName);
        } catch (Exception e) {
            return OperationResult.fail(LangKeys.BACKUP_DELETE_FAILED, e.getMessage());
        }
    }

    private void cleanOldVersions() {
        try {
            List<VersionInfo> versions = dbManager.getAllVersions();
            int maxVersions = BackupConfig.getMaxVersions();
            if (maxVersions > 0 && versions.size() > maxVersions) {
                for (int i = maxVersions; i < versions.size(); i++) {
                    deleteVersionOperation(versions.get(i).getVersionName());
                }
            }
        } catch (Exception e) {
            ModLog.error("清理旧版本失败", e);
        }
    }

    public OperationResult cleanAllBackupsOperation() {
        long startTime = System.currentTimeMillis();
        try {
            List<VersionInfo> versions = dbManager.getAllVersions();
            int versionCount = versions.size();
            for (VersionInfo version : versions) {
                dbManager.deleteFilesByVersionId(version.getId());
                dbManager.deleteVersion(version.getId());
            }

            if (blobStore != null && Files.exists(blobStore.getDataRoot())) {
                deleteDirectoryContents(blobStore.getDataRoot());
            }

            dbManager.clearAllBlobs();

            fileHashCache.clear();
            fileSizeCache.clear();
            pendingCaptureMap.clear();

            long elapsedMs = System.currentTimeMillis() - startTime;
            ModLog.info("[Instant Backup] 已清理所有备份数据 ({} 个版本, 耗时 {} ms)", versionCount, elapsedMs);
            return OperationResult.ok(LangKeys.BACKUP_CLEAN_DONE, backupPath);
        } catch (Exception e) {
            ModLog.error("清理备份数据失败", e);
            return OperationResult.fail(LangKeys.BACKUP_CLEAN_FAILED, e.getMessage());
        }
    }

    public void cleanOldDatabaseFiles() {
        try {
            Path oldDbPath = backupPath.resolve("backups.db");
            if (Files.exists(oldDbPath)) {
                Files.delete(oldDbPath);
                ModLog.info("[Instant Backup] 已清理旧数据库文件: {}", oldDbPath);
            }
            Path oldJournalPath = backupPath.resolve("backups.db-journal");
            if (Files.exists(oldJournalPath)) {
                Files.delete(oldJournalPath);
            }
        } catch (Exception e) {
            ModLog.warn("清理旧数据库文件失败", e);
        }
    }

    public OperationResult exportVersionOperation(String versionName) {
        try {
            VersionInfo version = dbManager.getVersionByName(versionName);
            if (version == null) {
                return OperationResult.fail(LangKeys.BACKUP_VERSION_NOT_FOUND, versionName);
            }

            String exportFileName = "InstantBackup_" + versionName + ".zip";
            Path exportFile = backupPath.resolve(exportFileName);

            threadPool.submitTask(() -> {
                long exportStartTime = System.currentTimeMillis();
                try {
                    List<FileInfo> files = dbManager.getFilesByVersionId(version.getId());
                    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(exportFile))) {
                        byte[] buffer = new byte[65536];
                        for (FileInfo fileInfo : files) {
                            BlobInfo blob = dbManager.getBlob(fileInfo.getBlobKey());
                            if (blob == null) {
                                continue;
                            }
                            zos.putNextEntry(new ZipEntry(fileInfo.getFilePath()));
                            try (InputStream input = blobStore.openRawContent(blob, worldPath)) {
                                int bytesRead;
                                while ((bytesRead = input.read(buffer)) != -1) {
                                    zos.write(buffer, 0, bytesRead);
                                }
                            }
                            zos.closeEntry();
                        }
                    }
                    long elapsedMs = System.currentTimeMillis() - exportStartTime;
                    ModLog.info("[Instant Backup] 导出完成: {} ({} 文件, 耗时 {} ms)", exportFile, files.size(), elapsedMs);
                } catch (Exception e) {
                    ModLog.error("[Instant Backup] 导出版本失败: {}", versionName, e);
                }
            });

            return OperationResult.ok(LangKeys.BACKUP_EXPORT_STARTED, exportFileName, backupPath);
        } catch (Exception e) {
            return OperationResult.fail(LangKeys.BACKUP_EXPORT_FAILED, e.getMessage());
        }
    }

    public void shutdown() {
        compressionQueue.shutdown();
        threadPool.shutdown();
    }

    private void deleteDirectoryContents(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (!d.equals(dir)) {
                    Files.delete(d);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String normalizePath(Path path) {
        return normalizePath(path.toString());
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/');
    }
}
