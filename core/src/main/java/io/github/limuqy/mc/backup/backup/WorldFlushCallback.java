package io.github.limuqy.mc.backup.backup;

/**
 * 世界刷盘回调（Mod 运行时注入 saveEverything，CLI 离线模式为空操作）。
 */
@FunctionalInterface
public interface WorldFlushCallback {
    void flushWorld() throws Exception;
}
