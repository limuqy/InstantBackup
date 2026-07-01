package io.github.limuqy.mc.backup.database.jdbc;

/**
 * SQL 方言枚举，集中维护 SQLite 和 MySQL 的差异
 */
public enum SqlDialect {
    SQLITE,
    MYSQL;

    /**
     * 获取 upsert blob 的 SQL 语句
     */
    public String getUpsertBlobSql(String tableName) {
        switch (this) {
            case SQLITE:
                return "INSERT INTO " + tableName + " (blob_key, file_path, file_hash, file_size, is_chunk, state, compressed_size) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(blob_key) DO UPDATE SET state = excluded.state, compressed_size = excluded.compressed_size";
            case MYSQL:
                return "INSERT INTO " + tableName + " (blob_key, file_path, file_hash, file_size, is_chunk, state, compressed_size) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE state = VALUES(state), compressed_size = VALUES(compressed_size)";
            default:
                throw new IllegalArgumentException("未知方言: " + this);
        }
    }

    /**
     * 获取布尔值的 SQL 表示
     */
    public int booleanToSql(boolean value) {
        return value ? 1 : 0;
    }

    /**
     * 从 SQL 值解析布尔
     */
    public boolean sqlToBoolean(int value) {
        return value != 0;
    }
}
