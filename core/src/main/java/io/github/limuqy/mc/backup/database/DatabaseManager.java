package io.github.limuqy.mc.backup.database;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DatabaseManager {
    private static DatabaseManager instance;
    private Connection connection;
    private Path dbPath;
    private static final int SCHEMA_VERSION = 2;

    private DatabaseManager() {
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public boolean init(Path storagePath) {
        this.dbPath = storagePath.resolve("backups.db");
        try {
            java.nio.file.Files.createDirectories(storagePath);
            connection = createConnection("jdbc:sqlite:" + dbPath.toString());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            if (!isSchemaCurrent()) {
                recreateSchema();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isSchemaCurrent() {
        try {
            if (!tableExists("schema_meta")) {
                return false;
            }
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT version FROM schema_meta LIMIT 1")) {
                if (rs.next()) {
                    return rs.getInt("version") >= SCHEMA_VERSION;
                }
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private void recreateSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS file_info");
            stmt.execute("DROP TABLE IF EXISTS blobs");
            stmt.execute("DROP TABLE IF EXISTS backup_versions");
            stmt.execute("DROP TABLE IF EXISTS schema_meta");
        }
        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS schema_meta (" +
                "    version INTEGER NOT NULL" +
                ")"
            );
            stmt.execute("DELETE FROM schema_meta");
            stmt.execute("INSERT INTO schema_meta (version) VALUES (" + SCHEMA_VERSION + ")");

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS backup_versions (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    version_name TEXT UNIQUE NOT NULL," +
                "    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "    description TEXT," +
                "    file_count INTEGER DEFAULT 0," +
                "    total_size BIGINT DEFAULT 0," +
                "    is_manual BOOLEAN DEFAULT 0," +
                "    status INTEGER DEFAULT 0" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS blobs (" +
                "    blob_key TEXT PRIMARY KEY," +
                "    file_path TEXT NOT NULL," +
                "    file_hash TEXT NOT NULL," +
                "    file_size BIGINT NOT NULL," +
                "    is_chunk BOOLEAN DEFAULT 0," +
                "    state INTEGER NOT NULL," +
                "    compressed_size BIGINT DEFAULT 0" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS file_info (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    version_id INTEGER NOT NULL," +
                "    file_path TEXT NOT NULL," +
                "    file_hash TEXT NOT NULL," +
                "    file_size BIGINT NOT NULL," +
                "    is_chunk BOOLEAN DEFAULT 0," +
                "    blob_key TEXT NOT NULL," +
                "    FOREIGN KEY (version_id) REFERENCES backup_versions(id) ON DELETE CASCADE" +
                ")"
            );

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_info_version_id ON file_info(version_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_info_blob_key ON file_info(blob_key)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_blobs_state ON blobs(state)");
        }
    }

    private Class<?> loadSqliteDriver() throws ClassNotFoundException {
        try {
            return Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }
        try {
            return Class.forName("org.sqlite.JDBC", true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException ignored) {
        }
        return Class.forName("org.sqlite.JDBC", true, ClassLoader.getSystemClassLoader());
    }

    private Connection createConnection(String url) throws Exception {
        try {
            return DriverManager.getConnection(url);
        } catch (SQLException e) {
            Class<?> driverClass = loadSqliteDriver();
            java.sql.Driver driver = (java.sql.Driver) driverClass.getDeclaredConstructor().newInstance();
            return driver.connect(url, new java.util.Properties());
        }
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    // ---------- 版本操作 ----------

    public int insertVersion(VersionInfo version) throws SQLException {
        String sql = "INSERT INTO backup_versions (version_name, description, is_manual, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, version.getVersionName());
            pstmt.setString(2, version.getDescription());
            pstmt.setBoolean(3, version.isManual());
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

    public void updateVersionStats(int versionId, int fileCount, long totalSize) throws SQLException {
        String sql = "UPDATE backup_versions SET file_count = ?, total_size = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, fileCount);
            pstmt.setLong(2, totalSize);
            pstmt.setInt(3, versionId);
            pstmt.executeUpdate();
        }
    }

    public void updateVersionStatus(int versionId, VersionStatus status) throws SQLException {
        String sql = "UPDATE backup_versions SET status = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, status.getCode());
            pstmt.setInt(2, versionId);
            pstmt.executeUpdate();
        }
    }

    public VersionInfo getVersion(int id) throws SQLException {
        String sql = "SELECT * FROM backup_versions WHERE id = ?";
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

    public VersionInfo getVersionByName(String versionName) throws SQLException {
        String sql = "SELECT * FROM backup_versions WHERE version_name = ?";
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

    public List<VersionInfo> getAllVersions() throws SQLException {
        List<VersionInfo> versions = new ArrayList<>();
        String sql = "SELECT * FROM backup_versions ORDER BY timestamp DESC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                versions.add(mapVersion(rs));
            }
        }
        return versions;
    }

    public VersionInfo getLatestVersion() throws SQLException {
        String sql = "SELECT * FROM backup_versions ORDER BY timestamp DESC LIMIT 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return mapVersion(rs);
            }
        }
        return null;
    }

    public void deleteVersion(int id) throws SQLException {
        String sql = "DELETE FROM backup_versions WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    /**
     * 检查版本是否所有 blob 均已 STORED
     */
    public boolean isVersionFullyStored(int versionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM file_info fi " +
            "JOIN blobs b ON fi.blob_key = b.blob_key " +
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

    public void refreshVersionCompletion(int versionId) throws SQLException {
        if (isVersionFullyStored(versionId)) {
            updateVersionStatus(versionId, VersionStatus.COMPLETED);
        } else {
            updateVersionStatus(versionId, VersionStatus.IN_PROGRESS);
        }
    }

    // ---------- 文件 manifest ----------

    public void insertFiles(List<FileInfo> files) throws SQLException {
        String sql = "INSERT INTO file_info (version_id, file_path, file_hash, file_size, is_chunk, blob_key) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (FileInfo file : files) {
                pstmt.setInt(1, file.getVersionId());
                pstmt.setString(2, file.getFilePath());
                pstmt.setString(3, file.getFileHash());
                pstmt.setLong(4, file.getFileSize());
                pstmt.setBoolean(5, file.isChunk());
                pstmt.setString(6, file.getBlobKey());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    public List<FileInfo> getFilesByVersionId(int versionId) throws SQLException {
        List<FileInfo> files = new ArrayList<>();
        String sql = "SELECT * FROM file_info WHERE version_id = ?";
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

    public void deleteFilesByVersionId(int versionId) throws SQLException {
        String sql = "DELETE FROM file_info WHERE version_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, versionId);
            pstmt.executeUpdate();
        }
    }

    public List<FileInfo> getFilesByBlobKeys(List<String> blobKeys) throws SQLException {
        if (blobKeys.isEmpty()) {
            return new ArrayList<>();
        }
        List<FileInfo> files = new ArrayList<>();
        String placeholders = String.join(",", blobKeys.stream().map(k -> "?").collect(Collectors.toList()));
        String sql = "SELECT * FROM file_info WHERE blob_key IN (" + placeholders + ")";
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

    // ---------- Blob 操作 ----------

    public void upsertBlob(BlobInfo blob) throws SQLException {
        String sql = "INSERT INTO blobs (blob_key, file_path, file_hash, file_size, is_chunk, state, compressed_size) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(blob_key) DO UPDATE SET state = excluded.state, compressed_size = excluded.compressed_size";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, blob.getBlobKey());
            pstmt.setString(2, blob.getFilePath());
            pstmt.setString(3, blob.getFileHash());
            pstmt.setLong(4, blob.getFileSize());
            pstmt.setBoolean(5, blob.isChunk());
            pstmt.setInt(6, blob.getState().getCode());
            pstmt.setLong(7, blob.getCompressedSize());
            pstmt.executeUpdate();
        }
    }

    public BlobInfo getBlob(String blobKey) throws SQLException {
        String sql = "SELECT * FROM blobs WHERE blob_key = ?";
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

    public void updateBlobState(String blobKey, BlobState state, long compressedSize) throws SQLException {
        String sql = "UPDATE blobs SET state = ?, compressed_size = ? WHERE blob_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, state.getCode());
            pstmt.setLong(2, compressedSize);
            pstmt.setString(3, blobKey);
            pstmt.executeUpdate();
        }
    }

    public List<BlobInfo> getBlobsByState(BlobState state) throws SQLException {
        List<BlobInfo> blobs = new ArrayList<>();
        String sql = "SELECT * FROM blobs WHERE state = ?";
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

    public int countBlobsByState(BlobState state) throws SQLException {
        String sql = "SELECT COUNT(*) FROM blobs WHERE state = ?";
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

    public List<BlobInfo> getPendingBlobsForVersions(List<Integer> versionIds) throws SQLException {
        if (versionIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<BlobInfo> blobs = new ArrayList<>();
        String placeholders = String.join(",", versionIds.stream().map(id -> "?").collect(Collectors.toList()));
        String sql = "SELECT DISTINCT b.* FROM blobs b " +
            "JOIN file_info fi ON fi.blob_key = b.blob_key " +
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

    public int getBlobReferenceCount(String blobKey) throws SQLException {
        String sql = "SELECT COUNT(*) FROM file_info WHERE blob_key = ?";
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

    public void deleteBlob(String blobKey) throws SQLException {
        String sql = "DELETE FROM blobs WHERE blob_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, blobKey);
            pstmt.executeUpdate();
        }
    }

    /**
     * 删除版本后 GC：移除无引用的 blob 记录，返回可删除物理文件的 blob 列表
     */
    public List<BlobInfo> gcUnreferencedBlobs() throws SQLException {
        List<BlobInfo> removed = new ArrayList<>();
        List<BlobInfo> allBlobs = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM blobs")) {
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

    private VersionInfo mapVersion(ResultSet rs) throws SQLException {
        VersionInfo version = new VersionInfo();
        version.setId(rs.getInt("id"));
        version.setVersionName(rs.getString("version_name"));
        version.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
        version.setDescription(rs.getString("description"));
        version.setFileCount(rs.getInt("file_count"));
        version.setTotalSize(rs.getLong("total_size"));
        version.setManual(rs.getBoolean("is_manual"));
        version.setStatus(VersionStatus.fromCode(rs.getInt("status")));
        return version;
    }

    private FileInfo mapFile(ResultSet rs) throws SQLException {
        FileInfo file = new FileInfo();
        file.setId(rs.getInt("id"));
        file.setVersionId(rs.getInt("version_id"));
        file.setFilePath(rs.getString("file_path"));
        file.setFileHash(rs.getString("file_hash"));
        file.setFileSize(rs.getLong("file_size"));
        file.setChunk(rs.getBoolean("is_chunk"));
        file.setBlobKey(rs.getString("blob_key"));
        return file;
    }

    private BlobInfo mapBlob(ResultSet rs) throws SQLException {
        BlobInfo blob = new BlobInfo();
        blob.setBlobKey(rs.getString("blob_key"));
        blob.setFilePath(rs.getString("file_path"));
        blob.setFileHash(rs.getString("file_hash"));
        blob.setFileSize(rs.getLong("file_size"));
        blob.setChunk(rs.getBoolean("is_chunk"));
        blob.setState(BlobState.fromCode(rs.getInt("state")));
        blob.setCompressedSize(rs.getLong("compressed_size"));
        return blob;
    }

    public void clearAllBlobs() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM blobs");
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
