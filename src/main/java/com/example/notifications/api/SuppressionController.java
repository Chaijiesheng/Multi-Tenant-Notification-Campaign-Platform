package com.example.notifications.api;

import com.example.notifications.domain.Channel;
import com.example.notifications.domain.SuppressionEntry;
import com.example.notifications.exception.TenantNotFoundException;
import com.example.notifications.repository.SuppressionRepository;
import com.example.notifications.repository.TenantRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/suppression")
@RequiredArgsConstructor
@Slf4j
public class SuppressionController {

    private final SuppressionRepository suppressionRepository;
    private final TenantRepository tenantRepository;

    @PostMapping
    public ResponseEntity<Void> addSuppression(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @Valid @RequestBody SuppressionRequest request) {

        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        try {
            SuppressionEntry entry = new SuppressionEntry();
            entry.setTenantId(tenantId);
            entry.setRecipientExternalId(request.getRecipientExternalId());
            entry.setChannel(request.getChannel());
            suppressionRepository.save(entry);
            log.info("Suppression added: tenant={} externalId={} channel={}",
                    tenantId, request.getRecipientExternalId(), request.getChannel());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (DataIntegrityViolationException e) {
            // Already suppressed — idempotent
            log.debug("Suppression already exists for tenant={} externalId={} channel={}",
                    tenantId, request.getRecipientExternalId(), request.getChannel());
            return ResponseEntity.ok().build();
        }
    }

    @DeleteMapping("/{recipientExternalId}/{channel}")
    public ResponseEntity<Void> removeSuppression(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String recipientExternalId,
            @PathVariable Channel channel) {

        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        SuppressionEntry entry = suppressionRepository
                .findByTenantIdAndRecipientExternalIdAndChannel(tenantId, recipientExternalId, channel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Suppression not found for externalId=" + recipientExternalId + " channel=" + channel));

        suppressionRepository.delete(entry);
        log.info("Suppression removed: tenant={} externalId={} channel={}", tenantId, recipientExternalId, channel);
        return ResponseEntity.noContent().build();
    }
}
