package com.example.notifications.repository;

import com.example.notifications.domain.NotificationJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, Long> {

    Optional<NotificationJob> findByIdempotencyKey(String key);

    @Query("SELECT j FROM NotificationJob j WHERE j.status = 'PENDING' AND (j.nextRetryAt IS NULL OR j.nextRetryAt <= :now)")
    List<NotificationJob> findPendingJobs(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT j FROM NotificationJob j WHERE j.campaignId = :campaignId AND j.tenantId = :tenantId AND j.status = 'FAILED'")
    List<NotificationJob> findFailedJobsByCampaign(@Param("campaignId") Long campaignId, @Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(j) FROM NotificationJob j WHERE j.campaignId = :campaignId AND j.tenantId = :tenantId AND j.status NOT IN ('SENT','FAILED','SKIPPED')")
    long countActiveByCampaign(@Param("campaignId") Long campaignId, @Param("tenantId") Long tenantId);

    @Query("""
            SELECT COUNT(j) FROM NotificationJob j
            WHERE j.tenantId = :tenantId
            AND j.status = 'SENT'
            AND j.createdAt >= :since
            """)
    long countSentMessagesSince(@Param("tenantId") Long tenantId, @Param("since") LocalDateTime since);
}
