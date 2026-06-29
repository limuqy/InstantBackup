package io.github.limuqy.mc.backup.compat;

import net.minecraft.commands.CommandSourceStack;

/**
 * 跨版本命令权限 API 兼容层。
 */
public final class PermissionCompat {
    private PermissionCompat() {
    }

    public static boolean hasCommandPermission(CommandSourceStack source, int level) {
#if MC_VER < MC_1_18_2
        return source.hasPermission(level);
#elif MC_VER < MC_1_20_1
        return source.hasPermissionLevel(level);
#else
        return source.hasPermission(level);
#endif
    }
}
