package com.example.notifications.integration;

import com.example.notifications.backpressure.WorkerQueueMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackPressureTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notifications_backpressure_test")
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

    @SpyBean
    private WorkerQueueMetrics spyMetrics;

    private static final String SMALL_CSV =
            "external_id,email,phone,push_token,timezone\n" +
            "bp-user1,bp1@example.com,+1111111111,,UTC\n";

    private HttpEntity<MultiValueMap<String, Object>> buildCampaignRequest(long tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", String.valueOf(tenantId));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", "Back Pressure Test Campaign");
        body.add("channel", "EMAIL");
        body.add("messageBody", "Testing back pressure");
        body.add("file", new ByteArrayResource(SMALL_CSV.getBytes()) {
            @Override public String getFilename() { return "recipients.csv"; }
        });

        return new HttpEntity<>(body, headers);
    }

    @Test
    void queueBelowThreshold_campaignCreateSucceeds() {
        // Real queue is empty — isOverloaded() returns false (real bean behaviour)
        assertThat(spyMetrics.isOverloaded()).isFalse();

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/campaigns",
                HttpMethod.POST,
                buildCampaignRequest(1),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void queueAt80Percent_campaignCreateReturns503() {
        // Stub isOverloaded() to return true without actually filling the queue
        doReturn(true).when(spyMetrics).isOverloaded();

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/campaigns",
                HttpMethod.POST,
                buildCampaignRequest(1),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
        assertThat(response.getBody()).contains("Worker queue is at capacity");
    }
}
