package io.github.limuqy.mc.backup.database;

import java.time.LocalDateTime;

public class VersionInfo {
    private int id;
    private String versionName;
    private LocalDateTime timestamp;
    private String description;
    private int fileCount;
    private long totalSize;
    private boolean isManual;
    private VersionStatus status = VersionStatus.IN_PROGRESS;

    public VersionInfo() {
    }

    public VersionInfo(String versionName, String description, boolean isManual) {
        this.versionName = versionName;
        this.description = description;
        this.isManual = isManual;
        this.timestamp = LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public boolean isManual() {
        return isManual;
    }

    public void setManual(boolean manual) {
        isManual = manual;
    }

    public VersionStatus getStatus() {
        return status;
    }

    public void setStatus(VersionStatus status) {
        this.status = status;
    }

    public String getDisplayDescription() {
        return description != null && !description.isEmpty() ? description : "-";
    }
}
