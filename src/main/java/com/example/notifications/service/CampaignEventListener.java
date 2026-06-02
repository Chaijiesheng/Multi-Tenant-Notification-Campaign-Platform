package com.example.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Handles domain events published by OutboxPoller.
 * Both CampaignCreated and NotificationSent events are delivered here
 * at-least-once — consumers must be idempotent.
 */
@Component
@Slf4j
public class CampaignEventListener {

    @EventListener
    public void onOutboxEvent(OutboxEventPublished event) {
        switch (event.event().getEventType()) {
            case "CampaignCreated" ->
                log.info("CampaignCreated event received for campaignId={}",
                        event.event().getAggregateId());

            case "NotificationSent" ->
                log.info("NotificationSent event received for notificationJobId={}",
                        event.event().getAggregateId());

            default ->
                log.debug("Unhandled outbox event type={} aggregateId={}",
                        event.event().getEventType(), event.event().getAggregateId());
        }
    }
}
