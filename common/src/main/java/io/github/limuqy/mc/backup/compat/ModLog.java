package io.github.limuqy.mc.backup.compat;

/**
 * 跨版本日志 API 兼容层（1.16 使用 Log4j，1.19+ 使用 SLF4J）。
 */
public final class ModLog {
    private ModLog() {
    }

#if MC_VER < MC_1_19_4
    private static final org.apache.logging.log4j.Logger LOG =
        org.apache.logging.log4j.LogManager.getLogger("InstantBackup");
#else
    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger("InstantBackup");
#endif

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
