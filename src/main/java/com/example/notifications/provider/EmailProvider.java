package com.example.notifications.provider;

import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class EmailProvider implements NotificationProvider {

    private static final String[] ERROR_MESSAGES = {
            "SMTP connection timeout",
            "Mailbox full",
            "Invalid recipient address",
            "Rate limit exceeded",
            "Authentication failure"
    };

    @Override
    public ProviderResponse send(NotificationJob job, Recipient recipient) {
        simulateLatency();
        String maskedEmail = maskEmail(recipient.getEmail());
        if (shouldFail()) {
            String error = randomError();
            log.warn("EMAIL delivery failed for {} - {}", maskedEmail, error);
            return new ProviderResponse(false, "Provider error: " + error);
        }
        log.info("EMAIL delivered successfully to {}", maskedEmail);
        return new ProviderResponse(true, "Delivered via EMAIL");
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "****";
        int at = email.indexOf('@');
        if (at <= 0) return "****";
        return email.charAt(0) + "****" + email.substring(at);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(50, 201));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldFail() {
        return ThreadLocalRandom.current().nextDouble() < 0.20;
    }

    private String randomError() {
        return ERROR_MESSAGES[ThreadLocalRandom.current().nextInt(ERROR_MESSAGES.length)];
    }
}
