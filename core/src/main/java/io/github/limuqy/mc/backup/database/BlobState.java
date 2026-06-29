package io.github.limuqy.mc.backup.database;

/**
 * Blob 物理文件状态
 */
public enum BlobState {
    /** 内容仅存在于世界文件，等待 COW 捕获或 migrate */
    PENDING(0),
    /** 已复制为 raw 中间文件，等待压缩 */
    STAGED(1),
    /** 已压缩为 .zst */
    STORED(2);

    private final int code;

    BlobState(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BlobState fromCode(int code) {
        for (BlobState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知 blob 状态: " + code);
    }
}
