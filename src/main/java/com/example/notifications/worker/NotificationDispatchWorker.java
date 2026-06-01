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
 * Sprint 3 changes:
 *   - ProviderFactory replaced by ProviderCircuitBreakerDecorator for circuit-breaker-guarded sends.
 *   - Executor field renamed to asyncTaskExecutor (matches VirtualThreadConfig bean name).
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
        // Idempotency guard — another thread may have already taken it
        if (job.getStatus() != NotificationStatus.PENDING) {
            return;
        }

        try {
            LoggingContext.set(job.getTenantId(), job.getCampaignId(), job.getId(),
                    job.getRetryCount(), job.getChannel().name());
            LoggingContext.setStatus(NotificationStatus.PROCESSING.name());

            job.setStatus(NotificationStatus.PROCESSING);
            notificationJobRepository.save(job);

            // Load related entities
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

            // Rule engine evaluation — short-circuits on first non-PASS
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
                    log.info("Job {} DELAYED by rule engine until {}: {}",
                            job.getId(), ruleResult.retryAt(), ruleResult.reason());
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
                case PASS -> {
                    // fall through to rate limiter + provider
                }
            }

            // Rate limit acquisition (virtual-thread-friendly polling)
            try {
                rateLimiter.acquire(job.getChannel());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Rate limiter interrupted for job {} — re-queuing", job.getId());
                job.setStatus(NotificationStatus.PENDING);
                notificationJobRepository.save(job);
                return;
            }

            // Circuit-breaker-guarded provider call
            ProviderResponse response = providerCircuitBreakerDecorator.send(job, recipient);

            int attemptNumber = job.getRetryCount() + 1;
            saveDeliveryAttempt(job, attemptNumber, response);

            if (response.success()) {
                job.setStatus(NotificationStatus.SENT);
                LoggingContext.setStatus(NotificationStatus.SENT.name());
                log.info("Job {} SENT on attempt {}", job.getId(), attemptNumber);
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

    private void updateCampaignCounts(NotificationJob job) {
        try {
            Optional<Campaign> optCampaign = campaignRepository.findByIdAndTenantId(
                    job.getCampaignId(), job.getTenantId());
            if (optCampaign.isEmpty()) return;

            Campaign campaign = optCampaign.get();

            if (job.getStatus() == NotificationStatus.SENT) {
                campaign.setSentCount(campaign.getSentCount() + 1);
            } else if (job.getStatus() == NotificationStatus.FAILED) {
                campaign.setFailedCount(campaign.getFailedCount() + 1);
            } else if (job.getStatus() == NotificationStatus.SKIPPED) {
                campaign.setSkippedCount(campaign.getSkippedCount() + 1);
            }

            long activeJobs = notificationJobRepository.countActiveByCampaign(
                    job.getCampaignId(), job.getTenantId());
            if (activeJobs == 0) {
                boolean hasFailures = campaign.getFailedCount() > 0;
                campaign.setStatus(hasFailures ? CampaignStatus.PARTIAL_FAILURE : CampaignStatus.COMPLETED);
                log.info("Campaign {} completed — sent={}, failed={}, skipped={}",
                        campaign.getId(), campaign.getSentCount(),
                        campaign.getFailedCount(), campaign.getSkippedCount());
            }

            campaignRepository.save(campaign);
        } catch (Exception e) {
            log.error("Failed to update campaign counts for campaign {} / job {}",
                    job.getCampaignId(), job.getId(), e);
        }
    }
}
