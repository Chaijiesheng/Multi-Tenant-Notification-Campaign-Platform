package com.example.notifications.integration;

import com.example.notifications.domain.Channel;
import com.example.notifications.resilience.ProviderCircuitBreakerDecorator;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CircuitBreakerIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notifications_cb_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProviderCircuitBreakerDecorator providerCircuitBreakerDecorator;

    @Autowired
    private Map<Channel, CircuitBreaker> channelCircuitBreakers;

    private String statusUrl() {
        return "http://localhost:" + port + "/actuator/circuit-breakers";
    }

    @Test
    @SuppressWarnings("unchecked")
    void circuitBreakerStatus_allClosed_onStartup() {
        ResponseEntity<Map> response = restTemplate.getForEntity(statusUrl(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> body = response.getBody();

        assertThat(body).containsKey("email");
        assertThat(body).containsKey("sms");
        assertThat(body).containsKey("push");

        assertThat(body.get("email")).isEqualToIgnoringCase("CLOSED");
        assertThat(body.get("sms")).isEqualToIgnoringCase("CLOSED");
        assertThat(body.get("push")).isEqualToIgnoringCase("CLOSED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void forceOpenCircuit_statusEndpointReflectsOpen() {
        // Force the SMS circuit breaker to OPEN state
        channelCircuitBreakers.get(Channel.SMS).transitionToOpenState();

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(statusUrl(), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, String> body = response.getBody();
            assertThat(body.get("sms")).isEqualToIgnoringCase("OPEN");
        } finally {
            // Restore — reset returns circuit to CLOSED state
            channelCircuitBreakers.get(Channel.SMS).reset();
        }
    }
}
