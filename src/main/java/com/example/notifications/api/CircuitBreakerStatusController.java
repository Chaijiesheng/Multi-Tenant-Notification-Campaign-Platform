package com.example.notifications.api;

import com.example.notifications.resilience.ProviderCircuitBreakerDecorator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exposes per-channel circuit breaker state for operational visibility.
 * Accessible at GET /actuator/circuit-breakers.
 */
@RestController
@RequestMapping("/actuator/circuit-breakers")
@RequiredArgsConstructor
public class CircuitBreakerStatusController {

    private final ProviderCircuitBreakerDecorator providerCircuitBreakerDecorator;

    @GetMapping
    public Map<String, String> getCircuitBreakerStates() {
        return providerCircuitBreakerDecorator.getAllStates().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().name().toLowerCase(),
                        e -> e.getValue().name()
                ));
    }
}
