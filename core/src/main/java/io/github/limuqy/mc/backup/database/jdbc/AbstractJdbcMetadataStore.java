package io.github.limuqy.mc.backup.database.jdbc;

import io.github.limuqy.mc.backup.database.*;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JDBC 元数据存储抽象基类，SQLite 和 MySQL 共享此实现
 */
public abstract class AbstractJdbcMetadataStore implements MetadataStore {
    protected Connection connection;
    protected final SqlDialect dialect;
    protected static final int SCHEMA_VERSION = 2;

    protected AbstractJdbcMetadataStore(SqlDialect dialect) {
        this.dialect = dialect;
    }

    /**
     * 获取物理表名（子类可覆盖以支持表名前缀）
     */
    protected String tableName(String logicalName) {
        return logicalName;
    }

    /**
     * 创建数据库连接（子类实现）
     */
    protected abstract Connection createConnection(Path configDir) throws Exception;

    /**
     * 创建表（子类实现，使用各自的 DDL 方言）
     */
    protected abstract void createTables() throws SQLException;

    /**
     * 检查表是否存在（子类实现）
     */
    protected abstract boolean tableExists(String tableName) throws SQLException;

    /**
     * 重建 schema（子类实现）
     */
    protected abstract void recreateSchema() throws SQLException;

