package io.github.limuqy.mc.backup.database;

/**
 * 备份版本状态
 */
public enum VersionStatus {
    /** 仍有 blob 未完成迁移/压缩 */
    IN_PROGRESS(0),
    /** 所有 blob 均已 STORED */
    COMPLETED(1);

    private final int code;

    VersionStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static VersionStatus fromCode(int code) {
        for (VersionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知版本状态: " + code);
    }
}
