package com.example.notifications.backpressure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Rejects new campaign-creation requests with HTTP 503 when the worker queue
 * exceeds 80% capacity, preventing the JVM from accepting work it cannot process
 * in a timely manner.
 *
 * Only intercepts POST /campaigns (exact path). GET /campaigns and
 * POST /campaigns/{id}/retry-failures are deliberately excluded.
 */
@Slf4j
@RequiredArgsConstructor
public class BackPressureInterceptor implements HandlerInterceptor {

    private final WorkerQueueMetrics workerQueueMetrics;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Only intercept POST /campaigns (not GET, not /retry-failures, not /{id})
        boolean isCampaignCreate = "POST".equalsIgnoreCase(method) && uri.matches(".*/campaigns/?$");

        if (isCampaignCreate && workerQueueMetrics.isOverloaded()) {
            int retryAfterSeconds = 30;
            double utilization = workerQueueMetrics.getQueueUtilization();

            log.warn("Back-pressure triggered: queue utilization={:.0f}%, returning 503",
                    utilization * 100);

            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            String body = """
                    {"timestamp":"%s","status":503,"error":"Service Unavailable",\
                    "message":"Worker queue is at capacity. Retry after %d seconds.",\
                    "retryAfter":%d}""".formatted(
                    LocalDateTime.now(), retryAfterSeconds, retryAfterSeconds);

            response.getWriter().write(body);
            return false;
        }

        return true;
    }
}
