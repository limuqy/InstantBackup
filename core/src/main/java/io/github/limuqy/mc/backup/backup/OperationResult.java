package io.github.limuqy.mc.backup.backup;

/**
 * 备份操作结果（Mod 命令与 CLI 共用）。
 */
public final class OperationResult {
    private final boolean success;
    private final String langKey;
    private final Object[] args;

    private OperationResult(boolean success, String langKey, Object... args) {
        this.success = success;
        this.langKey = langKey;
        this.args = args;
    }

    public static OperationResult ok(String langKey, Object... args) {
        return new OperationResult(true, langKey, args);
    }

    public static OperationResult fail(String langKey, Object... args) {
        return new OperationResult(false, langKey, args);
    }

    public boolean success() {
        return success;
    }

    public String langKey() {
        return langKey;
    }

    public Object[] args() {
        return args;
    }
}
