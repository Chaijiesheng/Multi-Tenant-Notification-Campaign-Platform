package com.example.notifications.api;

import com.example.notifications.domain.Channel;
import com.example.notifications.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/campaigns")
@RequiredArgsConstructor
@Slf4j
public class CampaignController {

    private final CampaignService campaignService;

    /**
     * Fix 6 — 'transactional' multipart field lets callers flag OTP / critical
     * campaigns, bypassing the DND quiet-hours rule for SMS and Push.
     * Field is optional; defaults to false (promotional).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignResponse> createCampaign(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestPart("name") String name,
            @RequestPart("channel") String channel,
            @RequestPart("messageBody") String messageBody,
            @RequestPart(value = "transactional", required = false) String transactional,
            @RequestPart("file") MultipartFile file) {

        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setName(name);
        request.setChannel(Channel.valueOf(channel.toUpperCase()));
        request.setMessageBody(messageBody);
        request.setTransactional(Boolean.parseBoolean(transactional));

        CampaignResponse response = campaignService.createCampaign(tenantId, request, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getCampaigns(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return ResponseEntity.ok(campaignService.getCampaigns(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getCampaign(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaign(tenantId, id));
    }

    @PostMapping("/{id}/retry-failures")
    public ResponseEntity<RetryFailuresResponse> retryFailures(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable Long id) {
        RetryFailuresResponse result = campaignService.retryCampaignFailures(tenantId, id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}
