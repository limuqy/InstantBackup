package io.github.limuqy.mc.backup;

import net.fabricmc.api.ModInitializer;
#if MC_VER <= MC_1_18_2
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
#else
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
#endif
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Fabric 加载器入口点
 * 使用 fabric-api 的生命周期事件管理服务器启动/停止
 */
public class ExampleModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ExampleMod.initializeForServer(new FabricLoaderHelper());

#if MC_VER <= MC_1_18_2
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
            ExampleMod.mod().getLoaderHelper().registerCommand(dispatcher));
#else
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            ExampleMod.mod().getLoaderHelper().registerCommand(dispatcher));
#endif

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ExampleMod.mod().onServerStarting(server);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (Boolean.getBoolean("instantbackup.selftest")) {
                io.github.limuqy.mc.backup.test.BackupSelfTest.run(ExampleMod.mod().getBackupPath());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ExampleMod.mod().onServerStopping();
        });

        System.out.println("[Instant Backup] Fabric 模组已加载");
    }
}
