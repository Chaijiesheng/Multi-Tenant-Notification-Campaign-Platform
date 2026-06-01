package com.example.notifications.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String aggregateType;

    @Column(nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(nullable = false)
    private boolean processed = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime processedAt;

    public static OutboxEvent campaignCreated(Long tenantId, Long campaignId, String campaignName, Channel channel) {
        OutboxEvent event = new OutboxEvent();
        event.setTenantId(tenantId);
        event.setAggregateType("Campaign");
        event.setAggregateId(campaignId.toString());
        event.setEventType("CampaignCreated");
        event.setPayload(String.format(
                "{\"tenantId\":%d,\"campaignId\":%d,\"campaignName\":\"%s\",\"channel\":\"%s\"}",
                tenantId, campaignId, campaignName.replace("\"", "\\\""), channel.name()
        ));
        return event;
    }
}
