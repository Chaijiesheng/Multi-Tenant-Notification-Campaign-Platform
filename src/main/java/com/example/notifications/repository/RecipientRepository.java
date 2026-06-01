package com.example.notifications.repository;

import com.example.notifications.domain.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {
    List<Recipient> findAllByCampaignIdAndTenantId(Long campaignId, Long tenantId);
}
