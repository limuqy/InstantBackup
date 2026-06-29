package io.github.limuqy.mc.backup.database;

import io.github.limuqy.mc.backup.hash.HashCalculator;

/**
 * 共享 blob 元数据，key = path|hash|size
 */
public class BlobInfo {
    private String blobKey;
    private String filePath;
    private String fileHash;
    private long fileSize;
    private boolean chunk;
    private BlobState state;
    private long compressedSize;

    public BlobInfo() {
    }

    public BlobInfo(String filePath, String fileHash, long fileSize, boolean chunk, BlobState state) {
        this.filePath = filePath;
        this.fileHash = fileHash;
        this.fileSize = fileSize;
        this.chunk = chunk;
        this.state = state;
        this.blobKey = HashCalculator.makeBlobKey(filePath, fileHash, fileSize);
    }

    public String getBlobKey() {
        return blobKey;
    }

    public void setBlobKey(String blobKey) {
        this.blobKey = blobKey;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isChunk() {
        return chunk;
    }

    public void setChunk(boolean chunk) {
        this.chunk = chunk;
    }

    public BlobState getState() {
        return state;
    }

    public void setState(BlobState state) {
        this.state = state;
    }

    public long getCompressedSize() {
        return compressedSize;
    }

    public void setCompressedSize(long compressedSize) {
        this.compressedSize = compressedSize;
    }
}
