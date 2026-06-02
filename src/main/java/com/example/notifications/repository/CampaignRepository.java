package com.example.notifications.repository;

import com.example.notifications.domain.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findAllByTenantId(Long tenantId);

    Optional<Campaign> findByIdAndTenantId(Long id, Long tenantId);

    // ── Deduplication (Fix 1) ────────────────────────────────────────────────
    /**
     * Returns 1 if this campaign was already COMPLETED or PARTIAL_FAILURE within
     * the given window — used by DeduplicationRule to prevent re-runs.
     */
    @Query(value = """
            SELECT COUNT(*) FROM campaigns
            WHERE id = :campaignId
            AND status IN ('COMPLETED','PARTIAL_FAILURE')
            AND updated_at >= :since
            """, nativeQuery = true)
    long countCompletedCampaignsSince(
            @Param("campaignId") Long campaignId,
            @Param("since") LocalDateTime since);

    // ── Atomic counter updates (Fix 2) ───────────────────────────────────────
    /**
     * Atomic SQL increment — safe under concurrent job processing.
     * Replaces the race-prone Java read-modify-write pattern.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "UPDATE campaigns SET sent_count = sent_count + 1 WHERE id = :id AND tenant_id = :tenantId",
            nativeQuery = true)
    void incrementSentCount(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "UPDATE campaigns SET failed_count = failed_count + 1 WHERE id = :id AND tenant_id = :tenantId",
            nativeQuery = true)
    void incrementFailedCount(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "UPDATE campaigns SET skipped_count = skipped_count + 1 WHERE id = :id AND tenant_id = :tenantId",
            nativeQuery = true)
    void incrementSkippedCount(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * Conditionally transitions a campaign to a terminal status only if it is
     * still PROCESSING. Returns the number of rows updated (1 = succeeded,
     * 0 = already transitioned by another thread — idempotent).
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE campaigns SET status = :status
            WHERE id = :id AND tenant_id = :tenantId AND status = 'PROCESSING'
            """, nativeQuery = true)
    int updateStatusIfProcessing(
            @Param("id") Long id,
            @Param("tenantId") Long tenantId,
            @Param("status") String status);

    // ── Credit check ─────────────────────────────────────────────────────────
    @Query("""
            SELECT COUNT(c) FROM Campaign c
            WHERE c.tenantId = :tenantId
            AND c.createdAt >= :since
            """)
    long countCampaignsSince(@Param("tenantId") Long tenantId, @Param("since") LocalDateTime since);
}
