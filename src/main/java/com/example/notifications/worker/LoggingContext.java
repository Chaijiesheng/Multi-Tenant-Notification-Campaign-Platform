package com.example.notifications.worker;

import org.slf4j.MDC;

public final class LoggingContext {

    private LoggingContext() {}

    public static void set(Long tenantId, Long campaignId, Long jobId, int retryCount, String channel) {
        MDC.put("tenantId", String.valueOf(tenantId));
        MDC.put("campaignId", String.valueOf(campaignId));
        MDC.put("notificationJobId", String.valueOf(jobId));
        MDC.put("retryCount", String.valueOf(retryCount));
        MDC.put("channel", channel);
    }

    public static void setStatus(String status) {
        MDC.put("status", status);
    }

    public static void clear() {
        MDC.remove("tenantId");
        MDC.remove("campaignId");
        MDC.remove("notificationJobId");
        MDC.remove("retryCount");
        MDC.remove("channel");
        MDC.remove("status");
    }
}
