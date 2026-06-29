package io.github.limuqy.mc.backup;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

import java.nio.file.Path;
import java.util.List;

/**
 * 平台抽象接口
 * 各平台（Fabric/Forge/NeoForge）实现此接口，提供平台特定的服务
 */
public interface LoaderHelper {

    /**
     * 获取 mod 版本
     */
    String getModVersion();

    /**
     * 获取配置目录
     */
    Path getConfigDir();

    /**
     * 注册命令
     *
     * @param dispatcher 命令分发器
     */
    void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher);

    /**
     * 获取 mod 根路径（生产环境为 jar 文件，开发环境可能为 classes 目录）
     */
    Path getModRootPath();

    /**
     * 获取已安装的 mod 列表
     *
     * @return mod 名称列表
     */
    List<String> getModList();
}
