package com.example.notifications.resilience;

import com.example.notifications.domain.Channel;
import com.example.notifications.domain.NotificationJob;
import com.example.notifications.domain.Recipient;
import com.example.notifications.provider.NotificationProvider;
import com.example.notifications.provider.ProviderFactory;
import com.example.notifications.provider.ProviderResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Decorates provider calls with per-channel circuit breakers.
 * When a provider repeatedly fails (exception-based), the circuit opens and
 * calls are rejected immediately without hitting the provider, preventing
 * cascading failures.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderCircuitBreakerDecorator {

    private final ProviderFactory providerFactory;
    private final Map<Channel, CircuitBreaker> channelCircuitBreakers;

    /**
     * Sends a notification via the appropriate channel provider, guarded by the
     * per-channel circuit breaker. Both unchecked exceptions AND ProviderResponse(false)
     * from the provider are treated as failures that the circuit breaker records.
     */
    public ProviderResponse send(NotificationJob job, Recipient recipient) {
        CircuitBreaker cb = channelCircuitBreakers.get(job.getChannel());

        Supplier<ProviderResponse> decoratedSupplier = CircuitBreaker.decorateSupplier(cb, () -> {
            NotificationProvider provider = providerFactory.getProvider(job.getChannel());
            ProviderResponse response = provider.send(job, recipient);
            // Treat soft failure as a circuit-breaker-recordable exception so that
            // sustained provider soft failures also trip the breaker.
            if (!response.success()) {
                throw new ProviderSoftFailureException(response.message());
            }
            return response;
        });

        try {
            return decoratedSupplier.get();
        } catch (ProviderSoftFailureException e) {
            // Provider returned failure — circuit breaker already recorded it
            return new ProviderResponse(false, e.getMessage());
        } catch (Exception e) {
            // Circuit is open (CallNotPermittedException) or unexpected error
            log.warn("Circuit breaker OPEN or provider failed for channel={} jobId={}",
                    job.getChannel(), job.getId());
            return new ProviderResponse(false, "Circuit open or provider unavailable: " + e.getMessage());
        }
    }

    public CircuitBreaker.State getState(Channel channel) {
        return channelCircuitBreakers.get(channel).getState();
    }

    public Map<Channel, CircuitBreaker.State> getAllStates() {
        return channelCircuitBreakers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getState()));
    }

    /**
     * Internal exception used to signal a provider soft failure (success=false) to the
     * circuit breaker so it can record the outcome against the failure-rate threshold.
     */
    static class ProviderSoftFailureException extends RuntimeException {
        ProviderSoftFailureException(String message) {
            super(message);
        }
    }
}
