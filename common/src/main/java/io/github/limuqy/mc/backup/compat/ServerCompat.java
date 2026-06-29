package io.github.limuqy.mc.backup.compat;

import net.minecraft.server.MinecraftServer;

/**
 * 跨版本 MinecraftServer API 兼容层。
 */
public final class ServerCompat {
    private ServerCompat() {
    }

    public static void saveEverything(MinecraftServer server, boolean flush, boolean force, boolean logSuccess) {
#if MC_VER < MC_1_18_2
        server.saveAllChunks(flush, force, logSuccess);
#else
        server.saveEverything(flush, force, logSuccess);
#endif
    }
}
