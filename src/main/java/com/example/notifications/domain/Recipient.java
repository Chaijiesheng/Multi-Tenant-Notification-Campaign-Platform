package com.example.notifications.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "recipients")
@Getter
@Setter
@NoArgsConstructor
public class Recipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private String externalId;

    @Column(length = 320)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 512)
    private String pushToken;

    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
