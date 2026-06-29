package io.github.limuqy.mc.backup.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 核心模块日志（无 Minecraft / Manifold 依赖）。
 */
public final class ModLog {
    private static final Logger LOG = LoggerFactory.getLogger("InstantBackup");

    private ModLog() {
    }

    public static void debug(String message, Object... args) {
        LOG.debug(message, args);
    }

    public static void info(String message, Object... args) {
        LOG.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOG.warn(message, args);
    }

    public static void error(String message, Object... args) {
        LOG.error(message, args);
    }
}
