package com.example.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CampaignEventListener {

    @EventListener
    public void onOutboxEvent(OutboxEventPublished event) {
        if ("CampaignCreated".equals(event.event().getEventType())) {
            log.info("CampaignCreated event received for campaignId={}", event.event().getAggregateId());
        }
    }
}
