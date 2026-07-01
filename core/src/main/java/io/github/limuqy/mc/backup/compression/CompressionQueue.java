package io.github.limuqy.mc.backup.compression;

import io.github.limuqy.mc.backup.backup.BackupEngine;
import io.github.limuqy.mc.backup.config.BackupConfig;
import io.github.limuqy.mc.backup.database.BlobInfo;
import io.github.limuqy.mc.backup.database.BlobState;
import io.github.limuqy.mc.backup.database.DatabaseManager;
import io.github.limuqy.mc.backup.storage.BlobStore;
import io.github.limuqy.mc.backup.logging.ModLog;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步压缩队列消费者
 */
public class CompressionQueue {
    private static CompressionQueue instance;

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread[] workers;
    private BlobStore blobStore;
    private final DatabaseManager dbManager = DatabaseManager.getInstance();

    private CompressionQueue() {
    }

    public static CompressionQueue getInstance() {
        if (instance == null) {
            instance = new CompressionQueue();
        }
        return instance;
    }

    public void start(BlobStore blobStore) {
        this.blobStore = blobStore;
        if (!running.compareAndSet(false, true)) {
            return;
        }
        if (!BackupConfig.isCompressionEnabled()) {
            ModLog.info("[Instant Backup] 压缩已关闭，跳过压缩队列 worker");
            return;
        }
        int threadCount = Math.max(1, BackupConfig.getCompressionThreads());
        workers = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int workerId = i + 1;
            workers[i] = new Thread(() -> workerLoop(workerId), "InstantBackup-Compress-" + workerId);
            workers[i].setDaemon(true);
            workers[i].setPriority(Thread.NORM_PRIORITY - 1);
            workers[i].start();
        }
        ModLog.info("[Instant Backup] 压缩队列已启动，线程数: {}", threadCount);
    }

    public void shutdown() {
        running.set(false);
        if (workers != null) {
            for (Thread worker : workers) {
                if (worker != null) {
                    worker.interrupt();
                }
            }
        }
    }

    public void enqueue(String blobKey) {
        if (blobKey != null) {
            queue.offer(blobKey);
        }
    }

    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 同步处理队列中所有待处理 blob（压缩关闭且无 worker 时使用）
     */
    public void drainPendingSync() {
        String blobKey;
        while ((blobKey = queue.poll()) != null) {
            processBlob(blobKey);
        }
    }

    /**
     * 启动时重新入队所有 STAGED blob
     */
    public void requeueStagedBlobs() {
        try {
            List<BlobInfo> staged = dbManager.getBlobsByState(BlobState.STAGED);
            for (BlobInfo blob : staged) {
                enqueue(blob.getBlobKey());
            }
            ModLog.info("[Instant Backup] 重新入队 STAGED blob: {}", staged.size());
        } catch (SQLException e) {
            ModLog.error("[Instant Backup] 重新入队 STAGED blob 失败", e);
        }
    }

    private void workerLoop(int workerId) {
        while (running.get()) {
            try {
                String blobKey = queue.take();
                processBlob(blobKey);
            } catch (InterruptedException e) {
                if (!running.get()) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                ModLog.error("[Instant Backup] 压缩 worker-{} 处理失败", workerId, e);
            }
        }
    }

    private void processBlob(String blobKey) {
        try {
            BlobInfo blob = dbManager.getBlob(blobKey);
            if (blob == null) {
                return;
            }
            if (blob.getState() == BlobState.STORED) {
                BackupEngine.getInstance().onBlobStored(blobKey);
                return;
            }
            if (blob.getState() != BlobState.STAGED) {
                return;
            }
            long storedSize;
            if (BackupConfig.isCompressionEnabled()) {
                storedSize = blobStore.compressRaw(blob);
                ModLog.debug("[Instant Backup] 压缩完成: {} ({} bytes)", blob.getFilePath(), storedSize);
            } else {
                storedSize = blobStore.finalizeWithoutCompression(blob);
                ModLog.debug("[Instant Backup] 未压缩存储: {} ({} bytes)", blob.getFilePath(), storedSize);
            }
            dbManager.updateBlobState(blobKey, BlobState.STORED, storedSize);
            BackupEngine.getInstance().onBlobStored(blobKey);
        } catch (Exception e) {
            ModLog.warn("[Instant Backup] 压缩失败，将稍后重试: {}", blobKey, e);
            enqueue(blobKey);
        }
    }
}
