package io.github.limuqy.mc.backup.cli;

import io.github.limuqy.mc.backup.backup.BackupEngine;
import io.github.limuqy.mc.backup.backup.BackupService;
import io.github.limuqy.mc.backup.config.BackupConfig;
import io.github.limuqy.mc.backup.config.BackupPaths;
import io.github.limuqy.mc.backup.database.DatabaseManager;
import io.github.limuqy.mc.backup.i18n.I18n;
import io.github.limuqy.mc.backup.logging.ModLog;
import io.github.limuqy.mc.backup.thread.BackupThreadPool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * CLI 启动与路径解析。
 */
public final class CliBootstrap {
    private static final long DEFAULT_WAIT_SECONDS = 3600L;

    private final Path serverDir;
    private final Path worldDir;
    private final Path configDir;
    private final Path backupDir;
    private final boolean interactive;
    private final BackupService backupService = new BackupService();
    private boolean initialized;

    private CliBootstrap(Path serverDir, Path worldDir, Path configDir, Path backupDir, boolean interactive) {
        this.serverDir = serverDir;
        this.worldDir = worldDir;
        this.configDir = configDir;
        this.backupDir = backupDir;
        this.interactive = interactive;
    }

    public static CliBootstrap parse(String[] args) throws IOException {
        Path serverDir = null;
        Path worldDir = null;
        Path configDir = null;
        Path backupDir = null;
        boolean interactive = true;
        List<String> commandArgs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--server-dir":
                    serverDir = Paths.get(requireValue(args, ++i, arg));
                    break;
                case "--world-dir":
                    worldDir = Paths.get(requireValue(args, ++i, arg));
                    break;
                case "--config-dir":
                    configDir = Paths.get(requireValue(args, ++i, arg));
                    break;
                case "--backup-dir":
                    backupDir = Paths.get(requireValue(args, ++i, arg));
                    break;
                case "--interactive":
                case "-i":
                    interactive = true;
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    commandArgs.add(arg);
                    break;
            }
        }

        if (serverDir == null) {
            throw new IllegalArgumentException("缺少必需参数 --server-dir <路径>");
        }
        serverDir = serverDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(serverDir)) {
            throw new IllegalArgumentException("服务器目录不存在: " + serverDir);
        }

        if (worldDir == null) {
            worldDir = resolveWorldDir(serverDir);
        } else {
            worldDir = worldDir.toAbsolutePath().normalize();
        }
        if (!Files.isDirectory(worldDir)) {
            throw new IllegalArgumentException("世界目录不存在: " + worldDir);
        }

        if (configDir == null) {
            configDir = serverDir.resolve("config").resolve("instantbackup");
        } else {
            configDir = configDir.toAbsolutePath().normalize();
        }

        if (backupDir == null) {
            BackupConfig.init(configDir);
            backupDir = BackupPaths.resolveBackupPath(serverDir, BackupConfig.getStoragePath());
        } else {
            backupDir = backupDir.toAbsolutePath().normalize();
        }

        CliBootstrap bootstrap = new CliBootstrap(serverDir, worldDir, configDir, backupDir, interactive);
        bootstrap.initRuntime();
        return bootstrap;
    }

    public boolean isInteractive() {
        return interactive;
    }

    private void initRuntime() {
        BackupConfig.init(configDir);
        I18n.reload();
        // CLI 离线模式：立即捕获区块，不依赖 Mixin COW
        BackupConfig.setDeferredChunkMigration(false);

        DatabaseManager dbManager = DatabaseManager.getInstance();
        if (!dbManager.init(configDir)) {
            throw new IllegalStateException("数据库初始化失败: " + configDir.resolve("backups.db"));
        }

        BackupEngine.getInstance().init(worldDir, backupDir, () ->
            ModLog.warn("[Instant Backup CLI] 离线模式跳过世界刷盘，请确保服务器已停止"));
        BackupThreadPool.getInstance();
        initialized = true;

        ModLog.info("[Instant Backup CLI] 服务器目录: {}", serverDir);
        ModLog.info("[Instant Backup CLI] 世界目录: {}", worldDir);
        ModLog.info("[Instant Backup CLI] 配置目录: {}", configDir);
        ModLog.info("[Instant Backup CLI] 备份目录: {}", backupDir);
    }

    public int execute(String[] commandArgs) throws InterruptedException {
        if (!initialized) {
            throw new IllegalStateException("CLI 尚未初始化");
        }
        if (commandArgs.length == 0) {
            printUsage();
            return 1;
        }
        return runCommand(commandArgs);
    }

    /**
     * 交互式 REPL，直到用户输入 stop / end / exit / quit。
     */
    public int runInteractive() throws IOException, InterruptedException {
        if (!initialized) {
            throw new IllegalStateException("CLI 尚未初始化");
        }

        printReplBanner();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("> ");
            System.out.flush();
            String line = reader.readLine();
            if (line == null) {
                System.out.println();
                break;
            }
            line = line.trim().replace("\uFEFF", "");
            if (line.isEmpty()) {
                continue;
            }
            if (isExitCommand(line)) {
                System.out.println("再见。");
                return 0;
            }

            String[] commandArgs = tokenizeLine(line);
            int exitCode = runCommand(commandArgs);
            if (exitCode == 2) {
                // create 被 session.lock 拒绝时不退出 REPL
                continue;
            }
        }
        return 0;
    }

    private void printReplBanner() {
        System.out.println("极速备份 CLI（Instant Backup CLI · 服务器: " + serverDir + "）");
        System.out.println("输入 help 查看命令，stop / end / exit / quit 退出");
        System.out.println();
    }

    private static boolean isExitCommand(String line) {
        switch (line.toLowerCase(Locale.ROOT)) {
            case "stop":
            case "end":
            case "exit":
            case "quit":
                return true;
            default:
                return false;
        }
    }

    private int runCommand(String[] commandArgs) throws InterruptedException {
        String command = commandArgs[0].toLowerCase(Locale.ROOT);
        if ("create".equals(command) && Files.exists(worldDir.resolve("session.lock"))) {
            System.err.println("[Instant Backup CLI] 检测到 session.lock，请先停止 Minecraft 服务器再创建备份");
            return 2;
        }

        String output = backupService.execute(commandArgs);
        System.out.println(output);

        if ("create".equals(command) || "export".equals(command) || "clean".equals(command)) {
            backupService.awaitIdle(DEFAULT_WAIT_SECONDS);
        }

        return 0;
    }

    public void shutdown() {
        BackupEngine.getInstance().shutdown();
    }

    private static Path resolveWorldDir(Path serverDir) throws IOException {
        Path propertiesFile = serverDir.resolve("server.properties");
        String levelName = "world";
        if (Files.exists(propertiesFile)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(propertiesFile)) {
                properties.load(input);
            }
            levelName = properties.getProperty("level-name", levelName);
        }
        return serverDir.resolve(levelName).normalize();
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("参数 " + flag + " 缺少值");
        }
        return args[index];
    }

    /**
     * 支持双引号包裹的参数（如 create "离线 备份"）。
     */
    static String[] tokenizeLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens.toArray(new String[]{});
    }

    public static void printUsage() {
        System.out.println(
            "极速备份 CLI（Instant Backup）- 离线备份工具\n"
            + "\n"
            + "用法:\n"
            + "  instantbackup-cli --server-dir <路径> [选项] [命令] [参数]\n"
            + "\n"
            + "说明:\n"
            + "  仅指定 --server-dir 时默认进入交互式 REPL；附带命令则单次执行后退出。\n"
            + "\n"
            + "选项:\n"
            + "  --world-dir         覆盖世界目录\n"
            + "  --config-dir        覆盖配置目录\n"
            + "  --backup-dir        覆盖备份目录\n"
            + "\n"
            + "命令:\n"
            + "  create [备注]     创建备份（要求服务器已停止）\n"
            + "  list              列出备份版本\n"
            + "  delete <序号>     删除版本\n"
            + "  export <序号>     导出 ZIP\n"
            + "  status            查看任务状态\n"
            + "  clean             清空所有备份数据\n"
            + "  help              显示帮助\n"
            + "\n"
            + "示例:\n"
            + "  java -jar instantbackup-cli-all.jar --server-dir D:\\mc-server\n"
            + "  java -jar instantbackup-cli-all.jar --server-dir D:\\mc-server list\n"
            + "\n"
            + "推荐: 使用 config/instantbackup/InstantBackup.cmd 或 InstantBackup.sh 一键启动\n"
        );
    }
}
