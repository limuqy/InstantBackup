package io.github.limuqy.mc.backup;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.commands.CommandSourceStack;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fabric 平台辅助器实现
 */
public class FabricLoaderHelper implements LoaderHelper {

    @Override
    public String getModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(ExampleMod.MOD_ID)
                .map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(ExampleMod.MOD_ID);
    }

    @Override
    public Path getModRootPath() {
        return FabricLoader.getInstance()
            .getModContainer(ExampleMod.MOD_ID)
            .map(ModContainer::getRootPath)
            .orElseThrow(() -> new IllegalStateException("找不到 Instant Backup mod 容器"));
    }

    @Override
    public void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandRegistry.register(dispatcher);
    }

    @Override
    public List<String> getModList() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(mod -> mod.getMetadata().getId())
                .collect(Collectors.toList());
    }
}
