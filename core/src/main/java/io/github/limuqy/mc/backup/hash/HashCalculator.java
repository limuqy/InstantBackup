package io.github.limuqy.mc.backup.hash;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class HashCalculator {
    private static final int CHUNK_READ_SIZE = 8192; // 8KB，.mca 头部双表
    private static final int BUFFER_SIZE = 65536;
    private static final net.jpountz.xxhash.XXHashFactory XX = net.jpountz.xxhash.XXHashFactory.fastestInstance();

    /**
     * 构造 blob 唯一键：path|hash|size
     */
    public static String makeBlobKey(String filePath, String fileHash, long fileSize) {
        return filePath + "|" + fileHash + "|" + fileSize;
    }

    /**
     * 计算文件 hash（区块可选 8KB 或全量）
     */
    public static String calculateFileHash(Path filePath, boolean isChunk, boolean fullChunkHash) throws IOException {
        if (isChunk && !fullChunkHash) {
            return calculateChunkHash(filePath);
        }
        return calculateHash(filePath);
    }

    /**
     * 计算文件的完整 XXHash 哈希
     */
    public static String calculateHash(Path filePath) throws IOException {
        long fileSize = Files.size(filePath);
        if (fileSize == 0) {
            return Long.toHexString(0L);
        }

        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        net.jpountz.xxhash.StreamingXXHash64 hasher = XX.newStreamingHash64(0L);
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r");
             FileChannel channel = raf.getChannel()) {
            while (channel.read(buffer) != -1) {
                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                hasher.update(bytes, 0, bytes.length);
                buffer.clear();
            }
        }
        return Long.toHexString(hasher.getValue());
    }

    /**
     * 计算区块文件的前 8KB XXHash 哈希
     */
    public static String calculateChunkHash(Path filePath) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(CHUNK_READ_SIZE);
        net.jpountz.xxhash.StreamingXXHash64 hasher = XX.newStreamingHash64(0L);
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r");
             FileChannel channel = raf.getChannel()) {
            int bytesRead = channel.read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                hasher.update(bytes, 0, bytes.length);
            }
        }
        return Long.toHexString(hasher.getValue());
    }

    /**
     * 判断文件是否为区块 region 文件
     */
    public static boolean isChunkFile(String fileName) {
        return fileName.endsWith(".mca");
    }
}
