package io.github.limuqy.mc.backup.database;

import io.github.limuqy.mc.backup.config.BackupConfig;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/**
 * 数据库管理器门面单例
 * <p>
 * 保留现有 public API，内部委托给 MetadataStore 实现。
 * 调用方（BackupEngine、CompressionQueue 等）无需感知后端类型。
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private MetadataStore store;

    private DatabaseManager() {
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * 初始化元数据存储
     *
     * @param configDir 配置目录（config/instantbackup/）
     * @return 初始化是否成功
     */
    public boolean init(Path configDir) {
        try {
            MetadataBackend backend = BackupConfig.getMetadataBackend();
            store = MetadataStoreFactory.create(backend);
            boolean success = store.init(configDir);
            if (!success) {
                System.err.println("[Instant Backup] 元数据存储初始化失败（后端: " + backend.getConfigValue() + "）");
                if (backend == MetadataBackend.SQLITE) {
                    System.err.println("[Instant Backup] 请安装 Minecraft SQLite JDBC 模组，或将 storage.metadata.type 设为 csv。");
                    System.err.println("[Instant Backup] Modrinth: https://modrinth.com/plugin/minecraft-sqlite-jdbc");
                } else if (backend == MetadataBackend.MYSQL) {
                    System.err.println("[Instant Backup] 请检查 MySQL 连接配置（storage.mysql.*）是否正确。");
                }
            }
            return success;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isConnected() {
        return store != null && store.isConnected();
    }

    public void close() {
        if (store != null) {
            store.close();
        }
    }

    // ========== 版本操作 ==========

    public int insertVersion(VersionInfo version) throws SQLException {
        return store.insertVersion(version);
    }

    public void updateVersionStats(int versionId, int fileCount, long totalSize) throws SQLException {
        store.updateVersionStats(versionId, fileCount, totalSize);
    }

    public void updateVersionStatus(int versionId, VersionStatus status) throws SQLException {
        store.updateVersionStatus(versionId, status);
    }

    public VersionInfo getVersion(int id) throws SQLException {
        return store.getVersion(id);
    }

    public VersionInfo getVersionByName(String versionName) throws SQLException {
        return store.getVersionByName(versionName);
    }

    public List<VersionInfo> getAllVersions() throws SQLException {
        return store.getAllVersions();
    }

    public VersionInfo getLatestVersion() throws SQLException {
        return store.getLatestVersion();
    }

    public void deleteVersion(int id) throws SQLException {
        store.deleteVersion(id);
    }

    public boolean isVersionFullyStored(int versionId) throws SQLException {
        return store.isVersionFullyStored(versionId);
    }

    public void refreshVersionCompletion(int versionId) throws SQLException {
        store.refreshVersionCompletion(versionId);
    }

    public List<Integer> getVersionIdsUpTo(int versionId) throws SQLException {
        return store.getVersionIdsUpTo(versionId);
    }

    // ========== 文件 manifest ==========

    public void insertFiles(List<FileInfo> files) throws SQLException {
        store.insertFiles(files);
    }

    public List<FileInfo> getFilesByVersionId(int versionId) throws SQLException {
        return store.getFilesByVersionId(versionId);
    }

    public void deleteFilesByVersionId(int versionId) throws SQLException {
        store.deleteFilesByVersionId(versionId);
    }

    public List<FileInfo> getFilesByBlobKeys(List<String> blobKeys) throws SQLException {
        return store.getFilesByBlobKeys(blobKeys);
    }

    // ========== Blob 操作 ==========

    public void upsertBlob(BlobInfo blob) throws SQLException {
        store.upsertBlob(blob);
    }

    public BlobInfo getBlob(String blobKey) throws SQLException {
        return store.getBlob(blobKey);
    }

    public void updateBlobState(String blobKey, BlobState state, long compressedSize) throws SQLException {
        store.updateBlobState(blobKey, state, compressedSize);
    }

    public List<BlobInfo> getBlobsByState(BlobState state) throws SQLException {
        return store.getBlobsByState(state);
    }

    public int countBlobsByState(BlobState state) throws SQLException {
        return store.countBlobsByState(state);
    }

    public List<BlobInfo> getPendingBlobsForVersions(List<Integer> versionIds) throws SQLException {
        return store.getPendingBlobsForVersions(versionIds);
    }

    public int getBlobReferenceCount(String blobKey) throws SQLException {
        return store.getBlobReferenceCount(blobKey);
    }

    public void deleteBlob(String blobKey) throws SQLException {
        store.deleteBlob(blobKey);
    }

    public List<BlobInfo> gcUnreferencedBlobs() throws SQLException {
        return store.gcUnreferencedBlobs();
    }

    public void clearAllBlobs() throws SQLException {
        store.clearAllBlobs();
    }
}
