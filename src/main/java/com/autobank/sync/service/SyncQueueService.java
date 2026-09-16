package com.autobank.sync.service;

import com.autobank.config.DatabaseConfig;
import com.autobank.sync.model.SyncTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SyncQueueService {
    private static final Logger log = LoggerFactory.getLogger(SyncQueueService.class);
    private static final SyncQueueService INSTANCE = new SyncQueueService();
    
    private ScheduledExecutorService scheduler;
    private final int MAX_RETRIES = 5;

    private SyncQueueService() {}

    public static SyncQueueService getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sync-queue-worker");
            t.setDaemon(true);
            return t;
        });

        // Run every 60 seconds
        scheduler.scheduleWithFixedDelay(this::processQueue, 10, 60, TimeUnit.SECONDS);
        log.info("SyncQueueService started");
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            log.info("SyncQueueService stopped");
        }
    }

    public void enqueueTask(String taskType, String payload, String targetEndpoint) {
        String sql = "INSERT INTO sync_queue (task_type, payload, target_endpoint, status) VALUES (?, ?, ?, 'PENDING')";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, taskType);
            stmt.setString(2, payload);
            stmt.setString(3, targetEndpoint);
            stmt.executeUpdate();
            log.info("Task enqueued: type={}, endpoint={}", taskType, targetEndpoint);
        } catch (Exception e) {
            log.error("Failed to enqueue sync task", e);
        }
    }

    private void processQueue() {
        if (!DatabaseConfig.isConnected()) return;

        // Fetch pending tasks
        List<SyncTask> tasks = new ArrayList<>();
        String querySql = "SELECT * FROM sync_queue WHERE status = 'PENDING' OR status = 'FAILED' ORDER BY id ASC LIMIT 10";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(querySql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                SyncTask task = new SyncTask();
                task.setId(rs.getInt("id"));
                task.setTaskType(rs.getString("task_type"));
                task.setPayload(rs.getString("payload"));
                task.setTargetEndpoint(rs.getString("target_endpoint"));
                task.setStatus(rs.getString("status"));
                task.setRetryCount(rs.getInt("retry_count"));
                Timestamp created = rs.getTimestamp("created_at");
                if (created != null) task.setCreatedAt(created.toLocalDateTime());
                Timestamp updated = rs.getTimestamp("updated_at");
                if (updated != null) task.setUpdatedAt(updated.toLocalDateTime());
                tasks.add(task);
            }
        } catch (Exception e) {
            log.error("Failed to fetch sync queue tasks", e);
            return;
        }

        if (tasks.isEmpty()) return;

        for (SyncTask task : tasks) {
            if (task.getRetryCount() >= MAX_RETRIES) {
                updateTaskStatus(task.getId(), "DEAD_LETTER", task.getRetryCount());
                continue;
            }

            try {
                log.info("Processing sync task {} (Type: {})", task.getId(), task.getTaskType());
                boolean success = performSync(task);
                if (success) {
                    updateTaskStatus(task.getId(), "COMPLETED", task.getRetryCount());
                } else {
                    updateTaskStatus(task.getId(), "FAILED", task.getRetryCount() + 1);
                }
            } catch (Exception e) {
                log.error("Error processing sync task " + task.getId(), e);
                updateTaskStatus(task.getId(), "FAILED", task.getRetryCount() + 1);
            }
        }
    }

    private boolean performSync(SyncTask task) {
        // Mocking the actual sync operation to a remote server/API
        // In a real scenario, this would use an HttpClient to POST the payload to task.getTargetEndpoint()
        try {
            // Simulate network delay
            Thread.sleep(500);
            
            // Randomly fail sometimes to test retries, but generally succeed
            if (Math.random() > 0.8) {
                log.warn("Simulated network failure for sync task {}", task.getId());
                return false;
            }
            
            log.info("Sync task {} successfully synced to {}", task.getId(), task.getTargetEndpoint());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void updateTaskStatus(int id, String status, int retryCount) {
        String sql = "UPDATE sync_queue SET status = ?, retry_count = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, retryCount);
            stmt.setInt(3, id);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to update sync task status for id " + id, e);
        }
    }
}
