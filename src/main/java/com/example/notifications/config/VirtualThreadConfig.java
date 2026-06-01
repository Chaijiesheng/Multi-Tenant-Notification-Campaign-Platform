package com.example.notifications.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Declares the primary async executor for the notification worker pool.
 *
 * With spring.threads.virtual.enabled=true, Spring Boot automatically configures
 * TomcatProtocolHandlerCustomizer to serve HTTP requests on virtual threads.
 * This bean creates the bounded ThreadPoolTaskExecutor that the
 * NotificationDispatchWorker uses for per-job dispatch. The explicit bounded pool
 * (core=10, max=50, queue=500) prevents unbounded memory growth while still
 * benefiting from virtual-thread-friendly blocking I/O inside each job.
 *
 * COUPLING: WorkerQueueMetrics.getQueueCapacity() must return the same value (500)
 * as the queueCapacity set here. If this changes, update WorkerQueueMetrics in sync.
 */
@Configuration
public class VirtualThreadConfig {

    @Bean(name = "asyncTaskExecutor")
    public ThreadPoolTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notif-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
