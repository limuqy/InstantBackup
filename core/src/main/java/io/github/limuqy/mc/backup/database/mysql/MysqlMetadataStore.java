package io.github.limuqy.mc.backup.database.mysql;

import io.github.limuqy.mc.backup.config.BackupConfig;
import io.github.limuqy.mc.backup.database.jdbc.AbstractJdbcMetadataStore;
import io.github.limuqy.mc.backup.database.jdbc.SqlDialect;

import java.nio.file.Path;
import java.sql.*;

/**
 * MySQL 8 元数据存储实现
 */
public class MysqlMetadataStore extends AbstractJdbcMetadataStore {
    private String tablePrefix;

    public MysqlMetadataStore() {
        super(SqlDialect.MYSQL);
    }

    @Override
    protected String tableName(String logicalName) {
        return tablePrefix + logicalName;
    }

    @Override
    protected Connection createConnection(Path configDir) throws Exception {
        // 加载 MySQL 驱动
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("[Instant Backup] MySQL 驱动未找到。请确认 mysql-connector-j 已正确打包。", e);
        }

        tablePrefix = BackupConfig.getMysqlTablePrefix();
        String host = BackupConfig.getMysqlHost();
        int port = BackupConfig.getMysqlPort();
        String database = BackupConfig.getMysqlDatabase();
        String username = BackupConfig.getMysqlUsername();
        String password = BackupConfig.getMysqlPassword();
        boolean useSsl = BackupConfig.isMysqlUseSsl();
        int timeout = BackupConfig.getMysqlConnectionTimeout();

        String url = String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true&characterEncoding=utf8mb4&serverTimezone=UTC&connectTimeout=%d000",
            host, port, database, useSsl, timeout
        );

        return DriverManager.getConnection(url, username, password);
    }

    @Override
    protected void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "schema_meta (" +
                "    version INT NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            insertSchemaVersion();

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "backup_versions (" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "    version_name VARCHAR(32) NOT NULL UNIQUE," +
                "    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "    description TEXT," +
                "    file_count INT DEFAULT 0," +
                "    total_size BIGINT DEFAULT 0," +
                "    is_manual TINYINT(1) DEFAULT 0," +
                "    status INT DEFAULT 0" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "blobs (" +
                "    blob_key VARCHAR(512) PRIMARY KEY," +
                "    file_path TEXT NOT NULL," +
                "    file_hash VARCHAR(64) NOT NULL," +
                "    file_size BIGINT NOT NULL," +
                "    is_chunk TINYINT(1) DEFAULT 0," +
                "    state INT NOT NULL," +
                "    compressed_size BIGINT DEFAULT 0" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "file_info (" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "    version_id BIGINT NOT NULL," +
                "    file_path TEXT NOT NULL," +
                "    file_hash VARCHAR(64) NOT NULL," +
                "    file_size BIGINT NOT NULL," +
                "    is_chunk TINYINT(1) DEFAULT 0," +
                "    blob_key VARCHAR(512) NOT NULL," +
                "    FOREIGN KEY (version_id) REFERENCES " + tablePrefix + "backup_versions(id) ON DELETE CASCADE," +
                "    INDEX idx_file_info_version_id (version_id)," +
                "    INDEX idx_file_info_blob_key (blob_key(255))" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_blobs_state ON " + tablePrefix + "blobs(state)");
        }
    }

    @Override
    protected boolean tableExists(String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tablePrefix + tableName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    @Override
    protected void recreateSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + tablePrefix + "file_info");
            stmt.execute("DROP TABLE IF EXISTS " + tablePrefix + "blobs");
            stmt.execute("DROP TABLE IF EXISTS " + tablePrefix + "backup_versions");
            stmt.execute("DROP TABLE IF EXISTS " + tablePrefix + "schema_meta");
        }
        createTables();
    }

    @Override
    protected void insertSchemaVersion() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM " + tablePrefix + "schema_meta");
            stmt.execute("INSERT INTO " + tablePrefix + "schema_meta (version) VALUES (" + SCHEMA_VERSION + ")");
        }
    }

    @Override
    protected boolean isSchemaCurrent() {
        try {
            if (!tableExists("schema_meta")) {
                return false;
            }
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT version FROM " + tablePrefix + "schema_meta LIMIT 1")) {
                if (rs.next()) {
                    return rs.getInt("version") >= SCHEMA_VERSION;
                }
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }
}
