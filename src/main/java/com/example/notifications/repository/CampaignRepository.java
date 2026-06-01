package com.example.notifications.repository;

import com.example.notifications.domain.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findAllByTenantId(Long tenantId);

    Optional<Campaign> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            SELECT COUNT(c) FROM Campaign c
            WHERE c.tenantId = :tenantId
            AND c.createdAt >= :since
            """)
    long countCampaignsSince(@Param("tenantId") Long tenantId, @Param("since") LocalDateTime since);
}
