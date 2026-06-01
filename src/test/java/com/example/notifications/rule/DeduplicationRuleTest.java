package com.example.notifications.rule;

import com.example.notifications.domain.*;
import com.example.notifications.repository.DeliveryAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeduplicationRuleTest {

    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;

    private DeduplicationRule rule;
    private NotificationJob job;
    private Recipient recipient;
    private Tenant tenant;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        rule = new DeduplicationRule(deliveryAttemptRepository);

        job = new NotificationJob();
        job.setId(1L);
        job.setTenantId(1L);
        job.setCampaignId(10L);
        job.setChannel(Channel.EMAIL);

        recipient = new Recipient();
        recipient.setId(100L);
        recipient.setTenantId(1L);

        tenant = new Tenant();
        tenant.setId(1L);

        campaign = new Campaign();
        campaign.setId(10L);
    }

    @Test
    void noRecentSuccessfulAttempts_returnsPass() {
        when(deliveryAttemptRepository.countSuccessfulAttemptsByCampaignSince(anyLong(), any()))
                .thenReturn(0L);

        RuleResult result = rule.evaluate(job, recipient, tenant, campaign);

        assertThat(result.outcome()).isEqualTo(RuleResult.Outcome.PASS);
    }

    @Test
    void recentSuccessfulAttempts_returnsDiscard() {
        when(deliveryAttemptRepository.countSuccessfulAttemptsByCampaignSince(anyLong(), any()))
                .thenReturn(3L);

        RuleResult result = rule.evaluate(job, recipient, tenant, campaign);

        assertThat(result.outcome()).isEqualTo(RuleResult.Outcome.DISCARD);
        assertThat(result.reason()).contains("already sent successfully");
    }
}