    @Override
    public boolean init(Path configDir) {
        try {
            connection = createConnection(configDir);
            if (!isSchemaCurrent()) {
                recreateSchema();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    protected boolean isSchemaCurrent() {
        try {
            if (!tableExists("schema_meta")) {
                return false;
            }
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT version FROM " + tableName("schema_meta") + " LIMIT 1")) {
                if (rs.next()) {
                    return rs.getInt("version") >= SCHEMA_VERSION;
                }
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }

    protected void insertSchemaVersion() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM " + tableName("schema_meta"));
            stmt.execute("INSERT INTO " + tableName("schema_meta") + " (version) VALUES (" + SCHEMA_VERSION + ")");
        }
    }

    // ========== 版本操作 ==========

    @Override
    public int insertVersion(VersionInfo version) throws SQLException {
        String sql = "INSERT INTO " + tableName("backup_versions") + " (version_name, description, is_manual, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, version.getVersionName());
            pstmt.setString(2, version.getDescription());
            pstmt.setInt(3, dialect.booleanToSql(version.isManual()));
            pstmt.setInt(4, version.getStatus().getCode());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    @Override
    public void updateVersionStats(int versionId, int fileCount, long totalSize) throws SQLException {
        String sql = "UPDATE " + tableName("backup_versions") + " SET file_count = ?, total_size = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, fileCount);
            pstmt.setLong(2, totalSize);
            pstmt.setInt(3, versionId);
            pstmt.executeUpdate();
        }
    }

    @Override
    public void updateVersionStatus(int versionId, VersionStatus status) throws SQLException {
        String sql = "UPDATE " + tableName("backup_versions") + " SET status = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, status.getCode());
            pstmt.setInt(2, versionId);
            pstmt.executeUpdate();
        }
    }

    @Override
    public VersionInfo getVersion(int id) throws SQLException {
        String sql = "SELECT * FROM " + tableName("backup_versions") + " WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapVersion(rs);
                }
            }
        }
        return null;
    }

    @Override
    public VersionInfo getVersionByName(String versionName) throws SQLException {
        String sql = "SELECT * FROM " + tableName("backup_versions") + " WHERE version_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, versionName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapVersion(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<VersionInfo> getAllVersions() throws SQLException {
        List<VersionInfo> versions = new ArrayList<>();
        String sql = "SELECT * FROM " + tableName("backup_versions") + " ORDER BY timestamp DESC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                versions.add(mapVersion(rs));
            }
        }
        return versions;
    }

    @Override
    public VersionInfo getLatestVersion() throws SQLException {
        String sql = "SELECT * FROM " + tableName("backup_versions") + " ORDER BY timestamp DESC LIMIT 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return mapVersion(rs);
            }
        }
        return null;
    }

    @Override
    public void deleteVersion(int id) throws SQLException {
        String sql = "DELETE FROM " + tableName("backup_versions") + " WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    @Override
    public boolean isVersionFullyStored(int versionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName("file_info") + " fi " +
            "JOIN " + tableName("blobs") + " b ON fi.blob_key = b.blob_key " +
            "WHERE fi.version_id = ? AND b.state != ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, versionId);
            pstmt.setInt(2, BlobState.STORED.getCode());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0;
                }
            }
        }
        return true;
    }

    @Override
    public void refreshVersionCompletion(int versionId) throws SQLException {
        if (isVersionFullyStored(versionId)) {
            updateVersionStatus(versionId, VersionStatus.COMPLETED);
        } else {
            updateVersionStatus(versionId, VersionStatus.IN_PROGRESS);
        }
    }

    @Override
    public List<Integer> getVersionIdsUpTo(int versionId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        boolean capture = false;
        for (VersionInfo version : getAllVersions()) {
            if (version.getId() == versionId) {
                capture = true;
            }
            if (capture) {
                ids.add(version.getId());
            }
        }
        return ids;
    }

    // ========== 文件 manifest ==========

    @Override
    public void insertFiles(List<FileInfo> files) throws SQLException {
        String sql = "INSERT INTO " + tableName("file_info") + " (version_id, file_path, file_hash, file_size, is_chunk, blob_key) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (FileInfo file : files) {
                pstmt.setInt(1, file.getVersionId());
                pstmt.setString(2, file.getFilePath());
                pstmt.setString(3, file.getFileHash());
                pstmt.setLong(4, file.getFileSize());
                pstmt.setInt(5, dialect.booleanToSql(file.isChunk()));
                pstmt.setString(6, file.getBlobKey());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    @Override
    public List<FileInfo> getFilesByVersionId(int versionId) throws SQLException {
        List<FileInfo> files = new ArrayList<>();
        String sql = "SELECT * FROM " + tableName("file_info") + " WHERE version_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, versionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapFile(rs));
                }
            }
        }
        return files;
    }

    @Override
    public void deleteFilesByVersionId(int versionId) throws SQLException {
        String sql = "DELETE FROM " + tableName("file_info") + " WHERE version_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, versionId);
            pstmt.executeUpdate();
        }
    }

    @Override
    public List<FileInfo> getFilesByBlobKeys(List<String> blobKeys) throws SQLException {
        if (blobKeys.isEmpty()) {
            return new ArrayList<>();
        }
        List<FileInfo> files = new ArrayList<>();
        String placeholders = String.join(",", blobKeys.stream().map(k -> "?").collect(Collectors.toList()));
        String sql = "SELECT * FROM " + tableName("file_info") + " WHERE blob_key IN (" + placeholders + ")";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < blobKeys.size(); i++) {
                pstmt.setString(i + 1, blobKeys.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapFile(rs));
                }
            }
        }
        return files;
    }

    // ========== Blob 操作 ==========

    @Override
    public void upsertBlob(BlobInfo blob) throws SQLException {
        String sql = dialect.getUpsertBlobSql(tableName("blobs"));
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, blob.getBlobKey());
            pstmt.setString(2, blob.getFilePath());
            pstmt.setString(3, blob.getFileHash());
            pstmt.setLong(4, blob.getFileSize());
            pstmt.setInt(5, dialect.booleanToSql(blob.isChunk()));
            pstmt.setInt(6, blob.getState().getCode());
            pstmt.setLong(7, blob.getCompressedSize());
            pstmt.executeUpdate();
        }
    }

    @Override
    public BlobInfo getBlob(String blobKey) throws SQLException {
        String sql = "SELECT * FROM " + tableName("blobs") + " WHERE blob_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, blobKey);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapBlob(rs);
                }
            }
        }
        return null;
    }

    @Override
    public void updateBlobState(String blobKey, BlobState state, long compressedSize) throws SQLException {
        String sql = "UPDATE " + tableName("blobs") + " SET state = ?, compressed_size = ? WHERE blob_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, state.getCode());
            pstmt.setLong(2, compressedSize);
            pstmt.setString(3, blobKey);
            pstmt.executeUpdate();
        }
    }

    @Override
    public List<BlobInfo> getBlobsByState(BlobState state) throws SQLException {
        List<BlobInfo> blobs = new ArrayList<>();
        String sql = "SELECT * FROM " + tableName("blobs") + " WHERE state = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, state.getCode());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    blobs.add(mapBlob(rs));
                }
            }
        }
        return blobs;
    }

    @Override
    public int countBlobsByState(BlobState state) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName("blobs") + " WHERE state = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, state.getCode());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<BlobInfo> getPendingBlobsForVersions(List<Integer> versionIds) throws SQLException {
        if (versionIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<BlobInfo> blobs = new ArrayList<>();
        String placeholders = String.join(",", versionIds.stream().map(id -> "?").collect(Collectors.toList()));
        String sql = "SELECT DISTINCT b.* FROM " + tableName("blobs") + " b " +
            "JOIN " + tableName("file_info") + " fi ON fi.blob_key = b.blob_key " +
            "WHERE fi.version_id IN (" + placeholders + ") AND b.state = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            int idx = 1;
            for (int versionId : versionIds) {
                pstmt.setInt(idx++, versionId);
            }
            pstmt.setInt(idx, BlobState.PENDING.getCode());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    blobs.add(mapBlob(rs));
                }
            }
        }
        return blobs;
    }

    @Override
    public int getBlobReferenceCount(String blobKey) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName("file_info") + " WHERE blob_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, blobKey);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public void deleteBlob(String blobKey) throws SQLException {
        String sql = "DELETE FROM " + tableName("blobs") + " WHERE blob_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, blobKey);
            pstmt.executeUpdate();
        }
    }

    @Override
    public List<BlobInfo> gcUnreferencedBlobs() throws SQLException {
        List<BlobInfo> removed = new ArrayList<>();
        List<BlobInfo> allBlobs = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName("blobs"))) {
            while (rs.next()) {
                allBlobs.add(mapBlob(rs));
            }
        }
        for (BlobInfo blob : allBlobs) {
            if (getBlobReferenceCount(blob.getBlobKey()) == 0) {
                deleteBlob(blob.getBlobKey());
                removed.add(blob);
            }
        }
        return removed;
    }

    @Override
    public void clearAllBlobs() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM " + tableName("blobs"));
        }
    }

    // ========== ResultSet 映射 ==========

    protected VersionInfo mapVersion(ResultSet rs) throws SQLException {
        VersionInfo version = new VersionInfo();
        version.setId(rs.getInt("id"));
        version.setVersionName(rs.getString("version_name"));
        version.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
        version.setDescription(rs.getString("description"));
        version.setFileCount(rs.getInt("file_count"));
        version.setTotalSize(rs.getLong("total_size"));
        version.setManual(dialect.sqlToBoolean(rs.getInt("is_manual")));
        version.setStatus(VersionStatus.fromCode(rs.getInt("status")));
        return version;
    }

    protected FileInfo mapFile(ResultSet rs) throws SQLException {
        FileInfo file = new FileInfo();
        file.setId(rs.getInt("id"));
        file.setVersionId(rs.getInt("version_id"));
        file.setFilePath(rs.getString("file_path"));
        file.setFileHash(rs.getString("file_hash"));
        file.setFileSize(rs.getLong("file_size"));
        file.setChunk(dialect.sqlToBoolean(rs.getInt("is_chunk")));
        file.setBlobKey(rs.getString("blob_key"));
        return file;
    }

    protected BlobInfo mapBlob(ResultSet rs) throws SQLException {
        BlobInfo blob = new BlobInfo();
        blob.setBlobKey(rs.getString("blob_key"));
        blob.setFilePath(rs.getString("file_path"));
        blob.setFileHash(rs.getString("file_hash"));
        blob.setFileSize(rs.getLong("file_size"));
        blob.setChunk(dialect.sqlToBoolean(rs.getInt("is_chunk")));
        blob.setState(BlobState.fromCode(rs.getInt("state")));
        blob.setCompressedSize(rs.getLong("compressed_size"));
        return blob;
    }
}
