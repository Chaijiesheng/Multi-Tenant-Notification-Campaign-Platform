package com.example.notifications.rule;

import com.example.notifications.domain.Campaign;
import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import com.example.notifications.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RuleEngine {

    private final List<NotificationRule> rules;

    @Transactional(readOnly = true)
    public RuleResult evaluate(NotificationJob job, Recipient recipient, Tenant tenant, Campaign campaign) {
        for (NotificationRule rule : rules) {
            RuleResult result = rule.evaluate(job, recipient, tenant, campaign);
            log.debug("Rule [{}] result: {} reason: {}", rule.name(), result.outcome(), result.reason());
            if (result.outcome() != RuleResult.Outcome.PASS) {
                return result;
            }
        }
        return RuleResult.pass();
    }
}
