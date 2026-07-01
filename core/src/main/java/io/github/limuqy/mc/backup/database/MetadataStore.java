package io.github.limuqy.mc.backup.database;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/**
 * 元数据存储接口，三种后端（CSV / SQLite / MySQL）共用此接口
 * <p>
 * 异常约定：CSV 实现可将 IOException 包装为 SQLException 以保持调用方兼容
 */
public interface MetadataStore {

    // ========== 生命周期 ==========

    /**
     * 按 backend 初始化存储（建目录 / 连库 / 校验 schema）
     *
     * @param configDir 配置目录（config/instantbackup/）
     * @return 初始化是否成功
     */
    boolean init(Path configDir);

    /**
     * 存储是否可用
     */
    boolean isConnected();

    /**
     * 释放连接 / 刷盘
     */
    void close();

    // ========== 版本操作 ==========

    /**
     * 插入版本并返回自增 id
     */
    int insertVersion(VersionInfo version) throws SQLException;

    /**
     * 更新版本统计信息
     */
    void updateVersionStats(int versionId, int fileCount, long totalSize) throws SQLException;

    /**
     * 更新版本状态
     */
    void updateVersionStatus(int versionId, VersionStatus status) throws SQLException;

    /**
     * 按 id 查询版本
     */
    VersionInfo getVersion(int id) throws SQLException;

    /**
     * 按名称查询版本
     */
    VersionInfo getVersionByName(String versionName) throws SQLException;

    /**
     * 获取所有版本，按 timestamp DESC 排序
     */
    List<VersionInfo> getAllVersions() throws SQLException;

    /**
     * 获取最新版本
     */
    VersionInfo getLatestVersion() throws SQLException;

    /**
     * 删除版本（CASCADE 删 file_info）
     */
    void deleteVersion(int id) throws SQLException;

    /**
     * 检查版本是否所有 blob 均已 STORED
     */
    boolean isVersionFullyStored(int versionId) throws SQLException;

    /**
     * 根据 blob 状态更新版本为 COMPLETED 或 IN_PROGRESS
     */
    void refreshVersionCompletion(int versionId) throws SQLException;

    /**
     * 从指定 id 起（含）至最新的 id 列表
     */
    List<Integer> getVersionIdsUpTo(int versionId) throws SQLException;

    // ========== 文件 manifest ==========

    /**
     * 批量插入文件清单
     */
    void insertFiles(List<FileInfo> files) throws SQLException;

    /**
     * 获取版本的文件清单
     */
    List<FileInfo> getFilesByVersionId(int versionId) throws SQLException;

    /**
     * 删除版本的文件清单
     */
    void deleteFilesByVersionId(int versionId) throws SQLException;

    /**
     * 按 blob_key 列表查询文件
     */
    List<FileInfo> getFilesByBlobKeys(List<String> blobKeys) throws SQLException;

    // ========== Blob 操作 ==========

    /**
     * 插入或更新 blob（冲突时更新 state 和 compressed_size）
     */
    void upsertBlob(BlobInfo blob) throws SQLException;

    /**
     * 单条 blob 查询
     */
    BlobInfo getBlob(String blobKey) throws SQLException;

    /**
     * 更新 blob 状态和压缩大小
     */
    void updateBlobState(String blobKey, BlobState state, long compressedSize) throws SQLException;

    /**
     * 按状态查询 blob 列表
     */
    List<BlobInfo> getBlobsByState(BlobState state) throws SQLException;

    /**
     * 按状态计数 blob
     */
    int countBlobsByState(BlobState state) throws SQLException;

    /**
     * 获取指定版本集合内的 PENDING blob
     */
    List<BlobInfo> getPendingBlobsForVersions(List<Integer> versionIds) throws SQLException;

    /**
     * 获取 blob 在 file_info 中的引用数
     */
    int getBlobReferenceCount(String blobKey) throws SQLException;

    /**
     * 删除 blob 行
     */
    void deleteBlob(String blobKey) throws SQLException;

    /**
     * 删除零引用 blob，返回被删列表供删物理文件
     */
    List<BlobInfo> gcUnreferencedBlobs() throws SQLException;

    /**
     * 清空 blobs 表（clean 命令）
     */
    void clearAllBlobs() throws SQLException;
}
