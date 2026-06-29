package io.github.limuqy.mc.backup.thread;

import io.github.limuqy.mc.backup.config.BackupConfig;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BackupThreadPool {
    private static BackupThreadPool instance;
    private ThreadPoolExecutor executor;
    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private final AtomicInteger totalTasks = new AtomicInteger(0);

    private BackupThreadPool() {
        initExecutor();
    }

    public static BackupThreadPool getInstance() {
        if (instance == null) {
            instance = new BackupThreadPool();
        }
        return instance;
    }

    private void initExecutor() {
        int corePoolSize = BackupConfig.getThreadCount();
        int maximumPoolSize = corePoolSize * 2;
        long keepAliveTime = 60L;

        executor = new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "InstantBackup-Worker-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public void updateThreadCount() {
        int corePoolSize = BackupConfig.getThreadCount();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaximumPoolSize(corePoolSize * 2);
    }

    public Future<?> submitTask(Runnable task) {
        totalTasks.incrementAndGet();
        return executor.submit(() -> {
            try {
                task.run();
            } finally {
                completedTasks.incrementAndGet();
            }
        });
    }

    public <T> Future<T> submitTask(Callable<T> task) {
        totalTasks.incrementAndGet();
        return executor.submit(() -> {
            try {
                return task.call();
            } finally {
                completedTasks.incrementAndGet();
            }
        });
    }

    public int getCompletedTasks() {
        return completedTasks.get();
    }

    public int getTotalTasks() {
        return totalTasks.get();
    }

    public int getActiveThreads() {
        return executor.getActiveCount();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void resetStats() {
        completedTasks.set(0);
        totalTasks.set(0);
    }
}
