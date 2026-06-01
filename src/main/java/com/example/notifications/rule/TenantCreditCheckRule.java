package com.example.notifications.rule;

import com.example.notifications.domain.Campaign;
import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import com.example.notifications.domain.Tenant;
import com.example.notifications.repository.CampaignRepository;
import com.example.notifications.repository.NotificationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
public class TenantCreditCheckRule implements NotificationRule {

    private final CampaignRepository campaignRepository;
    private final NotificationJobRepository notificationJobRepository;

    @Override
    public String name() {
        return "TenantCreditCheck";
    }

    @Override
    public RuleResult evaluate(NotificationJob job, Recipient recipient, Tenant tenant, Campaign campaign) {
        LocalDateTime startOfMonth = LocalDateTime.now()
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        long campaignCount = campaignRepository.countCampaignsSince(tenant.getId(), startOfMonth);
        if (campaignCount >= tenant.getMonthlyCampaignLimit()) {
            String reason = "Tenant " + tenant.getSlug() + " has reached monthly campaign limit";
            log.warn("Credit check failed: {}", reason);
            return RuleResult.reject(reason);
        }

        long messageCount = notificationJobRepository.countSentMessagesSince(tenant.getId(), startOfMonth);
        if (messageCount >= tenant.getMonthlyMessageLimit()) {
            String reason = "Tenant " + tenant.getSlug() + " has reached monthly message limit";
            log.warn("Credit check failed: {}", reason);
            return RuleResult.reject(reason);
        }

        return RuleResult.pass();
    }
}
