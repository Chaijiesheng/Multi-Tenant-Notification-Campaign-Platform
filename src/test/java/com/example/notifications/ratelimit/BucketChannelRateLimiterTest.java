package com.example.notifications.ratelimit;

import com.example.notifications.domain.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BucketChannelRateLimiterTest {

    private BucketChannelRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new BucketChannelRateLimiter();
        rateLimiter.init();
    }

    @Test
    void first100Acquires_allSucceed() {
        for (int i = 0; i < 100; i++) {
            boolean acquired = rateLimiter.tryAcquire(Channel.EMAIL);
            assertThat(acquired)
                    .as("Acquire #%d should succeed", i + 1)
                    .isTrue();
        }
    }

    @Test
    void acquire101st_returnsFalse() {
        for (int i = 0; i < 100; i++) {
            rateLimiter.tryAcquire(Channel.EMAIL);
        }
        boolean acquired = rateLimiter.tryAcquire(Channel.EMAIL);
        assertThat(acquired).isFalse();
    }

    @Test
    void bucketsAreIndependentPerChannel() {
        // Exhaust EMAIL bucket
        for (int i = 0; i < 100; i++) {
            rateLimiter.tryAcquire(Channel.EMAIL);
        }
        assertThat(rateLimiter.tryAcquire(Channel.EMAIL)).isFalse();

        // SMS bucket should still be full
        assertThat(rateLimiter.tryAcquire(Channel.SMS)).isTrue();
        assertThat(rateLimiter.tryAcquire(Channel.PUSH)).isTrue();
    }
}
