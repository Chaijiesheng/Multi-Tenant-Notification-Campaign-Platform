package com.example.notifications.provider;

import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class SmsProvider implements NotificationProvider {

    private static final String[] ERROR_MESSAGES = {
            "Network error",
            "Invalid phone number",
            "Carrier rejection",
            "Message too long",
            "Account suspended"
    };

    @Override
    public ProviderResponse send(NotificationJob job, Recipient recipient) {
        simulateLatency();
        String maskedPhone = maskPhone(recipient.getPhone());
        if (shouldFail()) {
            String error = randomError();
            log.warn("SMS delivery failed for {} - {}", maskedPhone, error);
            return new ProviderResponse(false, "Provider error: " + error);
        }
        log.info("SMS delivered successfully to {}", maskedPhone);
        return new ProviderResponse(true, "Delivered via SMS");
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "****";
        String prefix = phone.startsWith("+") ? "+" : "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 5) return prefix + "****";
        return prefix + digits.substring(0, 3) + "****" + digits.substring(digits.length() - 2);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(50, 201));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldFail() {
        return ThreadLocalRandom.current().nextDouble() < 0.15;
    }

    private String randomError() {
        return ERROR_MESSAGES[ThreadLocalRandom.current().nextInt(ERROR_MESSAGES.length)];
    }
}
