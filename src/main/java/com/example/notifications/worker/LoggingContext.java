package com.example.notifications.worker;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * MDC-based logging context for notification worker threads.
 *
 * Fields set per job:
 *   tenantId, campaignId, notificationId (was notificationJobId — Fix 4),
 *   retryCount, channel, status, correlationId (Fix 3).
 *
 * Always call clear() in a finally block to prevent MDC leakage
 * between tasks on the same thread.
 */
public final class LoggingContext {

    private LoggingContext() {}

    public static void set(Long tenantId, Long campaignId, Long jobId, int retryCount, String channel) {
        MDC.put("tenantId", String.valueOf(tenantId));
        MDC.put("campaignId", String.valueOf(campaignId));
        MDC.put("notificationId", String.valueOf(jobId));
        MDC.put("retryCount", String.valueOf(retryCount));
        MDC.put("channel", channel);
        MDC.put("correlationId", UUID.randomUUID().toString());
    }

    public static void setStatus(String status) {
        MDC.put("status", status);
    }

    public static void clear() {
        MDC.remove("tenantId");
        MDC.remove("campaignId");
        MDC.remove("notificationId");
        MDC.remove("retryCount");
        MDC.remove("channel");
        MDC.remove("status");
        MDC.remove("correlationId");
    }
}
