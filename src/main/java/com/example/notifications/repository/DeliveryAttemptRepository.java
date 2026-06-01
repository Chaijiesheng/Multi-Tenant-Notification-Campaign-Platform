package com.example.notifications.repository;

import com.example.notifications.domain.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {

    List<DeliveryAttempt> findByNotificationJobId(Long jobId);

    @Query("""
            SELECT COUNT(a) FROM DeliveryAttempt a
            WHERE a.notificationJobId IN (
                SELECT j.id FROM NotificationJob j WHERE j.campaignId = :campaignId
            )
            AND a.status = 'SUCCESS'
            AND a.attemptedAt >= :since
            """)
    long countSuccessfulAttemptsByCampaignSince(
            @Param("campaignId") Long campaignId,
            @Param("since") LocalDateTime since);
}
