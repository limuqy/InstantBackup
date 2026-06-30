package io.github.limuqy.mc.backup;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.commands.CommandSourceStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
        ModContainer container = FabricLoader.getInstance()
            .getModContainer(ExampleMod.MOD_ID)
            .orElseThrow(() -> new IllegalStateException("找不到 Instant Backup mod 容器"));
        return resolveModRootPath(container);
    }

    /**
     * 从 ModOrigin 路径中选取 mod 根路径：生产环境优先 JAR，开发环境优先 classes 输出目录。
     */
    private static Path resolveModRootPath(ModContainer container) {
        List<Path> paths = container.getOrigin().getPaths();
        if (paths.isEmpty()) {
            throw new IllegalStateException("找不到 Instant Backup mod 路径");
        }

        for (Path path : paths) {
            if (isModJar(path)) {
                return path;
            }
        }

        for (Path path : paths) {
            if (isClassesOutputDir(path)) {
                return path;
            }
        }

        return paths.get(0);
    }

    private static boolean isModJar(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static boolean isClassesOutputDir(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/classes/");
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
