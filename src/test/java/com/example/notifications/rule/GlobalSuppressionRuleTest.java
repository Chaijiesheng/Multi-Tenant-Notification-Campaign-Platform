package com.example.notifications.rule;

import com.example.notifications.domain.*;
import com.example.notifications.repository.SuppressionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSuppressionRuleTest {

    @Mock
    private SuppressionRepository suppressionRepository;

    private GlobalSuppressionRule rule;
    private NotificationJob job;
    private Recipient recipient;
    private Tenant tenant;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        rule = new GlobalSuppressionRule(suppressionRepository);

        job = new NotificationJob();
        job.setId(1L);
        job.setTenantId(1L);
        job.setCampaignId(10L);
        job.setChannel(Channel.SMS);

        recipient = new Recipient();
        recipient.setId(100L);
        recipient.setTenantId(1L);
        recipient.setExternalId("user-external-001");

        tenant = new Tenant();
        tenant.setId(1L);

        campaign = new Campaign();
        campaign.setId(10L);
    }

    @Test
    void suppressed_returnsSkip() {
        when(suppressionRepository.existsByTenantIdAndRecipientExternalIdAndChannel(
                any(), anyString(), any())).thenReturn(true);

        RuleResult result = rule.evaluate(job, recipient, tenant, campaign);

        assertThat(result.outcome()).isEqualTo(RuleResult.Outcome.SKIP);
        assertThat(result.reason()).contains("suppressed");
    }

    @Test
    void notSuppressed_returnsPass() {
        when(suppressionRepository.existsByTenantIdAndRecipientExternalIdAndChannel(
                any(), anyString(), any())).thenReturn(false);

        RuleResult result = rule.evaluate(job, recipient, tenant, campaign);

        assertThat(result.outcome()).isEqualTo(RuleResult.Outcome.PASS);
    }
}
