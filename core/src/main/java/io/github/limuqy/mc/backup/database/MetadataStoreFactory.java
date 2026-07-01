package io.github.limuqy.mc.backup.database;

import io.github.limuqy.mc.backup.database.csv.CsvMetadataStore;
import io.github.limuqy.mc.backup.database.mysql.MysqlMetadataStore;
import io.github.limuqy.mc.backup.database.sqlite.SqliteMetadataStore;

/**
 * 元数据存储工厂，根据后端类型创建对应实现
 */
public class MetadataStoreFactory {

    /**
     * 创建 MetadataStore 实现
     *
     * @param backend 后端类型
     * @return 对应的 MetadataStore 实例
     */
    public static MetadataStore create(MetadataBackend backend) {
        switch (backend) {
            case CSV:
                return new CsvMetadataStore();
            case SQLITE:
                return new SqliteMetadataStore();
            case MYSQL:
                return new MysqlMetadataStore();
            default:
                return new CsvMetadataStore();
        }
    }
}
