package io.github.limuqy.mc.backup.mixins;

import io.github.limuqy.mc.backup.ExampleMod;
import io.github.limuqy.mc.backup.backup.BackupManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

#if MC_VER < MC_1_18_2
import java.io.File;
#endif
import java.nio.file.Path;
import java.util.Locale;

/**
 * 区块保存 COW Mixin：在 region 文件被覆盖前复制原始内容到备份区。
 * 1.16 时代 RegionFileStorage.folder 为 File，1.18.2+ 为 Path。
 */
@Mixin(RegionFileStorage.class)
public abstract class ChunkSaveMixin {

#if MC_VER < MC_1_18_2
    @Shadow
    @Final
    private File folder;
#else
    @Shadow
    @Final
    private Path folder;
#endif

    @Inject(method = "write", at = @At("HEAD"))
    private void beforeChunkWrite(ChunkPos pos, CompoundTag tag, CallbackInfo ci) {
#if MC_VER < MC_1_18_2
        onRegionWrite(folder.toPath(), pos);
#else
        onRegionWrite(folder, pos);
#endif
    }

    private static void onRegionWrite(Path folderPath, ChunkPos pos) {
        try {
            Path worldPath = ExampleMod.mod().getWorldPath();
            String regionName = String.format(Locale.ROOT, "r.%d.%d.mca", pos.getRegionX(), pos.getRegionZ());
            Path regionFile = folderPath.resolve(regionName);
            String relativePath = worldPath.relativize(regionFile).toString().replace('\\', '/');
            BackupManager.getInstance().onChunkSave(relativePath);
        } catch (Exception ignored) {
            // 静默处理，不影响正常保存流程
        }
    }
}
