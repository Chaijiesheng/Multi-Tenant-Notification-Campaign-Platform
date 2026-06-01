package com.example.notifications.rule;

import com.example.notifications.domain.Campaign;
import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import com.example.notifications.domain.Tenant;

public interface NotificationRule {
    String name();
    RuleResult evaluate(NotificationJob job, Recipient recipient, Tenant tenant, Campaign campaign);
}
