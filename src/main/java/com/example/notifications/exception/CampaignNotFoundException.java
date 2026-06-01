package com.example.notifications.exception;

public class CampaignNotFoundException extends RuntimeException {

    public CampaignNotFoundException(Long campaignId) {
        super("Campaign not found with id: " + campaignId);
    }

    public CampaignNotFoundException(Long campaignId, Long tenantId) {
        super("Campaign not found with id: " + campaignId + " for tenant: " + tenantId);
    }
}
