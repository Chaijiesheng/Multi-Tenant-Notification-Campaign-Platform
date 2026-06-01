package com.example.notifications.integration;

import com.example.notifications.domain.Channel;
import com.example.notifications.domain.NotificationStatus;
import com.example.notifications.domain.SuppressionEntry;
import com.example.notifications.repository.NotificationJobRepository;
import com.example.notifications.repository.SuppressionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuppressionIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notifications_suppression_test")
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
    private SuppressionRepository suppressionRepository;

    @Autowired
    private NotificationJobRepository notificationJobRepository;

    @Test
    void suppressedRecipient_jobIsSkipped() {
        // Insert suppression for tenant 1
        SuppressionEntry entry = new SuppressionEntry();
        entry.setTenantId(1L);
        entry.setRecipientExternalId("sup-001");
        entry.setChannel(Channel.SMS);
        suppressionRepository.save(entry);

        // Create campaign (channel=SMS) with the suppressed user in the CSV
        String csv = "external_id,email,phone,push_token,timezone\n" +
                     "sup-001,sup001@example.com,+1234567890,token1,UTC\n";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", "1");
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", "Suppression Test Campaign");
        body.add("channel", "SMS");
        body.add("messageBody", "Test suppression");
        body.add("file", new ByteArrayResource(csv.getBytes()) {
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

        // Wait up to 10 seconds for the worker to process the job
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    boolean hasSkipped = notificationJobRepository.findAll().stream()
                            .anyMatch(j -> j.getTenantId().equals(1L)
                                    && j.getStatus() == NotificationStatus.SKIPPED);
                    assertThat(hasSkipped).isTrue();
                });
    }
}
