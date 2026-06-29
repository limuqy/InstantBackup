package io.github.limuqy.mc.backup.database;

public class FileInfo {
    private int id;
    private int versionId;
    private String filePath;
    private String fileHash;
    private long fileSize;
    private boolean isChunk;
    private String blobKey;

    public FileInfo() {
    }

    public FileInfo(int versionId, String filePath, String fileHash, long fileSize, boolean isChunk) {
        this.versionId = versionId;
        this.filePath = filePath;
        this.fileHash = fileHash;
        this.fileSize = fileSize;
        this.isChunk = isChunk;
        this.blobKey = io.github.limuqy.mc.backup.hash.HashCalculator.makeBlobKey(filePath, fileHash, fileSize);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getVersionId() {
        return versionId;
    }

    public void setVersionId(int versionId) {
        this.versionId = versionId;
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
        return isChunk;
    }

    public void setChunk(boolean chunk) {
        isChunk = chunk;
    }

    public String getBlobKey() {
        return blobKey;
    }

    public void setBlobKey(String blobKey) {
        this.blobKey = blobKey;
    }

    public void refreshBlobKey() {
        this.blobKey = io.github.limuqy.mc.backup.hash.HashCalculator.makeBlobKey(filePath, fileHash, fileSize);
    }
}
