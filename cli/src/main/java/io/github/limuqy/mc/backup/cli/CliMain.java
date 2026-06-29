package io.github.limuqy.mc.backup.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Instant Backup 独立 CLI 入口。
 */
public final class CliMain {
    private CliMain() {
    }

    public static void main(String[] args) {
        CliBootstrap bootstrap = null;
        try {
            ParsedArgs parsed = ParsedArgs.parse(args);
            if (parsed.showHelpOnly()) {
                CliBootstrap.printUsage();
                System.exit(0);
            }

            bootstrap = CliBootstrap.parse(parsed.bootstrapArgs());
            int exitCode;
            if (parsed.commandArgs().length > 0) {
                exitCode = bootstrap.execute(parsed.commandArgs());
            } else {
                exitCode = bootstrap.runInteractive();
            }
            System.exit(exitCode);
        } catch (IllegalArgumentException e) {
            System.err.println("[Instant Backup CLI] " + e.getMessage());
            CliBootstrap.printUsage();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("[Instant Backup CLI] 执行失败: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        } finally {
            if (bootstrap != null) {
                bootstrap.shutdown();
            }
        }
    }

    /**
     * 分离 bootstrap 参数与命令参数。
     */
    private static final class ParsedArgs {
        private final String[] bootstrapArgs;
        private final String[] commandArgs;
        private final boolean helpOnly;

        private ParsedArgs(String[] bootstrapArgs, String[] commandArgs, boolean helpOnly) {
            this.bootstrapArgs = bootstrapArgs;
            this.commandArgs = commandArgs;
            this.helpOnly = helpOnly;
        }

        static ParsedArgs parse(String[] args) {
            if (args.length == 0) {
                return new ParsedArgs(new String[0], new String[0], true);
            }
            if (args.length == 1 && isHelpFlag(args[0])) {
                return new ParsedArgs(new String[0], new String[0], true);
            }

            boolean interactive = false;
            List<String> bootstrap = new ArrayList<>();
            List<String> command = new ArrayList<>();

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (isBootstrapFlag(arg)) {
                    bootstrap.add(arg);
                    if (requiresValue(arg) && i + 1 < args.length) {
                        bootstrap.add(args[++i]);
                    }
                } else {
                    command.add(arg);
                }
            }

            return new ParsedArgs(
                bootstrap.toArray(new String[bootstrap.size()]),
                command.toArray(new String[command.size()]),
                false
            );
        }

        String[] bootstrapArgs() {
            return bootstrapArgs;
        }

        String[] commandArgs() {
            return commandArgs;
        }

        boolean showHelpOnly() {
            return helpOnly;
        }

        private static boolean isHelpFlag(String arg) {
            return "help".equalsIgnoreCase(arg) || "--help".equals(arg) || "-h".equals(arg);
        }

        private static boolean isBootstrapFlag(String arg) {
            return arg.startsWith("--");
        }

        private static boolean requiresValue(String arg) {
            return "--server-dir".equals(arg)
                || "--world-dir".equals(arg)
                || "--config-dir".equals(arg)
                || "--backup-dir".equals(arg);
        }
    }
}
