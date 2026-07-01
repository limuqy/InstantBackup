package io.github.limuqy.mc.backup.database.csv;

import io.github.limuqy.mc.backup.database.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * CSV 元数据存储实现
 * <p>
 * 使用内存索引 + 原子写盘策略，线程安全由 ReentrantReadWriteLock 保护
 */
public class CsvMetadataStore implements MetadataStore {
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int SCHEMA_VERSION = 2;

    private Path metadataDir;
    private boolean initialized = false;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // 内存索引
    private final Map<Integer, VersionInfo> versionsById = new LinkedHashMap<>();
    private final Map<String, VersionInfo> versionsByName = new HashMap<>();
    private final Map<String, BlobInfo> blobsByKey = new HashMap<>();
    private final Map<Integer, List<FileInfo>> filesByVersionId = new HashMap<>();
    private final Map<String, List<FileInfo>> filesByBlobKey = new HashMap<>();

    // ID 计数器
    private int nextVersionId = 1;
    private int nextFileInfoId = 1;

    @Override
    public boolean init(Path configDir) {
        try {
            metadataDir = configDir.resolve("metadata");
            Files.createDirectories(metadataDir);

            // 检查 schema 版本
            Path schemaPath = metadataDir.resolve("schema.properties");
            if (Files.exists(schemaPath)) {
                Properties props = new Properties();
                try (var is = Files.newInputStream(schemaPath)) {
                    props.load(is);
                }
                int version = Integer.parseInt(props.getProperty("version", "0"));
                if (version < SCHEMA_VERSION) {
                    // 旧版本，清空重建
                    clearAllCsvFiles();
                }
            }

            // 写入 schema 版本
            saveSchemaVersion();

            // 加载数据到内存
            loadAllData();
            initialized = true;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            initialized = false;
            return false;
        }
    }

    @Override
    public boolean isConnected() {
        return initialized;
    }

    @Override
    public void close() {
        try {
            lock.writeLock().lock();
            try {
                flushAll();
            } finally {
                lock.writeLock().unlock();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        initialized = false;
    }

    // ========== 内部方法 ==========

    private void saveSchemaVersion() throws IOException {
        Properties props = new Properties();
        props.setProperty("version", String.valueOf(SCHEMA_VERSION));
        Path schemaPath = metadataDir.resolve("schema.properties");
        try (var os = Files.newOutputStream(schemaPath)) {
            props.store(os, "InstantBackup Metadata Schema");
        }
    }

    private void clearAllCsvFiles() throws IOException {
        Files.deleteIfExists(metadataDir.resolve("versions.csv"));
        Files.deleteIfExists(metadataDir.resolve("blobs.csv"));
        Files.deleteIfExists(metadataDir.resolve("file_info.csv"));
    }

    private void loadAllData() throws IOException {
        // 加载 versions
        List<String[]> versionRows = CsvTableIO.readAll(metadataDir.resolve("versions.csv"), true);
        for (String[] row : versionRows) {
            VersionInfo v = parseVersion(row);
            versionsById.put(v.getId(), v);
            versionsByName.put(v.getVersionName(), v);
            if (v.getId() >= nextVersionId) {
                nextVersionId = v.getId() + 1;
            }
        }

        // 加载 blobs
        List<String[]> blobRows = CsvTableIO.readAll(metadataDir.resolve("blobs.csv"), true);
        for (String[] row : blobRows) {
            BlobInfo b = parseBlob(row);
            blobsByKey.put(b.getBlobKey(), b);
        }

        // 加载 file_info
        List<String[]> fileRows = CsvTableIO.readAll(metadataDir.resolve("file_info.csv"), true);
        for (String[] row : fileRows) {
            FileInfo f = parseFile(row);
            filesByVersionId.computeIfAbsent(f.getVersionId(), k -> new ArrayList<>()).add(f);
            filesByBlobKey.computeIfAbsent(f.getBlobKey(), k -> new ArrayList<>()).add(f);
            if (f.getId() >= nextFileInfoId) {
                nextFileInfoId = f.getId() + 1;
            }
        }
    }

    private void flushAll() throws IOException {
        // 写 versions.csv
        List<String[]> versionRows = new ArrayList<>();
        for (VersionInfo v : versionsById.values()) {
            versionRows.add(toRow(v));
        }
        CsvTableIO.writeAll(metadataDir.resolve("versions.csv"), CsvTableIO.headerFor("versions"), versionRows);

        // 写 blobs.csv
        List<String[]> blobRows = new ArrayList<>();
        for (BlobInfo b : blobsByKey.values()) {
            blobRows.add(toRow(b));
        }
        CsvTableIO.writeAll(metadataDir.resolve("blobs.csv"), CsvTableIO.headerFor("blobs"), blobRows);

        // 写 file_info.csv
        List<String[]> fileRows = new ArrayList<>();
        for (List<FileInfo> files : filesByVersionId.values()) {
            for (FileInfo f : files) {
                fileRows.add(toRow(f));
            }
        }
        CsvTableIO.writeAll(metadataDir.resolve("file_info.csv"), CsvTableIO.headerFor("file_info"), fileRows);
    }

    private void flushVersions() throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (VersionInfo v : versionsById.values()) {
            rows.add(toRow(v));
        }
        CsvTableIO.writeAll(metadataDir.resolve("versions.csv"), CsvTableIO.headerFor("versions"), rows);
    }

    private void flushBlobs() throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (BlobInfo b : blobsByKey.values()) {
            rows.add(toRow(b));
        }
        CsvTableIO.writeAll(metadataDir.resolve("blobs.csv"), CsvTableIO.headerFor("blobs"), rows);
    }

