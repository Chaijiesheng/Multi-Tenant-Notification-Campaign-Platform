package com.example.notifications.provider;

import com.example.notifications.domain.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderFactory {

    private final EmailProvider emailProvider;
    private final SmsProvider smsProvider;
    private final PushProvider pushProvider;

    public NotificationProvider getProvider(Channel channel) {
        return switch (channel) {
            case EMAIL -> emailProvider;
            case SMS -> smsProvider;
            case PUSH -> pushProvider;
        };
    }
}
