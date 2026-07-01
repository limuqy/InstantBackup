package io.github.limuqy.mc.backup.compat;

import net.minecraft.commands.CommandSourceStack;
#if MC_VER > MC_1_21_4
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
#endif

/**
 * 跨版本命令权限 API 兼容层。
 */
public final class PermissionCompat {
    private PermissionCompat() {
    }

    public static boolean hasCommandPermission(CommandSourceStack source, int level) {
#if MC_VER > MC_1_21_4
        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
#else
        return source.hasPermission(level);
#endif
    }
}
