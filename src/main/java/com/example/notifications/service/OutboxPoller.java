package com.example.notifications.service;

import com.example.notifications.domain.OutboxEvent;
import com.example.notifications.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Scheduled(fixedDelay = 3000)
    public void poll() {
        List<OutboxEvent> events = outboxEventRepository.findTop100ByProcessedFalseOrderByCreatedAtAsc();
        if (events.isEmpty()) return;

        log.debug("OutboxPoller processing {} events", events.size());

        for (OutboxEvent event : events) {
            try {
                applicationEventPublisher.publishEvent(new OutboxEventPublished(event));
                event.setProcessed(true);
                event.setProcessedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
                log.info("Outbox event published: type={} aggregateId={}", event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to process outbox event id={}: {}", event.getId(), e.getMessage(), e);
            }
        }
    }
}
