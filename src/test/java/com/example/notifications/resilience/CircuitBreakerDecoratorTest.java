package com.example.notifications.resilience;

import com.example.notifications.domain.Channel;
import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import com.example.notifications.provider.NotificationProvider;
import com.example.notifications.provider.ProviderFactory;
import com.example.notifications.provider.ProviderResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerDecoratorTest {

    @Mock
    private ProviderFactory mockFactory;

    @Mock
    private NotificationProvider mockProvider;

    private Map<Channel, CircuitBreaker> breakers;
    private ProviderCircuitBreakerDecorator decorator;
    private NotificationJob job;
    private Recipient recipient;

    @BeforeEach
    void setUp() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        breakers = new EnumMap<>(Channel.class);
        for (Channel ch : Channel.values()) {
            breakers.put(ch, registry.circuitBreaker("test-" + ch.name()));
        }

        when(mockFactory.getProvider(any())).thenReturn(mockProvider);

        decorator = new ProviderCircuitBreakerDecorator(mockFactory, breakers);

        job = new NotificationJob();
        job.setId(1L);
        job.setTenantId(1L);
        job.setCampaignId(10L);
        job.setChannel(Channel.EMAIL);

        recipient = new Recipient();
        recipient.setId(100L);
        recipient.setTenantId(1L);
    }

    @Test
    void providerSucceeds_returnsSuccess() {
        when(mockProvider.send(any(), any())).thenReturn(new ProviderResponse(true, "ok"));

        ProviderResponse result = decorator.send(job, recipient);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("ok");
        assertThat(decorator.getState(Channel.EMAIL)).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void providerFailsRepeatedly_circuitOpens() {
        // Create a circuit breaker with a low minimumNumberOfCalls for the test
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(4)
                .slidingWindowSize(4)
                .build();
        CircuitBreaker cb = CircuitBreaker.of("test-email-open", config);
        breakers.put(Channel.EMAIL, cb);

        when(mockProvider.send(any(), any())).thenThrow(new RuntimeException("provider down"));

        for (int i = 0; i < 4; i++) {
            ProviderResponse result = decorator.send(job, recipient);
            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("provider down");
        }

        assertThat(decorator.getState(Channel.EMAIL)).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void circuitOpen_returnsFailureWithoutCallingProvider() {
        CircuitBreaker cb = breakers.get(Channel.EMAIL);
        cb.transitionToOpenState();

        ProviderResponse result = decorator.send(job, recipient);

        assertThat(result.success()).isFalse();
        verify(mockProvider, never()).send(any(), any());
    }
}
