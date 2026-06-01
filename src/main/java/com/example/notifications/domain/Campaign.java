package com.example.notifications.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('EMAIL','SMS','PUSH')")
    private Channel channel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String messageBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('DRAFT','PROCESSING','COMPLETED','PARTIAL_FAILURE')")
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(nullable = false)
    private int totalRecipients = 0;

    @Column(nullable = false)
    private int sentCount = 0;

    @Column(nullable = false)
    private int failedCount = 0;

    @Column(nullable = false)
    private int skippedCount = 0;

    @Column(name = "is_transactional", nullable = false)
    private boolean transactional = false;

    @Column(name = "message_body_hash", length = 64)
    private String messageBodyHash;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    void computeHash() {
        this.updatedAt = LocalDateTime.now();
        if (this.messageBody != null) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(this.messageBody.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hashBytes) {
                    sb.append(String.format("%02x", b));
                }
                this.messageBodyHash = sb.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 algorithm not available", e);
            }
        }
    }
}
