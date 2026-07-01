package io.github.limuqy.mc.backup.script;

import io.github.limuqy.mc.backup.config.BackupConfig;
import io.github.limuqy.mc.backup.compat.ModLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * 在配置目录生成一键启动脚本，复用 mod JAR 运行交互式 CLI。
 */
public final class ScriptGenerator {
    private static final String CMD_FILE = "InstantBackup.cmd";
    private static final String SH_FILE = "InstantBackup.sh";
    private static final String MAIN_CLASS = "io.github.limuqy.mc.backup.cli.CliMain";

    private ScriptGenerator() {
    }

    /**
     * 生成 Windows cmd 与 Unix shell 脚本（若 script.enabled 为 true）。
     */
    public static void generate(Path configDir, Path modRootPath, Path serverDir) {
        if (!BackupConfig.isScriptEnabled()) {
            return;
        }

        try {
            Files.createDirectories(configDir);
            String javaBinary = resolveJavaBinary();
            String modPath = modRootPath.toAbsolutePath().normalize().toString();
            String serverPath = serverDir.toAbsolutePath().normalize().toString();

            Path cmdPath = configDir.resolve(CMD_FILE);
            Files.write(cmdPath, buildCmdScript(javaBinary, modPath, serverPath).getBytes(StandardCharsets.UTF_8));

            Path shPath = configDir.resolve(SH_FILE);
            Files.write(shPath, buildShScript(javaBinary, modPath, serverPath).getBytes(StandardCharsets.UTF_8));
            makeExecutable(shPath);

            ModLog.info("[Instant Backup] 已生成一键脚本: {}, {}", cmdPath, shPath);
        } catch (IOException e) {
            ModLog.warn("[Instant Backup] 生成一键脚本失败: {}", e.getMessage());
        }
    }

    private static String resolveJavaBinary() {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path javaExe = javaHome.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(javaExe)) {
            return javaExe.toAbsolutePath().normalize().toString();
        }
        return "java";
    }

    private static String buildCmdScript(String javaBinary, String modPath, String serverDir) {
        return "@echo off\n"
            + "chcp 65001 >nul\n"
            + "title 极速备份\n"
            + "\"" + escapeCmd(javaBinary) + "\" -cp \"" + escapeCmd(modPath) + "\" " + MAIN_CLASS + " --server-dir \"" + escapeCmd(serverDir) + "\" --interactive\n"
            + "pause\n";
    }

    private static String buildShScript(String javaBinary, String modPath, String serverDir) {
        return "#!/usr/bin/env bash\n"
            + "exec '" + escapeSh(javaBinary) + "' -cp '" + escapeSh(modPath) + "' " + MAIN_CLASS + " --server-dir '" + escapeSh(serverDir) + "' --interactive\n";
    }

    private static String escapeCmd(String value) {
        return value.replace("\"", "\"\"");
    }

    private static String escapeSh(String value) {
        return value.replace("'", "'\\''");
    }

    private static void makeExecutable(Path shPath) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE
            );
            Files.setPosixFilePermissions(shPath, perms);
        } catch (Exception ignored) {
            // Windows 等不支持 POSIX 权限时忽略
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
