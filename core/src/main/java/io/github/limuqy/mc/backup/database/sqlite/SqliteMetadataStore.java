package io.github.limuqy.mc.backup.database.sqlite;

import io.github.limuqy.mc.backup.database.jdbc.AbstractJdbcMetadataStore;
import io.github.limuqy.mc.backup.database.jdbc.SqlDialect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * SQLite 元数据存储实现
 */
public class SqliteMetadataStore extends AbstractJdbcMetadataStore {
    private Path dbPath;

    public SqliteMetadataStore() {
        super(SqlDialect.SQLITE);
    }

    @Override
    protected Connection createConnection(Path configDir) throws Exception {
        this.dbPath = configDir.resolve("backups.db");
        Files.createDirectories(configDir);
        Connection conn = createSqliteConnection("jdbc:sqlite:" + dbPath.toString());
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    private Connection createSqliteConnection(String url) throws Exception {
        try {
            return DriverManager.getConnection(url);
        } catch (SQLException e) {
            Class<?> driverClass = loadSqliteDriver();
            java.sql.Driver driver = (java.sql.Driver) driverClass.getDeclaredConstructor().newInstance();
            return driver.connect(url, new java.util.Properties());
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

    @Override
    protected void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS schema_meta (" +
                "    version INTEGER NOT NULL" +
                ")"
            );
            insertSchemaVersion();

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

    @Override
    protected boolean tableExists(String tableName) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    @Override
    protected void recreateSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS file_info");
            stmt.execute("DROP TABLE IF EXISTS blobs");
            stmt.execute("DROP TABLE IF EXISTS backup_versions");
            stmt.execute("DROP TABLE IF EXISTS schema_meta");
        }
        createTables();
    }
}
