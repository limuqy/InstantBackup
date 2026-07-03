package io.github.limuqy.mc.backup.compat;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/**
 * 跨版本 MinecraftServer API 兼容层。
 */
public final class ServerCompat {
    private ServerCompat() {
    }

    /** 1.21.1 起 getServerDirectory() 直接返回 Path，旧版返回 File。 */
    public static Path getServerDirectory(MinecraftServer server) {
#if MC_VER <= MC_1_20_6
        return server.getServerDirectory().toPath();
#else
        return server.getServerDirectory();
#endif
    }

    public static void saveEverything(MinecraftServer server, boolean flush, boolean force, boolean logSuccess) {
        server.saveEverything(flush, force, logSuccess);
    }
}
