package com.example.notifications.rule;

import com.example.notifications.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DndWindowRuleTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private Recipient recipient(String timezone) {
        Recipient r = new Recipient();
        r.setId(1L);
        r.setTenantId(1L);
        r.setTimezone(timezone);
        return r;
    }

    private NotificationJob job(Channel channel) {
        NotificationJob j = new NotificationJob();
        j.setId(1L);
        j.setTenantId(1L);
        j.setCampaignId(1L);
        j.setChannel(channel);
        return j;
    }

    private Campaign campaign(boolean transactional) {
        Campaign c = new Campaign();
        c.setId(1L);
        c.setTransactional(transactional);
        return c;
    }

    private Tenant tenant() {
        Tenant t = new Tenant();
        t.setId(1L);
        return t;
    }

    /** Fix the clock to 23:00 UTC which is 19:00 New_York (EST = UTC-4 in summer).
        To get 23:00 NY we need 04:00 UTC next day (UTC-5 winter) or 03:00 UTC (UTC-4 summer).
        We'll use 2025-01-15 (winter, UTC-5) so NY 23:00 = UTC 04:00 the next day = 2025-01-16T04:00Z */
    private Clock clockAt_23h_NewYork() {
        // 2025-01-16T04:00:00Z → New_York sees 2025-01-15T23:00:00-05:00
        return Clock.fixed(Instant.parse("2025-01-16T04:00:00Z"), ZoneId.of("UTC"));
    }

    /** 09:00 NY in winter (UTC-5) = 14:00 UTC */
    private Clock clockAt_09h_NewYork() {
        return Clock.fixed(Instant.parse("2025-01-15T14:00:00Z"), ZoneId.of("UTC"));
    }

    @Test
    void emailChannel_alwaysPasses() {
        DndWindowRule rule = new DndWindowRule(clockAt_23h_NewYork());
        RuleResult result = rule.evaluate(job(Channel.EMAIL), recipient("America/New_York"), tenant(), campaign(false));
        assertThat(result.outcome()).isEqualTo(RuleResult.Outcome.PASS);
    }

    @Test
    void smsChannel_duringDndWindow_returnsDelay() {
        DndWindowRule rule = new DndWindowRule(clockAt_23h_NewYork());
        RuleResult result = rule.evaluate(job(Channel.SMS), recipient("America/New_York"), tenant(), campaign(false));

        assertThat(result.outcome()).isEqualTo(RuleResult.Outcome.DELAY);
        assertThat(result.retryAt()).isNotNull();
        // retryAt should be next 08:00 NY = 2025-01-16T08:00-05:00 = 2025-01-16T13:00 UTC
        assertThat(result.retryAt().getHour()).isEqualTo(13);
    }

    @Test
    void smsChannel_outside_dndWindow_returnsPass() {
        DndWindowRule rule = new DndWindowRule(clockAt_09h_NewYork());
        RuleResult result = rule.evaluate(job(Channel.SMS), recipient("America/New_York"), tenant(), campaign(false));
        assertThat(result.outcome()).isEqualTo(RuleResult.Outcome.PASS);
    }

    @Test
    void smsChannel_transactionalCampaign_duringDnd_returnsPass() {
        DndWindowRule rule = new DndWindowRule(clockAt_23h_NewYork());
        RuleResult result = rule.evaluate(job(Channel.SMS), recipient("America/New_York"), tenant(), campaign(true));
        assertThat(result.outcome()).isEqualTo(RuleResult.Outcome.PASS);
    }
}
