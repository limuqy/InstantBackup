package io.github.limuqy.mc.backup;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.stream.Collectors;

import java.nio.file.Path;
import java.util.List;

/**
 * NeoForge 平台辅助器实现
 */
public class NeoForgeLoaderHelper implements LoaderHelper {

    @Override
    public String getModVersion() {
        return ModList.get()
                .getModContainerById(ExampleMod.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve(ExampleMod.MOD_ID);
    }

    @Override
    public Path getModRootPath() {
        return ModList.get()
            .getModContainerById(ExampleMod.MOD_ID)
            .map(container -> container.getModFile().getFilePath())
            .orElseThrow(() -> new IllegalStateException("找不到 Instant Backup mod 容器"));
    }

    @Override
    public void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandRegistry.register(dispatcher);
    }

    @Override
    public List<String> getModList() {
        return ModList.get().getMods().stream()
                .map(modInfo -> modInfo.getModId())
                .collect(Collectors.toList());
    }
}
