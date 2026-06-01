package com.example.notifications.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OutboxIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notifications_outbox_test")
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
    private JdbcTemplate jdbcTemplate;

    private static final String CSV =
            "external_id,email,phone,push_token,timezone\n" +
            "outbox-user1,outbox1@example.com,+1111111111,tok1,UTC\n";

    @Test
    void createCampaign_outboxEventIsCreatedAndProcessed() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", "1");
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", "Outbox Test Campaign");
        body.add("channel", "EMAIL");
        body.add("messageBody", "Hello outbox!");
        body.add("file", new ByteArrayResource(CSV.getBytes()) {
            @Override
            public String getFilename() { return "recipients.csv"; }
        });

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Assert at least one outbox event was created with the correct type
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'CampaignCreated'",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);

        // Assert that the OutboxPoller marks it as processed within 6 seconds
        await()
                .atMost(Duration.ofSeconds(6))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Map<String, Object> row = jdbcTemplate.queryForMap(
                            "SELECT processed, processed_at FROM outbox_events WHERE event_type = 'CampaignCreated' LIMIT 1");
                    assertThat(((Number) row.get("processed")).intValue()).isEqualTo(1);
                    assertThat(row.get("processed_at")).isNotNull();
                });
    }
}
