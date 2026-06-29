package io.github.limuqy.mc.backup.compat;

import net.minecraft.server.MinecraftServer;

/**
 * Carpet Mod 假人检测（无 Carpet 编译依赖）。
 * 参考 FabricTPA：通过类名识别 {@code carpet.patches.EntityPlayerMPFake}。
 */
public final class CarpetCompat {
    private static final String CARPET_FAKE_CLASS = "carpet.patches.EntityPlayerMPFake";

    private CarpetCompat() {
    }

    /**
     * 统计当前在线的地毯假人数量。
     */
    public static int countCarpetBots(MinecraftServer server) {
        return (int) server.getPlayerList().getPlayers().stream()
            .filter(player -> CARPET_FAKE_CLASS.equals(player.getClass().getName()))
            .count();
    }
}
