package com.example.notifications.provider;

import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;

public interface NotificationProvider {
    ProviderResponse send(NotificationJob job, Recipient recipient);
}
