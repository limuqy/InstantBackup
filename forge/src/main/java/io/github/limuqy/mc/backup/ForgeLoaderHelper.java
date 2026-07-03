package io.github.limuqy.mc.backup;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Forge 平台辅助器实现
 */
public class ForgeLoaderHelper implements LoaderHelper {

    @Override
    public String getModVersion() {
#if MC_VER < MC_26_2
        return ModList.get().getModContainerById(ExampleMod.MOD_ID)
#else
        return ModList.getModContainerById(ExampleMod.MOD_ID)
#endif
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve(ExampleMod.MOD_ID);
    }

    @Override
    public Path getModRootPath() {
#if MC_VER < MC_26_2
        return ModList.get().getModContainerById(ExampleMod.MOD_ID)
#else
        return ModList.getModContainerById(ExampleMod.MOD_ID)
#endif
            .map(container -> resolveModRootPath(container.getModInfo().getOwningFile().getFile().getFilePath()))
            .orElseThrow(() -> new IllegalStateException("找不到 Instant Backup mod 容器"));
    }

    /**
     * 解析 mod 根路径：生产环境优先 JAR，开发环境将 resources/main 回退到 classes/java/main。
     */
    private static Path resolveModRootPath(Path primary) {
        Path classesSibling = toClassesSibling(primary);

        if (isModJar(primary)) {
            return primary;
        }
        if (isClassesOutputDir(primary)) {
            return primary;
        }
        if (classesSibling != null) {
            return classesSibling;
        }
        return primary;
    }

    private static Path toClassesSibling(Path path) {
        if (!Files.isDirectory(path)) {
            return null;
        }
        String normalized = path.toString().replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);
        int index = lower.indexOf("/resources/main");
        if (index < 0) {
            return null;
        }
        Path candidate = Paths.get(normalized.substring(0, index), "classes", "java", "main");
        return Files.isDirectory(candidate) ? candidate : null;
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
    public List<Path> getModClasspath() {
        Path modPath = getModRootPath();
        if (isModJar(modPath)) {
            return List.of(modPath);
        }
        // Dev 模式：添加 core 模块的 classes 目录
        List<Path> paths = new java.util.ArrayList<>();
        paths.add(modPath);
        Path coreClasses = modPath.resolve("../../../core/build/classes/java/main").normalize();
        if (Files.isDirectory(coreClasses)) {
            paths.add(coreClasses);
        }
        return paths;
    }

    @Override
    public void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandRegistry.register(dispatcher);
    }

    @Override
    public List<String> getModList() {
#if MC_VER < MC_26_2
        return ModList.get().getMods().stream()
#else
        return ModList.getMods().stream()
#endif
                .map(IModInfo::getModId)
                .collect(Collectors.toList());
    }
}
