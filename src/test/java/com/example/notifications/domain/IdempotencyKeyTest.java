package com.example.notifications.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeyTest {

    @Test
    void keyFormat_isCorrect() {
        IdempotencyKey key = IdempotencyKey.of(1L, 42L, 99L, Channel.EMAIL);
        assertThat(key.value()).isEqualTo("1:42:99:EMAIL");
    }

    @Test
    void keyFormat_smsChannel() {
        IdempotencyKey key = IdempotencyKey.of(10L, 5L, 3L, Channel.SMS);
        assertThat(key.value()).isEqualTo("10:5:3:SMS");
    }

    @Test
    void keyFormat_pushChannel() {
        IdempotencyKey key = IdempotencyKey.of(2L, 7L, 100L, Channel.PUSH);
        assertThat(key.value()).isEqualTo("2:7:100:PUSH");
    }
}
