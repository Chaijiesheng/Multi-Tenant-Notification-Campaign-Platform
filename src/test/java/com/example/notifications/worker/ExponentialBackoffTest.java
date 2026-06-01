package com.example.notifications.worker;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialBackoffTest {

    private LocalDateTime computeNextRetryAt(int retryCount) {
        long backoffSeconds = Math.min((long) Math.pow(2, retryCount - 1), 60);
        return LocalDateTime.now().plusSeconds(backoffSeconds);
    }

    @Test
    void retryCount1_backoff1Second() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = computeNextRetryAt(1);
        assertThat(ChronoUnit.SECONDS.between(before, nextRetry)).isBetween(0L, 2L);
        assertThat(ChronoUnit.MILLIS.between(before.plusSeconds(1), nextRetry)).isBetween(-500L, 500L);
    }

    @Test
    void retryCount2_backoff2Seconds() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = computeNextRetryAt(2);
        assertThat(ChronoUnit.MILLIS.between(before.plusSeconds(2), nextRetry)).isBetween(-500L, 500L);
    }

    @Test
    void retryCount3_backoff4Seconds() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = computeNextRetryAt(3);
        assertThat(ChronoUnit.MILLIS.between(before.plusSeconds(4), nextRetry)).isBetween(-500L, 500L);
    }

    @Test
    void retryCount4_backoff8Seconds() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = computeNextRetryAt(4);
        assertThat(ChronoUnit.MILLIS.between(before.plusSeconds(8), nextRetry)).isBetween(-500L, 500L);
    }

    @Test
    void veryHighRetryCount_cappedAt60Seconds() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = computeNextRetryAt(20);
        assertThat(ChronoUnit.MILLIS.between(before.plusSeconds(60), nextRetry)).isBetween(-500L, 500L);
    }
}
