package com.example.notifications.exception;

public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(Long tenantId) {
        super("Tenant not found with id: " + tenantId);
    }

    public TenantNotFoundException(String slug) {
        super("Tenant not found with slug: " + slug);
    }
}