    private void flushFileInfo() throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (List<FileInfo> files : filesByVersionId.values()) {
            for (FileInfo f : files) {
                rows.add(toRow(f));
            }
        }
        CsvTableIO.writeAll(metadataDir.resolve("file_info.csv"), CsvTableIO.headerFor("file_info"), rows);
    }

    // ========== 行解析 ==========

    private VersionInfo parseVersion(String[] row) {
        VersionInfo v = new VersionInfo();
        v.setId(Integer.parseInt(row[0]));
        v.setVersionName(row[1]);
        v.setTimestamp(LocalDateTime.parse(row[2], TIMESTAMP_FMT));
        v.setDescription(row[3].isEmpty() ? null : row[3]);
        v.setFileCount(Integer.parseInt(row[4]));
        v.setTotalSize(Long.parseLong(row[5]));
        v.setManual("1".equals(row[6]));
        v.setStatus(VersionStatus.fromCode(Integer.parseInt(row[7])));
        return v;
    }

    private BlobInfo parseBlob(String[] row) {
        BlobInfo b = new BlobInfo();
        b.setBlobKey(row[0]);
        b.setFilePath(row[1]);
        b.setFileHash(row[2]);
        b.setFileSize(Long.parseLong(row[3]));
        b.setChunk("1".equals(row[4]));
        b.setState(BlobState.fromCode(Integer.parseInt(row[5])));
        b.setCompressedSize(Long.parseLong(row[6]));
        return b;
    }

    private FileInfo parseFile(String[] row) {
        FileInfo f = new FileInfo();
        f.setId(Integer.parseInt(row[0]));
        f.setVersionId(Integer.parseInt(row[1]));
        f.setFilePath(row[2]);
        f.setFileHash(row[3]);
        f.setFileSize(Long.parseLong(row[4]));
        f.setChunk("1".equals(row[5]));
        f.setBlobKey(row[6]);
        return f;
    }

    // ========== 行序列化 ==========

    private String[] toRow(VersionInfo v) {
        return new String[]{
            String.valueOf(v.getId()),
            v.getVersionName(),
            v.getTimestamp().format(TIMESTAMP_FMT),
            v.getDescription() != null ? v.getDescription() : "",
            String.valueOf(v.getFileCount()),
            String.valueOf(v.getTotalSize()),
            v.isManual() ? "1" : "0",
            String.valueOf(v.getStatus().getCode())
        };
    }

    private String[] toRow(BlobInfo b) {
        return new String[]{
            b.getBlobKey(),
            b.getFilePath(),
            b.getFileHash(),
            String.valueOf(b.getFileSize()),
            b.isChunk() ? "1" : "0",
            String.valueOf(b.getState().getCode()),
            String.valueOf(b.getCompressedSize())
        };
    }

    private String[] toRow(FileInfo f) {
        return new String[]{
            String.valueOf(f.getId()),
            String.valueOf(f.getVersionId()),
            f.getFilePath(),
            f.getFileHash(),
            String.valueOf(f.getFileSize()),
            f.isChunk() ? "1" : "0",
            f.getBlobKey()
        };
    }

    /**
     * 将 IOException 包装为 SQLException 以保持接口兼容
     */
    private SQLException wrapIOException(IOException e) {
        return new SQLException("CSV 操作失败: " + e.getMessage(), e);
    }

    // ========== 版本操作 ==========

    @Override
    public int insertVersion(VersionInfo version) throws SQLException {
        lock.writeLock().lock();
        try {
            int id = nextVersionId++;
            version.setId(id);
            versionsById.put(id, version);
            versionsByName.put(version.getVersionName(), version);
            flushVersions();
            return id;
        } catch (IOException e) {
            throw wrapIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void updateVersionStats(int versionId, int fileCount, long totalSize) throws SQLException {
        lock.writeLock().lock();
        try {
            VersionInfo v = versionsById.get(versionId);
            if (v != null) {
                v.setFileCount(fileCount);
                v.setTotalSize(totalSize);
                flushVersions();
            }
        } catch (IOException e) {
            throw wrapIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void updateVersionStatus(int versionId, VersionStatus status) throws SQLException {
        lock.writeLock().lock();
        try {
            VersionInfo v = versionsById.get(versionId);
            if (v != null) {
                v.setStatus(status);
                flushVersions();
            }
        } catch (IOException e) {
            throw wrapIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public VersionInfo getVersion(int id) throws SQLException {
        lock.readLock().lock();
        try {
            return versionsById.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public VersionInfo getVersionByName(String versionName) throws SQLException {
        lock.readLock().lock();
        try {
            return versionsByName.get(versionName);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<VersionInfo> getAllVersions() throws SQLException {
        lock.readLock().lock();
        try {
            List<VersionInfo> result = new ArrayList<>(versionsById.values());
            result.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public VersionInfo getLatestVersion() throws SQLException {
        lock.readLock().lock();
        try {
            return versionsById.values().stream()
                .max(Comparator.comparing(VersionInfo::getTimestamp))
                .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void deleteVersion(int id) throws SQLException {
        lock.writeLock().lock();
        try {
            VersionInfo v = versionsById.remove(id);
            if (v != null) {
                versionsByName.remove(v.getVersionName());
                // CASCADE: 删除关联的 file_info
                List<FileInfo> removed = filesByVersionId.remove(id);
                if (removed != null) {
                    for (FileInfo f : removed) {
                        List<FileInfo> blobFiles = filesByBlobKey.get(f.getBlobKey());
                        if (blobFiles != null) {
                            blobFiles.removeIf(fi -> fi.getId() == f.getId());
                            if (blobFiles.isEmpty()) {
                                filesByBlobKey.remove(f.getBlobKey());
                            }
                        }
                    }
                }
                flushAll();
            }
        } catch (IOException e) {
            throw wrapIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean isVersionFullyStored(int versionId) throws SQLException {
        lock.readLock().lock();
        try {
            List<FileInfo> files = filesByVersionId.get(versionId);
            if (files == null || files.isEmpty()) {
                return true;
            }
            for (FileInfo f : files) {
                BlobInfo blob = blobsByKey.get(f.getBlobKey());
                if (blob == null || blob.getState() != BlobState.STORED) {
                    return false;
                }
            }
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void refreshVersionCompletion(int versionId) throws SQLException {
        if (isVersionFullyStored(versionId)) {
            updateVersionStatus(versionId, VersionStatus.COMPLETED);
        } else {
            updateVersionStatus(versionId, VersionStatus.IN_PROGRESS);
        }
    }

    @Override
    public List<Integer> getVersionIdsUpTo(int versionId) throws SQLException {
        lock.readLock().lock();
        try {
            List<Integer> ids = new ArrayList<>();
            boolean capture = false;
            List<VersionInfo> sorted = getAllVersions();
            for (VersionInfo v : sorted) {
                if (v.getId() == versionId) {
                    capture = true;
                }
                if (capture) {
                    ids.add(v.getId());
                }
            }
            return ids;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ========== 文件 manifest ==========

    @Override
    public void insertFiles(List<FileInfo> files) throws SQLException {
        lock.writeLock().lock();
        try {
            for (FileInfo f : files) {
                f.setId(nextFileInfoId++);
                filesByVersionId.computeIfAbsent(f.getVersionId(), k -> new ArrayList<>()).add(f);
                filesByBlobKey.computeIfAbsent(f.getBlobKey(), k -> new ArrayList<>()).add(f);
            }
            flushFileInfo();
        } catch (IOException e) {
            throw wrapIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<FileInfo> getFilesByVersionId(int versionId) throws SQLException {
        lock.readLock().lock();
        try {
            List<FileInfo> files = filesByVersionId.get(versionId);
            return files != null ? new ArrayList<>(files) : new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void deleteFilesByVersionId(int versionId) throws SQLException {
        lock.writeLock().lock();
        try {
            List<FileInfo> removed = filesByVersionId.remove(versionId);
            if (removed != null) {
                for (FileInfo f : removed) {
                    List<FileInfo> blobFiles = filesByBlobKey.get(f.getBlobKey());
                    if (blobFiles != null) {
                        blobFiles.removeIf(fi -> fi.getVersionId() == versionId);
                        if (blobFiles.isEmpty()) {
                            filesByBlobKey.remove(f.getBlobKey());
                        }
                    }
                }
                flushFileInfo();
            }
        } catch (IOException e) {
            throw wrapIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<FileInfo> getFilesByBlobKeys(List<String> blobKeys) throws SQLException {
        lock.readLock().lock();
        try {
            List<FileInfo> result = new ArrayList<>();
            for (String key : blobKeys) {
                List<FileInfo> files = filesByBlobKey.get(key);
                if (files != null) {
                    result.addAll(files);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ========== Blob 操作 ==========

    @Override
    public void upsertBlob(BlobInfo blob) throws SQLException {
        lock.writeLock().lock();
        try {
            blobsByKey.put(blob.getBlobKey(), blob);
            flushBlobs();
        } catch (IOException e) {
            throw wrapIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public BlobInfo getBlob(String blobKey) throws SQLException {
        lock.readLock().lock();
        try {
            return blobsByKey.get(blobKey);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void updateBlobState(String blobKey, BlobState state, long compressedSize) throws SQLException {
        lock.writeLock().lock();
        try {
            BlobInfo blob = blobsByKey.get(blobKey);
            if (blob != null) {
                blob.setState(state);
                blob.setCompressedSize(compressedSize);
                flushBlobs();
            }
        } catch (IOException e) {
            throw wrapIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<BlobInfo> getBlobsByState(BlobState state) throws SQLException {
        lock.readLock().lock();
        try {
            return blobsByKey.values().stream()
                .filter(b -> b.getState() == state)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int countBlobsByState(BlobState state) throws SQLException {
        lock.readLock().lock();
        try {
            return (int) blobsByKey.values().stream()
                .filter(b -> b.getState() == state)
                .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<BlobInfo> getPendingBlobsForVersions(List<Integer> versionIds) throws SQLException {
        lock.readLock().lock();
        try {
            Set<String> blobKeys = new HashSet<>();
            for (int versionId : versionIds) {
                List<FileInfo> files = filesByVersionId.get(versionId);
                if (files != null) {
                    for (FileInfo f : files) {
                        blobKeys.add(f.getBlobKey());
                    }
                }
            }
            return blobKeys.stream()
                .map(blobsByKey::get)
                .filter(Objects::nonNull)
                .filter(b -> b.getState() == BlobState.PENDING)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int getBlobReferenceCount(String blobKey) throws SQLException {
        lock.readLock().lock();
        try {
            List<FileInfo> files = filesByBlobKey.get(blobKey);
            return files != null ? files.size() : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void deleteBlob(String blobKey) throws SQLException {
        lock.writeLock().lock();
        try {
            blobsByKey.remove(blobKey);
            flushBlobs();
        } catch (IOException e) {
            throw wrapIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<BlobInfo> gcUnreferencedBlobs() throws SQLException {
        lock.writeLock().lock();
        try {
            List<BlobInfo> removed = new ArrayList<>();
            Iterator<Map.Entry<String, BlobInfo>> it = blobsByKey.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, BlobInfo> entry = it.next();
                List<FileInfo> refs = filesByBlobKey.get(entry.getKey());
                if (refs == null || refs.isEmpty()) {
                    removed.add(entry.getValue());
                    it.remove();
                }
            }
            if (!removed.isEmpty()) {
                flushBlobs();
            }
            return removed;
        } catch (IOException e) {
            throw wrapIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clearAllBlobs() throws SQLException {
        lock.writeLock().lock();
        try {
            blobsByKey.clear();
            flushBlobs();
        } catch (IOException e) {
            throw wrapIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
