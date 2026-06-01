package com.example.notifications.rule;

import com.example.notifications.domain.Campaign;
import com.example.notifications.domain.Channel;
import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import com.example.notifications.domain.Tenant;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Slf4j
public class DndWindowRule implements NotificationRule {

    private final Clock clock;

    public DndWindowRule() {
        this.clock = Clock.systemDefaultZone();
    }

    public DndWindowRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String name() {
        return "DndWindow";
    }

    @Override
    public RuleResult evaluate(NotificationJob job, Recipient recipient, Tenant tenant, Campaign campaign) {
        if (job.getChannel() == Channel.EMAIL) {
            return RuleResult.pass();
        }

        if (campaign.isTransactional()) {
            return RuleResult.pass();
        }

        ZoneId zoneId = resolveZone(recipient.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId);
        int hour = now.getHour();

        boolean dndActive = (hour >= 22 || hour < 8);
        if (dndActive) {
            ZonedDateTime nextWindow = now.toLocalDate().atTime(8, 0).atZone(zoneId);
            if (!nextWindow.isAfter(now)) {
                nextWindow = nextWindow.plusDays(1);
            }
            LocalDateTime retryAt = nextWindow.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            String reason = "DND window active for channel " + job.getChannel();
            log.debug("DND active for recipient {} in zone {} — retry at {}", recipient.getId(), zoneId, retryAt);
            return RuleResult.delay(reason, retryAt);
        }

        return RuleResult.pass();
    }

    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            log.warn("Blank timezone on recipient — defaulting to UTC");
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}' — defaulting to UTC", timezone);
            return ZoneOffset.UTC;
        }
    }
}
