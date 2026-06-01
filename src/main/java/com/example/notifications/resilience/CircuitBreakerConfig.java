package com.example.notifications.resilience;

import com.example.notifications.domain.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Programmatic Resilience4j circuit breaker configuration — one breaker per channel.
 * Using programmatic configuration (not @CircuitBreaker annotations) for full control
 * and testability.
 */
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config =
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(10)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .recordExceptions(RuntimeException.class, Exception.class)
                        .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public Map<Channel, CircuitBreaker> channelCircuitBreakers(CircuitBreakerRegistry registry) {
        EnumMap<Channel, CircuitBreaker> breakers = new EnumMap<>(Channel.class);
        for (Channel channel : Channel.values()) {
            breakers.put(channel,
                    registry.circuitBreaker("provider-" + channel.name().toLowerCase()));
        }
        return breakers;
    }
}
