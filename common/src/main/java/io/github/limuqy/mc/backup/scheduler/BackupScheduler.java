package io.github.limuqy.mc.backup.scheduler;

import io.github.limuqy.mc.backup.backup.BackupManager;
import io.github.limuqy.mc.backup.config.BackupConfig;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BackupScheduler {
    private static BackupScheduler instance;
    private final BackupManager backupManager = BackupManager.getInstance();
    private final AtomicInteger tickCounter = new AtomicInteger(0);
    private final AtomicLong lastBackupTime = new AtomicLong(0);
    private volatile boolean enabled = true;

    private BackupScheduler() {
    }

    public static BackupScheduler getInstance() {
        if (instance == null) {
            instance = new BackupScheduler();
        }
        return instance;
    }

    /**
     * 服务器 tick 回调
     * 由 Mixin 每 tick 调用
     *
     * @param playerCount 当前玩家数量
     * @param carpetBotCount 地毯假人数量
     */
    public void onServerTick(int playerCount, int carpetBotCount) {
        if (!enabled || !BackupConfig.isEnabled()) {
            return;
        }

        int ticks = tickCounter.incrementAndGet();

        // 每 20 tick = 1 秒检查一次
        if (ticks >= 20) {
            tickCounter.set(0);

            long currentTime = System.currentTimeMillis();
            long intervalMs = BackupConfig.getBackupInterval() * 1000L;

            // 首次运行时初始化上次备份时间
            if (lastBackupTime.get() == 0) {
                lastBackupTime.set(currentTime);
                return;
            }

            // 检查是否到达备份间隔
            if (currentTime - lastBackupTime.get() >= intervalMs) {
                if (shouldBackup(playerCount, carpetBotCount) && !backupManager.isBackupInProgress()) {
                    lastBackupTime.set(currentTime);
                    backupManager.createBackup(null, false);
                }
            }
        }
    }

    /**
     * 判断是否应该执行备份
     */
    private boolean shouldBackup(int playerCount, int carpetBotCount) {
        if (!BackupConfig.isOnlyWhenPlayersOnline()) {
            return true;
        }

        int effectivePlayerCount = playerCount;
        if (BackupConfig.isExcludeCarpetBots()) {
            effectivePlayerCount = Math.max(0, playerCount - carpetBotCount);
        }
        return effectivePlayerCount > 0;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reset() {
        tickCounter.set(0);
        lastBackupTime.set(0);
    }
}
