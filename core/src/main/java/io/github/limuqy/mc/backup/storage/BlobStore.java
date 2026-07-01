package io.github.limuqy.mc.backup.storage;

import io.github.limuqy.mc.backup.compression.ZstdCompressor;
import io.github.limuqy.mc.backup.config.BackupConfig;
import io.github.limuqy.mc.backup.database.BlobInfo;
import io.github.limuqy.mc.backup.database.BlobState;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 镜像世界目录结构的 blob 物理文件管理
 * 压缩态: data/&lt;相对路径&gt;.&lt;hash&gt;.zst
 * 未压缩 STORED: data/&lt;相对路径&gt;.&lt;hash&gt;
 * 中间态: data/&lt;相对路径&gt;.&lt;hash&gt;（STAGED，等待压缩）
 */
public class BlobStore {
    private final Path dataRoot;

    public BlobStore(Path backupPath) {
        this.dataRoot = backupPath.resolve("data");
    }

    public Path getDataRoot() {
        return dataRoot;
    }

    public Path resolveCompressedPath(String filePath, String fileHash) {
        return dataRoot.resolve(normalizePath(filePath) + "." + fileHash + ".zst");
    }

    public Path resolveRawPath(String filePath, String fileHash) {
        return dataRoot.resolve(normalizePath(filePath) + "." + fileHash);
    }

    /**
     * 流式复制世界文件到 raw 中间路径
     */
    public Path captureToRaw(Path sourceFile, String relativePath, String fileHash) throws IOException {
        Path rawPath = resolveRawPath(relativePath, fileHash);
        Files.createDirectories(rawPath.getParent());
        Files.copy(sourceFile, rawPath, StandardCopyOption.REPLACE_EXISTING);
        long size = Files.size(sourceFile);
        if (Files.size(rawPath) != size) {
            throw new IOException("捕获后大小不一致: " + relativePath);
        }
        return rawPath;
    }

    /**
     * 将 raw 压缩为 .zst 并删除 raw
     */
    public long compressRaw(BlobInfo blob) throws IOException {
        Path rawPath = resolveRawPath(blob.getFilePath(), blob.getFileHash());
        Path compressedPath = resolveCompressedPath(blob.getFilePath(), blob.getFileHash());
        if (!Files.exists(rawPath)) {
            if (Files.exists(compressedPath)) {
                return Files.size(compressedPath);
            }
            throw new IOException("raw 文件不存在: " + rawPath);
        }
        ZstdCompressor.compressToPath(rawPath, compressedPath, BackupConfig.getCompressionLevel());
        long compressedSize = Files.size(compressedPath);
        Files.deleteIfExists(rawPath);
        return compressedSize;
    }

    /**
     * 不压缩：保留 raw 文件，返回存储大小
     */
    public long finalizeWithoutCompression(BlobInfo blob) throws IOException {
        Path rawPath = resolveRawPath(blob.getFilePath(), blob.getFileHash());
        Path compressedPath = resolveCompressedPath(blob.getFilePath(), blob.getFileHash());
        if (Files.exists(rawPath)) {
            return Files.size(rawPath);
        }
        if (Files.exists(compressedPath)) {
            return Files.size(compressedPath);
        }
        throw new IOException("blob 物理文件不存在: " + blob.getFilePath());
    }

    public boolean compressedExists(BlobInfo blob) {
        return Files.exists(resolveCompressedPath(blob.getFilePath(), blob.getFileHash()));
    }

    public boolean rawExists(BlobInfo blob) {
        return Files.exists(resolveRawPath(blob.getFilePath(), blob.getFileHash()));
    }

    /**
     * 将 blob 内容写入目标路径（自动解压或复制 raw）
     */
    public void materializeTo(BlobInfo blob, Path targetFile, Path worldPath) throws IOException {
        Files.createDirectories(targetFile.getParent());
        switch (blob.getState()) {
            case STORED:
                materializeStoredTo(blob, targetFile);
                break;
            case STAGED:
                Files.copy(resolveRawPath(blob.getFilePath(), blob.getFileHash()), targetFile, StandardCopyOption.REPLACE_EXISTING);
                break;
            case PENDING:
                Path worldFile = worldPath.resolve(blob.getFilePath());
                if (Files.exists(worldFile)) {
                    Files.copy(worldFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    throw new IOException("PENDING blob 在世界中不存在: " + blob.getFilePath());
                }
                break;
        }
    }

    public void decompressTo(BlobInfo blob, Path targetFile) throws IOException {
        materializeStoredTo(blob, targetFile);
    }

    /**
     * 打开 blob 原始内容流（用于 export，输出未压缩数据）
     */
    public InputStream openRawContent(BlobInfo blob, Path worldPath) throws IOException {
        switch (blob.getState()) {
            case STORED:
                return openStoredContent(blob);
            case STAGED:
                return Files.newInputStream(resolveRawPath(blob.getFilePath(), blob.getFileHash()));
            case PENDING: {
                Path worldFile = worldPath.resolve(blob.getFilePath());
                return Files.newInputStream(worldFile);
            }
            default:
                throw new IOException("未知 blob 状态: " + blob.getState());
        }
    }

    public void deleteBlobFiles(BlobInfo blob) {
        try {
            Files.deleteIfExists(resolveCompressedPath(blob.getFilePath(), blob.getFileHash()));
            Files.deleteIfExists(resolveRawPath(blob.getFilePath(), blob.getFileHash()));
        } catch (IOException ignored) {
        }
    }

    private void materializeStoredTo(BlobInfo blob, Path targetFile) throws IOException {
        Path compressedPath = resolveCompressedPath(blob.getFilePath(), blob.getFileHash());
        Path rawPath = resolveRawPath(blob.getFilePath(), blob.getFileHash());
        Files.createDirectories(targetFile.getParent());
        if (Files.exists(compressedPath)) {
            try (InputStream input = Files.newInputStream(compressedPath);
                 com.github.luben.zstd.ZstdInputStream zstdInput = new com.github.luben.zstd.ZstdInputStream(input);
                 OutputStream output = Files.newOutputStream(targetFile)) {
                copyStream(zstdInput, output);
            }
        } else if (Files.exists(rawPath)) {
            Files.copy(rawPath, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            throw new IOException("STORED blob 物理文件不存在: " + blob.getFilePath());
        }
    }

    private InputStream openStoredContent(BlobInfo blob) throws IOException {
        Path compressedPath = resolveCompressedPath(blob.getFilePath(), blob.getFileHash());
        if (Files.exists(compressedPath)) {
            InputStream input = Files.newInputStream(compressedPath);
            return new com.github.luben.zstd.ZstdInputStream(input);
        }
        Path rawPath = resolveRawPath(blob.getFilePath(), blob.getFileHash());
        if (Files.exists(rawPath)) {
            return Files.newInputStream(rawPath);
        }
        throw new IOException("STORED blob 物理文件不存在: " + blob.getFilePath());
    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[65536];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/');
    }
}
