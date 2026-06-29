package io.github.limuqy.mc.backup.scheduler;

import io.github.limuqy.mc.backup.ExampleMod;
import io.github.limuqy.mc.backup.compat.CarpetCompat;
import net.minecraft.server.MinecraftServer;

/**
 * 服务器 tick 回调入口，由 {@link io.github.limuqy.mc.backup.mixins.ServerTickMixin} 调用。
 */
public final class BackupTickHandler {
    private BackupTickHandler() {
    }

    public static void onServerTick(MinecraftServer server) {
        try {
            ExampleMod.mod();
            int playerCount = server.getPlayerList().getPlayerCount();
            int carpetBotCount = CarpetCompat.countCarpetBots(server);
            BackupScheduler.getInstance().onServerTick(playerCount, carpetBotCount);
        } catch (Throwable ignored) {
            // 静默失败，不影响服务器 tick
        }
    }
}
