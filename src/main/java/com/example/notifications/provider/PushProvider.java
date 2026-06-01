package com.example.notifications.provider;

import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class PushProvider implements NotificationProvider {

    private static final String[] ERROR_MESSAGES = {
            "Invalid push token",
            "Token expired",
            "Device offline",
            "Payload too large",
            "FCM quota exceeded"
    };

    @Override
    public ProviderResponse send(NotificationJob job, Recipient recipient) {
        simulateLatency();
        String maskedToken = maskToken(recipient.getPushToken());
        if (shouldFail()) {
            String error = randomError();
            log.warn("PUSH delivery failed for token {} - {}", maskedToken, error);
            return new ProviderResponse(false, "Provider error: " + error);
        }
        log.info("PUSH delivered successfully to token {}", maskedToken);
        return new ProviderResponse(true, "Delivered via PUSH");
    }

    private String maskToken(String token) {
        if (token == null || token.isBlank()) return "****";
        if (token.length() <= 8) return "****";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(50, 201));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldFail() {
        return ThreadLocalRandom.current().nextDouble() < 0.25;
    }

    private String randomError() {
        return ERROR_MESSAGES[ThreadLocalRandom.current().nextInt(ERROR_MESSAGES.length)];
    }
}
