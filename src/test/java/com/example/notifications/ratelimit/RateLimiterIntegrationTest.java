package com.example.notifications.ratelimit;

import com.example.notifications.domain.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RateLimiterIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notifications_ratelimit_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private BucketChannelRateLimiter rateLimiter;

    @BeforeEach
    void resetBucket() {
        rateLimiter.resetBucket(Channel.SMS);
    }

    @Test
    void smsChannel_100AcquiresSucceed() {
        for (int i = 0; i < 100; i++) {
            assertThat(rateLimiter.tryAcquire(Channel.SMS))
                    .as("Acquire #%d should succeed", i + 1)
                    .isTrue();
        }
    }

    @Test
    void smsChannel_101stAcquireFails() {
        for (int i = 0; i < 100; i++) {
            rateLimiter.tryAcquire(Channel.SMS);
        }
        assertThat(rateLimiter.tryAcquire(Channel.SMS)).isFalse();
    }
}
