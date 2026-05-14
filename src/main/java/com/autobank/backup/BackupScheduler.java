package com.autobank.backup;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * BackupScheduler — runs auto-backups on a configurable daily schedule.
 *
 * Usage:
 *   BackupScheduler.getInstance().start(onSuccess, onError);
 *   BackupScheduler.getInstance().stop();
 */
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);
    private static final BackupScheduler INSTANCE = new BackupScheduler();

    /** Time-of-day for the daily auto backup (can be changed at runtime). */
    private LocalTime scheduledTime = LocalTime.of(22, 0); // 10 PM default

    private ScheduledExecutorService scheduler;
    private Consumer<Path>   onSuccess;
    private Consumer<String> onError;

    private BackupScheduler() {}

    public static BackupScheduler getInstance() { return INSTANCE; }

    // ── Control ───────────────────────────────────────────────────────────────

    /**
     * Starts the scheduler. Fires once at the configured time each day.
     * Callbacks are dispatched on the JavaFX thread.
     *
     * @param onSuccess receives the backup path when done
     * @param onError   receives the error message on failure
     */
    public synchronized void start(Consumer<Path> onSuccess, Consumer<String> onError) {
        stop();
        this.onSuccess = onSuccess;
        this.onError   = onError;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "autobank-backup-scheduler");
            t.setDaemon(true);
            return t;
        });

        long initialDelay = secondsUntilNext(scheduledTime);
        long period       = TimeUnit.DAYS.toSeconds(1);

        scheduler.scheduleAtFixedRate(this::runBackup, initialDelay, period, TimeUnit.SECONDS);
        log.info("Backup scheduler started — next run in {} minutes",
                TimeUnit.SECONDS.toMinutes(initialDelay));
    }

    public synchronized void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
            log.info("Backup scheduler stopped");
        }
    }

    /**
     * Triggers a backup immediately on a background thread (non-blocking).
     */
    public void triggerNow(Consumer<Path> onSuccess, Consumer<String> onError) {
        Thread t = new Thread(() -> {
            try {
                Path result = new BackupService().createBackup();
                if (onSuccess != null) Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception e) {
                log.error("Manual backup failed: {}", e.getMessage(), e);
                if (onError != null) Platform.runLater(() -> onError.accept(e.getMessage()));
            }
        }, "autobank-manual-backup");
        t.setDaemon(true);
        t.start();
    }

    public LocalTime getScheduledTime()            { return scheduledTime; }
    public void setScheduledTime(LocalTime time)   { this.scheduledTime = time; }

    // ── Private ───────────────────────────────────────────────────────────────

    private void runBackup() {
        log.info("Scheduled backup starting...");
        try {
            Path result = new BackupService().createBackup();
            log.info("Scheduled backup done: {}", result);
            if (onSuccess != null) Platform.runLater(() -> onSuccess.accept(result));
        } catch (Exception e) {
            log.error("Scheduled backup failed: {}", e.getMessage(), e);
            if (onError != null) Platform.runLater(() -> onError.accept(e.getMessage()));
        }
    }

    /** Seconds from now until the next occurrence of targetTime (same day or tomorrow). */
    private static long secondsUntilNext(LocalTime target) {
        LocalTime now = LocalTime.now();
        long secondsToday = now.until(target, java.time.temporal.ChronoUnit.SECONDS);
        if (secondsToday <= 0) secondsToday += TimeUnit.DAYS.toSeconds(1);
        return secondsToday;
    }
}
