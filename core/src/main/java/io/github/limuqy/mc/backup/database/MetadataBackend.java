package io.github.limuqy.mc.backup.database;

/**
 * 元数据存储后端类型
 */
public enum MetadataBackend {
    CSV("csv"),
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String configValue;

    MetadataBackend(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigValue() {
        return configValue;
    }

    /**
     * 从配置字符串解析后端类型，非法值回退到 CSV
     */
    public static MetadataBackend fromConfig(String value) {
        if (value == null || value.trim().isEmpty()) {
            return CSV;
        }
        String normalized = value.trim().toLowerCase();
        for (MetadataBackend backend : values()) {
            if (backend.configValue.equals(normalized)) {
                return backend;
            }
        }
        return CSV;
    }
}
