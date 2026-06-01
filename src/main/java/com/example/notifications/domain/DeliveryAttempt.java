package com.example.notifications.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_attempts")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long notificationJobId;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('SUCCESS','FAILURE')")
    private AttemptStatus status;

    @Column(columnDefinition = "TEXT")
    private String providerResponse;

    @Column(nullable = false, updatable = false)
    private LocalDateTime attemptedAt = LocalDateTime.now();

    public enum AttemptStatus {
        SUCCESS, FAILURE
    }
}
