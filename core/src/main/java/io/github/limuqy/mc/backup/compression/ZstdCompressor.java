package io.github.limuqy.mc.backup.compression;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdOutputStream;
import com.github.luben.zstd.ZstdInputStream;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class ZstdCompressor {
    private static final int BUFFER_SIZE = 65536; // 64KB buffer

    /**
     * 使用 ZSTD 压缩文件（保持相对路径结构）
     *
     * @param sourceFile 源文件路径
     * @param targetDir 目标目录
     * @param sourceRoot 源文件根目录（用于计算相对路径）
     * @param level 压缩等级
     * @return 压缩后的文件路径
     */
    public static Path compressFile(Path sourceFile, Path targetDir, Path sourceRoot, int level) throws IOException {
        Files.createDirectories(targetDir);

        // 计算相对路径并保持目录结构
        String relativePath = sourceRoot.relativize(sourceFile).toString();
        String compressedFileName = relativePath + ".zst";
        Path targetFile = targetDir.resolve(compressedFileName);

        // 确保目标目录存在
        Files.createDirectories(targetFile.getParent());

        // 使用流式压缩，避免 OOM
        try (InputStream input = Files.newInputStream(sourceFile);
             OutputStream output = Files.newOutputStream(targetFile);
             ZstdOutputStream zstdOutput = new ZstdOutputStream(output, level)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                zstdOutput.write(buffer, 0, bytesRead);
            }
        }

        return targetFile;
    }

    /**
     * 压缩到指定目标路径
     */
    public static void compressToPath(Path sourceFile, Path targetFile, int level) throws IOException {
        Files.createDirectories(targetFile.getParent());
        try (InputStream input = Files.newInputStream(sourceFile);
             OutputStream output = Files.newOutputStream(targetFile);
             ZstdOutputStream zstdOutput = new ZstdOutputStream(output, level)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                zstdOutput.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * 使用 ZSTD 压缩文件（简化版本，保持向后兼容）
     */
    public static Path compressFile(Path sourceFile, Path targetDir, int level) throws IOException {
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(sourceFile.getFileName().toString() + ".zst");

        // 使用流式压缩，避免 OOM
        try (InputStream input = Files.newInputStream(sourceFile);
             OutputStream output = Files.newOutputStream(targetFile);
             ZstdOutputStream zstdOutput = new ZstdOutputStream(output, level)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                zstdOutput.write(buffer, 0, bytesRead);
            }
        }

        return targetFile;
    }

    /**
     * 使用 ZSTD 解压文件到字节数组
     */
    public static byte[] decompressFile(Path compressedFile) throws IOException {
        try (InputStream input = Files.newInputStream(compressedFile);
             ZstdInputStream zstdInput = new ZstdInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = zstdInput.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            return output.toByteArray();
        }
    }

    /**
     * 解压文件到目标路径（保持相对路径结构）
     *
     * @param compressedFile 压缩文件路径
     * @param targetDir 目标目录
     * @param sourceRoot 源文件根目录（用于计算相对路径）
     * @return 解压后的文件路径
     */
    public static Path decompressFile(Path compressedFile, Path targetDir, Path sourceRoot) throws IOException {
        Files.createDirectories(targetDir);

        // 计算相对路径
        String relativePath = sourceRoot.relativize(compressedFile).toString();
        if (relativePath.endsWith(".zst")) {
            relativePath = relativePath.substring(0, relativePath.length() - 4);
        }
        Path targetFile = targetDir.resolve(relativePath);

        // 确保目标目录存在
        Files.createDirectories(targetFile.getParent());

        // 使用流式解压
        try (InputStream input = Files.newInputStream(compressedFile);
             ZstdInputStream zstdInput = new ZstdInputStream(input);
             OutputStream output = Files.newOutputStream(targetFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = zstdInput.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }

        return targetFile;
    }

    /**
     * 解压文件到目标路径（简化版本，保持向后兼容）
     */
    public static Path decompressFile(Path compressedFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        String fileName = compressedFile.getFileName().toString();
        if (fileName.endsWith(".zst")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        Path targetFile = targetDir.resolve(fileName);

        // 使用流式解压
        try (InputStream input = Files.newInputStream(compressedFile);
             ZstdInputStream zstdInput = new ZstdInputStream(input);
             OutputStream output = Files.newOutputStream(targetFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = zstdInput.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }

        return targetFile;
    }

    /**
     * 获取压缩后的大小（不写入文件）
     */
    public static long getCompressedSize(Path sourceFile, int level) throws IOException {
        try (InputStream input = Files.newInputStream(sourceFile);
             ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZstdOutputStream zstdOutput = new ZstdOutputStream(output, level)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                zstdOutput.write(buffer, 0, bytesRead);
            }
            return output.size();
        }
    }
}
