package com.example.notifications.integration;

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

import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoadTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notifications_load_test")
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

    @Test
    void thousandRecipientCampaign_completesWithinTimeout() {
        // Build a CSV with 1000 recipients
        StringBuilder csv = new StringBuilder("external_id,email,phone,push_token,timezone\n");
        for (int i = 1; i <= 1000; i++) {
            csv.append(String.format("user-%d,user%d@example.com,+441234%05d,,UTC%n", i, i, i));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", "1");
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        byte[] csvBytes = csv.toString().getBytes();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", "Load Test 1000 Campaign");
        body.add("channel", "EMAIL");
        body.add("messageBody", "Load test message");
        body.add("file", new ByteArrayResource(csvBytes) {
            @Override public String getFilename() { return "load-test.csv"; }
        });

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "http://localhost:" + port + "/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Number campaignId = (Number) createResponse.getBody().get("id");
        assertThat(campaignId).isNotNull();

        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.set("X-Tenant-Id", "1");

        // Wait up to 120 seconds for all 1000 jobs to reach a terminal state
        await()
                .atMost(120, SECONDS)
                .pollInterval(2, SECONDS)
                .untilAsserted(() -> {
                    ResponseEntity<Map> campaignResponse = restTemplate.exchange(
                            "http://localhost:" + port + "/campaigns/" + campaignId,
                            HttpMethod.GET,
                            new HttpEntity<>(getHeaders),
                            Map.class);

                    assertThat(campaignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                    Map<String, Object> campaign = campaignResponse.getBody();

                    int sentCount    = ((Number) campaign.get("sentCount")).intValue();
                    int failedCount  = ((Number) campaign.get("failedCount")).intValue();
                    int skippedCount = ((Number) campaign.get("skippedCount")).intValue();
                    String status    = (String) campaign.get("status");

                    int total = sentCount + failedCount + skippedCount;
                    assertThat(total)
                            .as("Total terminal jobs (sent + failed + skipped) should equal 1000, got %d", total)
                            .isEqualTo(1000);
                    assertThat(status)
                            .as("Campaign status should be COMPLETED or PARTIAL_FAILURE")
                            .isIn("COMPLETED", "PARTIAL_FAILURE");
                });
    }
}
