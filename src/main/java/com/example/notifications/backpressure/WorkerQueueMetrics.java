package com.example.notifications.backpressure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Exposes queue metrics for the bounded notification worker executor.
 *
 * COUPLING NOTE: getQueueCapacity() hard-codes 500, which MUST match the
 * queueCapacity value in VirtualThreadConfig (and application.yml). If either
 * changes, update both in sync.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WorkerQueueMetrics {

    private final ThreadPoolTaskExecutor asyncTaskExecutor;

    public int getQueueSize() {
        return asyncTaskExecutor.getThreadPoolExecutor().getQueue().size();
    }

    public int getQueueCapacity() {
        return 500;
    }

    public double getQueueUtilization() {
        return (double) getQueueSize() / getQueueCapacity();
    }

    public boolean isOverloaded() {
        return getQueueUtilization() >= 0.80;
    }
}
