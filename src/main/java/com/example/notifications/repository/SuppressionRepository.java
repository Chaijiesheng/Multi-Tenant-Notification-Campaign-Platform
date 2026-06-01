package com.example.notifications.repository;

import com.example.notifications.domain.Channel;
import com.example.notifications.domain.SuppressionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuppressionRepository extends JpaRepository<SuppressionEntry, Long> {

    boolean existsByTenantIdAndRecipientExternalIdAndChannel(
            Long tenantId, String recipientExternalId, Channel channel);

    Optional<SuppressionEntry> findByTenantIdAndRecipientExternalIdAndChannel(
            Long tenantId, String recipientExternalId, Channel channel);
}
