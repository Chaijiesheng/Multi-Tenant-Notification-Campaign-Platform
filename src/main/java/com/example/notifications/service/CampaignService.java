package com.example.notifications.service;

import com.example.notifications.api.CampaignResponse;
import com.example.notifications.api.CreateCampaignRequest;
import com.example.notifications.api.RetryFailuresResponse;
import com.example.notifications.domain.*;
import com.example.notifications.exception.CampaignNotFoundException;
import com.example.notifications.exception.TenantNotFoundException;
import com.example.notifications.repository.*;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final TenantRepository tenantRepository;
    private final CampaignRepository campaignRepository;
    private final RecipientRepository recipientRepository;
    private final NotificationJobRepository notificationJobRepository;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * Creates a campaign, streams the CSV to create recipients and notification jobs,
     * and writes a CampaignCreated outbox event — all in a single atomic transaction.
     *
     * Fix 6: campaign.transactional is set from CreateCampaignRequest.isTransactional(),
     * allowing API callers to flag OTP / critical campaigns that bypass DND quiet hours.
     */
    @Transactional
    public CampaignResponse createCampaign(Long tenantId, CreateCampaignRequest request, MultipartFile csvFile) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        Campaign campaign = new Campaign();
        campaign.setTenantId(tenantId);
        campaign.setName(request.getName());
        campaign.setChannel(request.getChannel());
        campaign.setMessageBody(request.getMessageBody());
        campaign.setTransactional(request.isTransactional()); // Fix 6
        campaign.setStatus(CampaignStatus.DRAFT);
        campaign = campaignRepository.save(campaign);

        int recipientCount = parseCsvAndCreateJobs(tenantId, campaign, csvFile);

        campaign.setTotalRecipients(recipientCount);
        campaign.setStatus(CampaignStatus.PROCESSING);
        campaign = campaignRepository.save(campaign);

        // Transactional Outbox — same commit covers Campaign + Recipients + Jobs + OutboxEvent
        outboxEventRepository.save(OutboxEvent.campaignCreated(
                tenantId, campaign.getId(), campaign.getName(), campaign.getChannel()));

        log.info("Campaign {} created with {} recipients for tenant {} (transactional={})",
                campaign.getId(), recipientCount, tenantId, campaign.isTransactional());
        return CampaignResponse.from(campaign);
    }

    private int parseCsvAndCreateJobs(Long tenantId, Campaign campaign, MultipartFile csvFile) {
        int count = 0;
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8))) {

            String[] header = reader.readNext();
            if (header == null) {
                log.warn("CSV file is empty for campaign {}", campaign.getId());
                return 0;
            }

            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length == 0) continue;
                String externalId = row[0].trim();
                if (externalId.isBlank()) {
                    log.warn("Skipping CSV row with blank external_id in campaign {}", campaign.getId());
                    continue;
                }

                String email     = row.length > 1 ? row[1].trim() : null;
                String phone     = row.length > 2 ? row[2].trim() : null;
                String pushToken = row.length > 3 ? row[3].trim() : null;
                String timezone  = row.length > 4 ? row[4].trim() : "UTC";

                Recipient recipient = new Recipient();
                recipient.setTenantId(tenantId);
                recipient.setCampaignId(campaign.getId());
                recipient.setExternalId(externalId);
                recipient.setEmail(email != null && !email.isBlank() ? email : null);
                recipient.setPhone(phone != null && !phone.isBlank() ? phone : null);
                recipient.setPushToken(pushToken != null && !pushToken.isBlank() ? pushToken : null);
                recipient.setTimezone(timezone.isBlank() ? "UTC" : timezone);
                recipient = recipientRepository.save(recipient);

                IdempotencyKey key = IdempotencyKey.of(
                        tenantId, campaign.getId(), recipient.getId(), campaign.getChannel());

                if (notificationJobRepository.findByIdempotencyKey(key.value()).isEmpty()) {
                    NotificationJob job = new NotificationJob();
                    job.setTenantId(tenantId);
                    job.setCampaignId(campaign.getId());
                    job.setRecipientId(recipient.getId());
                    job.setChannel(campaign.getChannel());
                    job.setIdempotencyKey(key.value());
                    job.setStatus(NotificationStatus.PENDING);
                    notificationJobRepository.save(job);
                    count++;
                } else {
                    log.warn("Duplicate idempotency key {} — skipping job creation", key.value());
                }
            }
        } catch (IOException | CsvValidationException e) {
            throw new IllegalArgumentException("Failed to parse CSV file: " + e.getMessage(), e);
        }
        return count;
    }

    public List<CampaignResponse> getCampaigns(Long tenantId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        return campaignRepository.findAllByTenantId(tenantId)
                .stream()
                .map(CampaignResponse::from)
                .toList();
    }

    public CampaignResponse getCampaign(Long tenantId, Long campaignId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        Campaign campaign = campaignRepository.findByIdAndTenantId(campaignId, tenantId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId, tenantId));
        return CampaignResponse.from(campaign);
    }

    @Transactional
    public RetryFailuresResponse retryCampaignFailures(Long tenantId, Long campaignId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        Campaign campaign = campaignRepository.findByIdAndTenantId(campaignId, tenantId)
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));

        List<NotificationJob> failedJobs =
                notificationJobRepository.findFailedJobsByCampaign(campaignId, tenantId);

        if (failedJobs.isEmpty()) {
            return new RetryFailuresResponse(
                    campaignId, 0, "No failed jobs found for campaign " + campaignId);
        }

        for (NotificationJob job : failedJobs) {
            job.setStatus(NotificationStatus.PENDING);
            job.setRetryCount(0);
            job.setNextRetryAt(null);
        }
        notificationJobRepository.saveAll(failedJobs);

        campaign.setStatus(CampaignStatus.PROCESSING);
        campaignRepository.save(campaign);

        log.info("Requeued {} failed jobs for campaignId={} tenantId={}",
                failedJobs.size(), campaignId, tenantId);

        return new RetryFailuresResponse(
                campaignId, failedJobs.size(),
                "Successfully requeued " + failedJobs.size() + " jobs");
    }
}
