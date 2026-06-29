package io.github.limuqy.mc.backup;

import io.github.limuqy.mc.backup.compat.ModLog;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * NeoForge 加载器入口点
 */
@Mod(ExampleMod.MOD_ID)
public class ExampleModNeoForge {

    public ExampleModNeoForge() {
        ExampleMod.initializeForServer(new NeoForgeLoaderHelper());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
        ModLog.info("[Instant Backup] NeoForge 模组已加载，等待服务器启动...");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ExampleMod.mod().onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (Boolean.getBoolean("instantbackup.selftest")) {
            io.github.limuqy.mc.backup.test.BackupSelfTest.run(ExampleMod.mod().getBackupPath());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ExampleMod.mod().onServerStopping();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ExampleMod.mod().getLoaderHelper().registerCommand(event.getDispatcher());
        ModLog.info("[Instant Backup] 命令已注册 (NeoForge)");
    }
}
