package com.example.notifications.rule;

import com.example.notifications.domain.Campaign;
import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import com.example.notifications.domain.Tenant;
import com.example.notifications.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * Discards jobs for campaigns that were already fully completed within the last
 * 5 minutes, preventing accidental re-runs of a recently finished campaign.
 *
 * Fix: previously used countSuccessfulAttemptsByCampaignSince(), which incorrectly
 * discarded all remaining jobs the moment the first job in a campaign succeeded.
 * Now checks whether the campaign itself reached COMPLETED / PARTIAL_FAILURE within
 * the deduplication window — so mid-campaign sends are never affected.
 */
@Slf4j
@RequiredArgsConstructor
public class DeduplicationRule implements NotificationRule {

    private final CampaignRepository campaignRepository;

    @Override
    public String name() {
        return "Deduplication";
    }

    @Override
    public RuleResult evaluate(NotificationJob job, Recipient recipient, Tenant tenant, Campaign campaign) {
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        long recentCompleted = campaignRepository.countCompletedCampaignsSince(
                job.getCampaignId(), fiveMinutesAgo);

        if (recentCompleted > 0) {
            String reason = "Campaign " + job.getCampaignId()
                    + " was already completed within the last 5 minutes";
            log.debug("Deduplication discard: {}", reason);
            return RuleResult.discard(reason);
        }
        return RuleResult.pass();
    }
}
