package com.example.notifications.service;

import com.example.notifications.domain.OutboxEvent;

public record OutboxEventPublished(OutboxEvent event) {}
