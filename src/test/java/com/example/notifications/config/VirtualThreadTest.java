package com.example.notifications.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VirtualThreadTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notifications_vt_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private ThreadPoolTaskExecutor asyncTaskExecutor;

    @Autowired
    private Environment env;

    @Test
    void asyncExecutor_usesNamedThreads() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        asyncTaskExecutor.execute(() -> future.complete(Thread.currentThread().getName()));

        String threadName = future.get(5, TimeUnit.SECONDS);
        assertThat(threadName).startsWith("notif-worker-");
    }

    @Test
    void springConfiguration_virtualThreadsEnabled() {
        String value = env.getProperty("spring.threads.virtual.enabled");
        assertThat(value).isEqualTo("true");
    }

    @Test
    void asyncExecutor_hasBoundedPool() {
        assertThat(asyncTaskExecutor.getCorePoolSize()).isEqualTo(10);
        assertThat(asyncTaskExecutor.getMaxPoolSize()).isEqualTo(50);
    }
}
