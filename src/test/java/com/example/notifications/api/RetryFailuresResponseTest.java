package com.example.notifications.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryFailuresResponseTest {

    @Test
    void record_constructsAllFieldsCorrectly() {
        RetryFailuresResponse response = new RetryFailuresResponse(1L, 5, "Successfully requeued 5 jobs");

        assertThat(response.campaignId()).isEqualTo(1L);
        assertThat(response.jobsRequeued()).isEqualTo(5);
        assertThat(response.message()).contains("5");
    }

    @Test
    void record_zeroJobsRequeued() {
        RetryFailuresResponse response = new RetryFailuresResponse(42L, 0, "No failed jobs found for campaign 42");

        assertThat(response.campaignId()).isEqualTo(42L);
        assertThat(response.jobsRequeued()).isEqualTo(0);
        assertThat(response.message()).contains("42");
    }

    @Test
    void record_equalityBasedOnAllFields() {
        RetryFailuresResponse r1 = new RetryFailuresResponse(1L, 5, "done");
        RetryFailuresResponse r2 = new RetryFailuresResponse(1L, 5, "done");
        assertThat(r1).isEqualTo(r2);
    }
}
