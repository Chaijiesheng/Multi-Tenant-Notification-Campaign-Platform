package com.example.notifications.config;

import com.example.notifications.repository.CampaignRepository;
import com.example.notifications.repository.DeliveryAttemptRepository;
import com.example.notifications.repository.NotificationJobRepository;
import com.example.notifications.repository.SuppressionRepository;
import com.example.notifications.rule.DeduplicationRule;
import com.example.notifications.rule.DndWindowRule;
import com.example.notifications.rule.GlobalSuppressionRule;
import com.example.notifications.rule.TenantCreditCheckRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class RuleEngineConfig {

    @Bean
    @Order(1)
    public GlobalSuppressionRule globalSuppressionRule(SuppressionRepository suppressionRepository) {
        return new GlobalSuppressionRule(suppressionRepository);
    }

    @Bean
    @Order(2)
    public DndWindowRule dndWindowRule() {
        return new DndWindowRule();
    }

    @Bean
    @Order(3)
    public TenantCreditCheckRule tenantCreditCheckRule(
            CampaignRepository campaignRepository,
            NotificationJobRepository notificationJobRepository) {
        return new TenantCreditCheckRule(campaignRepository, notificationJobRepository);
    }

    @Bean
    @Order(4)
    public DeduplicationRule deduplicationRule(DeliveryAttemptRepository deliveryAttemptRepository) {
        return new DeduplicationRule(deliveryAttemptRepository);
    }
}
