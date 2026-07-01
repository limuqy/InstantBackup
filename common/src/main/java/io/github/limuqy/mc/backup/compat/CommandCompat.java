package io.github.limuqy.mc.backup.compat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * 跨版本命令反馈 API 兼容层。
 */
public final class CommandCompat {
    private CommandCompat() {
    }

    public static void sendSuccess(CommandSourceStack source, Component message) {
#if MC_VER <= MC_1_19_4
        source.sendSuccess(message, false);
#else
        source.sendSuccess(() -> message, false);
#endif
    }

    public static void sendFailure(CommandSourceStack source, Component message) {
        source.sendFailure(message);
    }
}
