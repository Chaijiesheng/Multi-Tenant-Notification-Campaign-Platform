package com.example.notifications.worker;

import com.example.notifications.domain.*;
import com.example.notifications.ratelimit.ChannelRateLimiter;
import com.example.notifications.repository.*;
import com.example.notifications.resilience.ProviderCircuitBreakerDecorator;
import com.example.notifications.provider.ProviderResponse;
import com.example.notifications.rule.RuleEngine;
import com.example.notifications.rule.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Polls for PENDING notification jobs every 2 seconds and dispatches them
 * to the bounded async executor for processing.
 *
 * Fixes applied:
 *  - Fix 2: sentCount/failedCount/skippedCount updated via atomic SQL increments
 *            (campaignRepository.incrementSentCount etc.) instead of read-modify-write,
 *            eliminating the race condition under concurrent job processing.
 *  - Fix 5: NotificationSent outbox event persisted atomically on SENT status transition.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationDispatchWorker {

    private final NotificationJobRepository notificationJobRepository;
    private final RecipientRepository recipientRepository;
    private final CampaignRepository campaignRepository;
    private final TenantRepository tenantRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProviderCircuitBreakerDecorator providerCircuitBreakerDecorator;
    private final RuleEngine ruleEngine;
    private final ChannelRateLimiter rateLimiter;
    private final Executor asyncTaskExecutor;

    @Scheduled(fixedDelay = 2000)
    public void poll() {
        List<NotificationJob> jobs = notificationJobRepository.findPendingJobs(
                LocalDateTime.now(), PageRequest.ofSize(50));

        if (jobs.isEmpty()) return;

        log.debug("Dispatching {} pending jobs", jobs.size());

        for (NotificationJob job : jobs) {
            asyncTaskExecutor.execute(() -> processJob(job.getId()));
        }
    }

    @Transactional
    public void processJob(Long jobId) {
        Optional<NotificationJob> optJob = notificationJobRepository.findById(jobId);
        if (optJob.isEmpty()) return;

        NotificationJob job = optJob.get();
        if (job.getStatus() != NotificationStatus.PENDING) {
            return; // Idempotency guard — another thread took it first
        }

        try {
            LoggingContext.set(job.getTenantId(), job.getCampaignId(), job.getId(),
                    job.getRetryCount(), job.getChannel().name());
            LoggingContext.setStatus(NotificationStatus.PROCESSING.name());

            job.setStatus(NotificationStatus.PROCESSING);
            notificationJobRepository.save(job);

            Optional<Recipient> optRecipient = recipientRepository.findById(job.getRecipientId());
            if (optRecipient.isEmpty()) {
                log.warn("Recipient {} not found — skipping job {}", job.getRecipientId(), job.getId());
                job.setStatus(NotificationStatus.SKIPPED);
                notificationJobRepository.save(job);
                updateCampaignCounts(job);
                return;
            }

            Tenant tenant = tenantRepository.findById(job.getTenantId())
                    .orElseThrow(() -> new IllegalStateException("Tenant not found: " + job.getTenantId()));

            Campaign campaign = campaignRepository.findByIdAndTenantId(job.getCampaignId(), job.getTenantId())
                    .orElseThrow(() -> new IllegalStateException("Campaign not found: " + job.getCampaignId()));

            Recipient recipient = optRecipient.get();

            // ── Rule engine ─────────────────────────────────────────────────
            RuleResult ruleResult = ruleEngine.evaluate(job, recipient, tenant, campaign);

            switch (ruleResult.outcome()) {
                case SKIP -> {
                    log.info("Job {} SKIPPED by rule engine: {}", job.getId(), ruleResult.reason());
                    job.setStatus(NotificationStatus.SKIPPED);
                    notificationJobRepository.save(job);
                    updateCampaignCounts(job);
                    return;
                }
                case DELAY -> {
                    log.info("Job {} DELAYED until {}: {}", job.getId(), ruleResult.retryAt(), ruleResult.reason());
                    job.setStatus(NotificationStatus.PENDING);
                    job.setNextRetryAt(ruleResult.retryAt());
                    notificationJobRepository.save(job);
                    return;
                }
                case REJECT -> {
                    log.warn("Job {} REJECTED by rule engine: {}", job.getId(), ruleResult.reason());
                    job.setStatus(NotificationStatus.FAILED);
                    notificationJobRepository.save(job);
                    updateCampaignCounts(job);
                    return;
                }
                case DISCARD -> {
                    log.info("Job {} DISCARDED by deduplication: {}", job.getId(), ruleResult.reason());
                    job.setStatus(NotificationStatus.SKIPPED);
                    notificationJobRepository.save(job);
                    updateCampaignCounts(job);
                    return;
                }
                case PASS -> { /* fall through */ }
            }

            // ── Rate limiter ────────────────────────────────────────────────
            try {
                rateLimiter.acquire(job.getChannel());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Rate limiter interrupted for job {} — re-queuing", job.getId());
                job.setStatus(NotificationStatus.PENDING);
                notificationJobRepository.save(job);
                return;
            }

            // ── Provider call (circuit-breaker-guarded) ─────────────────────
            ProviderResponse response = providerCircuitBreakerDecorator.send(job, recipient);

            int attemptNumber = job.getRetryCount() + 1;
            saveDeliveryAttempt(job, attemptNumber, response);

            if (response.success()) {
                job.setStatus(NotificationStatus.SENT);
                LoggingContext.setStatus(NotificationStatus.SENT.name());
                log.info("Job {} SENT on attempt {}", job.getId(), attemptNumber);

                // Fix 5 — persist NotificationSent event in the same transaction
                outboxEventRepository.save(OutboxEvent.notificationSent(
                        job.getTenantId(), job.getCampaignId(),
                        job.getId(), job.getChannel().name()));

            } else {
                job.setRetryCount(job.getRetryCount() + 1);
                if (job.getRetryCount() >= job.getMaxRetries()) {
                    job.setStatus(NotificationStatus.FAILED);
                    LoggingContext.setStatus(NotificationStatus.FAILED.name());
                    log.warn("Job {} FAILED permanently after {} retries", job.getId(), job.getRetryCount());
                } else {
                    job.setStatus(NotificationStatus.PENDING);
                    long backoffSeconds = Math.min((long) Math.pow(2, job.getRetryCount() - 1), 60);
                    job.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
                    LoggingContext.setStatus(NotificationStatus.PENDING.name());
                    log.warn("Job {} will retry in {}s (attempt {})",
                            job.getId(), backoffSeconds, job.getRetryCount());
                }
            }

            notificationJobRepository.save(job);
            updateCampaignCounts(job);

        } catch (Exception e) {
            log.error("Unexpected error processing job {}: {}", jobId, e.getMessage(), e);
            try {
                NotificationJob j = notificationJobRepository.findById(jobId).orElse(null);
                if (j != null && j.getStatus() == NotificationStatus.PROCESSING) {
                    j.setStatus(NotificationStatus.PENDING);
                    notificationJobRepository.save(j);
                }
            } catch (Exception ex) {
                log.error("Failed to reset job {} status after error", jobId, ex);
            }
        } finally {
            LoggingContext.clear();
        }
    }

    private void saveDeliveryAttempt(NotificationJob job, int attemptNumber, ProviderResponse response) {
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setNotificationJobId(job.getId());
        attempt.setTenantId(job.getTenantId());
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStatus(response.success()
                ? DeliveryAttempt.AttemptStatus.SUCCESS
                : DeliveryAttempt.AttemptStatus.FAILURE);
        attempt.setProviderResponse(response.message());
        deliveryAttemptRepository.save(attempt);
    }

    /**
     * Fix 2 — atomic SQL increments replace the previous read-modify-write pattern.
     *
     * Previous pattern (UNSAFE under concurrency):
     *   campaign.setSentCount(campaign.getSentCount() + 1);  // two threads read 5, both write 6
     *   campaignRepository.save(campaign);
     *
     * New pattern (safe):
     *   UPDATE campaigns SET sent_count = sent_count + 1 WHERE id = ? AND tenant_id = ?
     *   — atomically applied by MySQL regardless of concurrent updates.
     *
     * Final status is determined purely from count queries (no stale entity reads)
     * and written with a conditional UPDATE (status = 'PROCESSING' guard) so
     * concurrent threads that both see activeJobs == 0 are idempotent.
     */
    private void updateCampaignCounts(NotificationJob job) {
        try {
            switch (job.getStatus()) {
                case SENT    -> campaignRepository.incrementSentCount(job.getCampaignId(), job.getTenantId());
                case FAILED  -> campaignRepository.incrementFailedCount(job.getCampaignId(), job.getTenantId());
                case SKIPPED -> campaignRepository.incrementSkippedCount(job.getCampaignId(), job.getTenantId());
                default      -> {} // PENDING / PROCESSING — no counter change
            }

            long activeJobs = notificationJobRepository.countActiveByCampaign(
                    job.getCampaignId(), job.getTenantId());

            if (activeJobs == 0) {
                long failedJobs = notificationJobRepository.countFailedByCampaign(
                        job.getCampaignId(), job.getTenantId());
                CampaignStatus finalStatus = failedJobs > 0
                        ? CampaignStatus.PARTIAL_FAILURE
                        : CampaignStatus.COMPLETED;

                // Conditional update: only transitions from PROCESSING → terminal.
                // Returns 0 if another thread already wrote the terminal status — safe to ignore.
                int updated = campaignRepository.updateStatusIfProcessing(
                        job.getCampaignId(), job.getTenantId(), finalStatus.name());

                if (updated > 0) {
                    log.info("Campaign {} completed — status={}", job.getCampaignId(), finalStatus);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update campaign counts for campaign {} / job {}",
                    job.getCampaignId(), job.getId(), e);
        }
    }
}
