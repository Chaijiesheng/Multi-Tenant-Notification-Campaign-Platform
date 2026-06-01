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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultiTenantIsolationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notifications_isolation_test")
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

    private static final String SINGLE_ROW_CSV =
            "external_id,email,phone,push_token,timezone\n" +
            "user1,user1@example.com,+1234567890,,UTC\n";

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private ResponseEntity<Map> createCampaign(long tenantId, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", String.valueOf(tenantId));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", name);
        body.add("channel", "EMAIL");
        body.add("messageBody", "Isolation test message");
        body.add("file", new ByteArrayResource(SINGLE_ROW_CSV.getBytes()) {
            @Override public String getFilename() { return "recipients.csv"; }
        });

        return restTemplate.exchange(
                baseUrl() + "/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    @Test
    void tenantA_cannotSee_tenantBCampaigns() {
        // Create a campaign for tenant 1
        ResponseEntity<Map> createResponse = createCampaign(1, "Tenant1 Campaign");
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Number campaignId = (Number) createResponse.getBody().get("id");

        // Fetch campaign list as tenant 2
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("X-Tenant-Id", "2");
        ResponseEntity<List> listResponse = restTemplate.exchange(
                baseUrl() + "/campaigns",
                HttpMethod.GET,
                new HttpEntity<>(headers2),
                List.class);

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> campaigns = listResponse.getBody();

        // Tenant 2 must not see tenant 1's campaign
        boolean contains = campaigns.stream()
                .anyMatch(c -> campaignId.longValue() == ((Number) c.get("id")).longValue());
        assertThat(contains).isFalse();
    }

    @Test
    void tenantB_cannotGet_tenantACampaignById() {
        // Create campaign for tenant 1
        ResponseEntity<Map> createResponse = createCampaign(1, "Tenant1 Secret Campaign");
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Number campaignId = (Number) createResponse.getBody().get("id");

        // Try to fetch it as tenant 2 — must get 404
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("X-Tenant-Id", "2");
        ResponseEntity<String> getResponse = restTemplate.exchange(
                baseUrl() + "/campaigns/" + campaignId,
                HttpMethod.GET,
                new HttpEntity<>(headers2),
                String.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void campaignCount_isolatedPerTenant() {
        // Create 2 campaigns for tenant 1
        createCampaign(1, "T1 Camp A");
        createCampaign(1, "T1 Camp B");

        // Create 3 campaigns for tenant 2
        createCampaign(2, "T2 Camp A");
        createCampaign(2, "T2 Camp B");
        createCampaign(2, "T2 Camp C");

        HttpHeaders h1 = new HttpHeaders();
        h1.set("X-Tenant-Id", "1");
        ResponseEntity<List> list1 = restTemplate.exchange(
                baseUrl() + "/campaigns", HttpMethod.GET, new HttpEntity<>(h1), List.class);

        HttpHeaders h2 = new HttpHeaders();
        h2.set("X-Tenant-Id", "2");
        ResponseEntity<List> list2 = restTemplate.exchange(
                baseUrl() + "/campaigns", HttpMethod.GET, new HttpEntity<>(h2), List.class);

        // Tenant 1 sees exactly 2 campaigns it owns
        assertThat(list1.getBody()).hasSizeGreaterThanOrEqualTo(2);
        list1.getBody().forEach(obj -> {
            Map<?, ?> c = (Map<?, ?>) obj;
            assertThat(((Number) c.get("tenantId")).longValue()).isEqualTo(1L);
        });

        // Tenant 2 sees exactly 3 campaigns it owns
        assertThat(list2.getBody()).hasSizeGreaterThanOrEqualTo(3);
        list2.getBody().forEach(obj -> {
            Map<?, ?> c = (Map<?, ?>) obj;
            assertThat(((Number) c.get("tenantId")).longValue()).isEqualTo(2L);
        });
    }
}
