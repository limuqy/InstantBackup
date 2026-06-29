package io.github.limuqy.mc.backup.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 备份路径解析工具（无 Minecraft 依赖，Mod 与 CLI 共用）。
 */
public final class BackupPaths {
    private BackupPaths() {
    }

    /**
     * 将配置中的 storage.path 解析为实际备份根目录。
     * 绝对路径直接使用；相对路径相对于服务器根目录。
     */
    public static Path resolveBackupPath(Path serverRoot, String configuredPath) {
        Path path = Paths.get(configuredPath.trim());
        return path.isAbsolute()
            ? path.normalize()
            : serverRoot.resolve(path).normalize();
    }
}
