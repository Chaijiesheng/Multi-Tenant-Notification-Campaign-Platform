package com.example.notifications.rule;

import com.example.notifications.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleEngineTest {

    @Mock private NotificationRule rule1;
    @Mock private NotificationRule rule2;
    @Mock private NotificationRule rule3;
    @Mock private NotificationRule rule4;

    private RuleEngine ruleEngine;
    private NotificationJob job;
    private Recipient recipient;
    private Tenant tenant;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        ruleEngine = new RuleEngine(List.of(rule1, rule2, rule3, rule4));

        job = new NotificationJob();
        job.setId(1L);
        job.setTenantId(1L);
        job.setCampaignId(10L);
        job.setChannel(Channel.EMAIL);

        recipient = new Recipient();
        recipient.setId(100L);

        tenant = new Tenant();
        tenant.setId(1L);

        campaign = new Campaign();
        campaign.setId(10L);
    }

    @Test
    void allRulesPass_returnsFinalPass() {
        when(rule1.evaluate(any(), any(), any(), any())).thenReturn(RuleResult.pass());
        when(rule2.evaluate(any(), any(), any(), any())).thenReturn(RuleResult.pass());
        when(rule3.evaluate(any(), any(), any(), any())).thenReturn(RuleResult.pass());
        when(rule4.evaluate(any(), any(), any(), any())).thenReturn(RuleResult.pass());

        RuleResult result = ruleEngine.evaluate(job, recipient, tenant, campaign);

        assertThat(result.outcome()).isEqualTo(RuleResult.Outcome.PASS);
        verify(rule1).evaluate(any(), any(), any(), any());
        verify(rule2).evaluate(any(), any(), any(), any());
        verify(rule3).evaluate(any(), any(), any(), any());
        verify(rule4).evaluate(any(), any(), any(), any());
    }

    @Test
    void firstRuleSkips_shortCircuits() {
        when(rule1.evaluate(any(), any(), any(), any()))
                .thenReturn(RuleResult.skip("suppressed"));

        RuleResult result = ruleEngine.evaluate(job, recipient, tenant, campaign);

        assertThat(result.outcome()).isEqualTo(RuleResult.Outcome.SKIP);
        verify(rule1).evaluate(any(), any(), any(), any());
        verify(rule2, never()).evaluate(any(), any(), any(), any());
        verify(rule3, never()).evaluate(any(), any(), any(), any());
        verify(rule4, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void thirdRuleDelays_shortCircuitsAfterThird() {
        when(rule1.evaluate(any(), any(), any(), any())).thenReturn(RuleResult.pass());
        when(rule2.evaluate(any(), any(), any(), any())).thenReturn(RuleResult.pass());
        when(rule3.evaluate(any(), any(), any(), any()))
                .thenReturn(RuleResult.delay("DND active", LocalDateTime.now().plusHours(6)));

        RuleResult result = ruleEngine.evaluate(job, recipient, tenant, campaign);

        assertThat(result.outcome()).isEqualTo(RuleResult.Outcome.DELAY);
        verify(rule1).evaluate(any(), any(), any(), any());
        verify(rule2).evaluate(any(), any(), any(), any());
        verify(rule3).evaluate(any(), any(), any(), any());
        verify(rule4, never()).evaluate(any(), any(), any(), any());
    }
}
