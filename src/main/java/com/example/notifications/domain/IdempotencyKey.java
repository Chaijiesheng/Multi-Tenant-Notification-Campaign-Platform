package com.example.notifications.domain;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey must not be blank");
        }
    }

    public static IdempotencyKey of(Long tenantId, Long campaignId, Long recipientId, Channel channel) {
        String key = tenantId + ":" + campaignId + ":" + recipientId + ":" + channel.name();
        return new IdempotencyKey(key);
    }
}
