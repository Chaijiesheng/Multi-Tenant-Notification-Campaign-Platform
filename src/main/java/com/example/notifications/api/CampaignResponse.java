package com.example.notifications.api;

import com.example.notifications.domain.Campaign;
import com.example.notifications.domain.CampaignStatus;
import com.example.notifications.domain.Channel;

import java.time.LocalDateTime;

public record CampaignResponse(
        Long id,
        Long tenantId,
        String name,
        Channel channel,
        CampaignStatus status,
        int totalRecipients,
        int sentCount,
        int failedCount,
        int skippedCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CampaignResponse from(Campaign c) {
        return new CampaignResponse(
                c.getId(),
                c.getTenantId(),
                c.getName(),
                c.getChannel(),
                c.getStatus(),
                c.getTotalRecipients(),
                c.getSentCount(),
                c.getFailedCount(),
                c.getSkippedCount(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
