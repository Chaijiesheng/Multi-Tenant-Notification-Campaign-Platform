package com.example.notifications.rule;

import com.example.notifications.domain.Campaign;
import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import com.example.notifications.domain.Tenant;
import com.example.notifications.repository.DeliveryAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
public class DeduplicationRule implements NotificationRule {

    private final DeliveryAttemptRepository deliveryAttemptRepository;

    @Override
    public String name() {
        return "Deduplication";
    }

    @Override
    public RuleResult evaluate(NotificationJob job, Recipient recipient, Tenant tenant, Campaign campaign) {
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        long recentCount = deliveryAttemptRepository.countSuccessfulAttemptsByCampaignSince(
                job.getCampaignId(), fiveMinutesAgo);

        if (recentCount > 0) {
            String reason = "Duplicate: campaign " + job.getCampaignId()
                    + " already sent successfully within 5 minutes";
            log.debug("Deduplication discard: {}", reason);
            return RuleResult.discard(reason);
        }
        return RuleResult.pass();
    }
}
