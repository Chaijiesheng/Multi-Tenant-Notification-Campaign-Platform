package com.example.notifications.backpressure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerQueueMetricsTest {

    @Mock
    private ThreadPoolTaskExecutor mockExecutor;

    @Mock
    private ThreadPoolExecutor mockThreadPoolExecutor;

    @Mock
    private BlockingQueue<Runnable> mockQueue;

    private WorkerQueueMetrics metrics;

    @BeforeEach
    void setUp() {
        when(mockExecutor.getThreadPoolExecutor()).thenReturn(mockThreadPoolExecutor);
        when(mockThreadPoolExecutor.getQueue()).thenReturn(mockQueue);
        metrics = new WorkerQueueMetrics(mockExecutor);
    }

    @Test
    void queueAt79Percent_isNotOverloaded() {
        // 399 / 500 = 79.8% — below the 80% threshold
        when(mockQueue.size()).thenReturn(399);

        assertThat(metrics.getQueueSize()).isEqualTo(399);
        assertThat(metrics.getQueueUtilization()).isLessThan(0.80);
        assertThat(metrics.isOverloaded()).isFalse();
    }

    @Test
    void queueAt80Percent_isOverloaded() {
        // 400 / 500 = 80% — exactly at the threshold
        when(mockQueue.size()).thenReturn(400);

        assertThat(metrics.getQueueUtilization()).isGreaterThanOrEqualTo(0.80);
        assertThat(metrics.isOverloaded()).isTrue();
    }

    @Test
    void queueAt100Percent_isOverloaded() {
        // 500 / 500 = 100%
        when(mockQueue.size()).thenReturn(500);

        assertThat(metrics.getQueueUtilization()).isEqualTo(1.0);
        assertThat(metrics.isOverloaded()).isTrue();
    }

    @Test
    void queueCapacity_is500() {
        assertThat(metrics.getQueueCapacity()).isEqualTo(500);
    }

    @Test
    void emptyQueue_isNotOverloaded() {
        when(mockQueue.size()).thenReturn(0);

        assertThat(metrics.isOverloaded()).isFalse();
        assertThat(metrics.getQueueUtilization()).isEqualTo(0.0);
    }
}
