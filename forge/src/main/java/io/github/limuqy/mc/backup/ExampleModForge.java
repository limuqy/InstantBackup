package io.github.limuqy.mc.backup;

import io.github.limuqy.mc.backup.compat.ModLog;
import net.minecraftforge.event.RegisterCommandsEvent;
#if MC_VER < MC_1_17_1
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
#elif MC_VER < MC_1_18_2
import net.minecraftforge.fmlserverevents.FMLServerStartedEvent;
import net.minecraftforge.fmlserverevents.FMLServerStartingEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppedEvent;
#else
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
#endif
#if MC_VER > MC_1_21_4
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
#else
import net.minecraftforge.eventbus.api.SubscribeEvent;
#endif
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 加载器入口点
 */
@Mod(ExampleMod.MOD_ID)
public class ExampleModForge {

    public ExampleModForge() {
        ExampleMod.initializeForServer(new ForgeLoaderHelper());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
        ModLog.info("[Instant Backup] Forge 模组已加载，等待服务器启动...");
    }

#if MC_VER < MC_1_18_2
    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        ExampleMod.mod().onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerStarted(FMLServerStartedEvent event) {
        if (Boolean.getBoolean("instantbackup.selftest")) {
            io.github.limuqy.mc.backup.test.BackupSelfTest.run(ExampleMod.mod().getBackupPath());
        }
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppedEvent event) {
        ExampleMod.mod().onServerStopping();
    }
#else
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
#endif

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ExampleMod.mod().getLoaderHelper().registerCommand(event.getDispatcher());
        ModLog.info("[Instant Backup] 命令已注册 (Forge)");
    }
}
