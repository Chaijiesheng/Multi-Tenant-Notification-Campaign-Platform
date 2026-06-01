package com.example.notifications.domain;

public record TenantId(Long value) {
    public TenantId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("TenantId must be a positive number");
        }
    }
}
