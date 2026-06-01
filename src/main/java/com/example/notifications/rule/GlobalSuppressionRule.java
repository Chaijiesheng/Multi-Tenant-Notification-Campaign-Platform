package com.example.notifications.rule;

import com.example.notifications.domain.Campaign;
import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import com.example.notifications.domain.Tenant;
import com.example.notifications.repository.SuppressionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class GlobalSuppressionRule implements NotificationRule {

    private final SuppressionRepository suppressionRepository;

    @Override
    public String name() {
        return "GlobalSuppression";
    }

    @Override
    public RuleResult evaluate(NotificationJob job, Recipient recipient, Tenant tenant, Campaign campaign) {
        boolean suppressed = suppressionRepository.existsByTenantIdAndRecipientExternalIdAndChannel(
                recipient.getTenantId(), recipient.getExternalId(), job.getChannel());

        if (suppressed) {
            String maskedId = maskExternalId(recipient.getExternalId());
            String reason = "Recipient " + maskedId + " is suppressed for channel " + job.getChannel();
            log.debug("Suppression hit: {}", reason);
            return RuleResult.skip(reason);
        }
        return RuleResult.pass();
    }

    private String maskExternalId(String externalId) {
        if (externalId == null || externalId.length() <= 3) return "****";
        return externalId.substring(0, 3) + "****";
    }
}
