package com.example.notifications.domain;

public record CampaignId(Long value) {
    public CampaignId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("CampaignId must be a positive number");
        }
    }
}
