package io.github.limuqy.mc.backup;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.limuqy.mc.backup.command.BackupCommandHandler;
import io.github.limuqy.mc.backup.compat.ChatCompat;
import io.github.limuqy.mc.backup.compat.CommandCompat;
import io.github.limuqy.mc.backup.compat.PermissionCompat;
import io.github.limuqy.mc.backup.i18n.LangKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import io.github.limuqy.mc.backup.compat.ModLog;

public class CommandRegistry {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("backup")
            .requires(source -> PermissionCompat.hasCommandPermission(source, 2)) // 需要 OP 权限
            .then(Commands.literal("help")
                .executes(context -> executeCommand(context, "help")))
            .then(Commands.literal("create")
                .executes(context -> executeCommand(context, "create"))
                .then(Commands.argument("description", StringArgumentType.greedyString())
                    .executes(context -> {
                        String description = StringArgumentType.getString(context, "description");
                        return executeCommand(context, "create", description);
                    })))
            .then(Commands.literal("list")
                .executes(context -> executeCommand(context, "list")))
            .then(Commands.literal("delete")
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int index = IntegerArgumentType.getInteger(context, "index");
                        return executeCommand(context, "delete", String.valueOf(index));
                    })))
            .then(Commands.literal("export")
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int index = IntegerArgumentType.getInteger(context, "index");
                        return executeCommand(context, "export", String.valueOf(index));
                    })))
            .then(Commands.literal("migrate")
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int index = IntegerArgumentType.getInteger(context, "index");
                        return executeCommand(context, "migrate", String.valueOf(index));
                    })))
            .then(Commands.literal("status")
                .executes(context -> executeCommand(context, "status")))
            .then(Commands.literal("config")
                .executes(context -> executeCommand(context, "config"))
                .then(Commands.argument("key", StringArgumentType.word())
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(context -> {
                            String key = StringArgumentType.getString(context, "key");
                            String value = StringArgumentType.getString(context, "value");
                            return executeCommand(context, "config", key, value);
                        }))))
            .then(Commands.literal("clean")
                .executes(context -> executeCommand(context, "clean")))
        );
    }

    private static int executeCommand(CommandContext<CommandSourceStack> context, String... args) {
        try {
            CommandSourceStack source = context.getSource();
            Component result = BackupCommandHandler.execute(args);
            CommandCompat.sendSuccess(source, result);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ModLog.error("[Instant Backup] 命令执行失败", e);
            CommandCompat.sendFailure(context.getSource(), ChatCompat.translatable(LangKeys.COMMAND_EXEC_FAILED, e.getMessage()));
            return 0;
        }
    }
}
