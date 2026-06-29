package io.github.limuqy.mc.backup.mixins;

import io.github.limuqy.mc.backup.scheduler.BackupTickHandler;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * 服务器 tick 钩子：驱动定时备份调度。
 */
@Mixin(MinecraftServer.class)
public abstract class ServerTickMixin {

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void instantbackup$onTickServer(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        try {
            BackupTickHandler.onServerTick((MinecraftServer) (Object) this);
        } catch (Throwable ignored) {
            // 静默失败，不影响服务器 tick
        }
    }
}
