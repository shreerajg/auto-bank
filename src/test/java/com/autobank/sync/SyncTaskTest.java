package com.autobank.sync;

import com.autobank.sync.model.SyncTask;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class SyncTaskTest {

    @Test
    public void testSyncTaskProperties() {
        SyncTask task = new SyncTask();
        task.setId(101);
        task.setTaskType("TRANSACTION_SYNC");
        task.setPayload("{\"txId\": 55, \"amount\": 1500.00}");
        task.setTargetEndpoint("https://central.patasinstha.org/api/v1/sync");
        task.setStatus("PENDING");
        task.setRetryCount(0);

        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        assertEquals(101, task.getId());
        assertEquals("TRANSACTION_SYNC", task.getTaskType());
        assertTrue(task.getPayload().contains("1500.00"));
        assertEquals("https://central.patasinstha.org/api/v1/sync", task.getTargetEndpoint());
        assertEquals("PENDING", task.getStatus());
        assertEquals(0, task.getRetryCount());
        assertEquals(now, task.getCreatedAt());
        assertEquals(now, task.getUpdatedAt());
    }

    @Test
    public void testRetryIncrement() {
        SyncTask task = new SyncTask();
        task.setRetryCount(0);
        task.setRetryCount(task.getRetryCount() + 1);
        assertEquals(1, task.getRetryCount());

        task.setRetryCount(task.getRetryCount() + 1);
        assertEquals(2, task.getRetryCount());
    }
}
