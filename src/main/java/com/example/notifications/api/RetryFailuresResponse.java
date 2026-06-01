package com.example.notifications.api;

public record RetryFailuresResponse(
        Long campaignId,
        int jobsRequeued,
        String message
) {}
