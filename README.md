# Multi-Tenant Notification & Campaign Platform

A production-quality SaaS platform for sending large-scale notifications (Email, SMS, Push) to customers across multiple tenants. Built with Java 21, Spring Boot 3.3, and MySQL 8.

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Features](#features)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Database Migrations](#database-migrations)
- [Testing](#testing)
- [Known Limitations](#known-limitations)

---

## Overview

This platform allows companies (tenants) to create notification campaigns, upload recipient lists via CSV, and dispatch messages through simulated Email, SMS, and Push channels — reliably and at scale.

Key design goals:
- **Async-first** — HTTP requests return `202 Accepted` immediately; delivery happens in the background
- **Reliable** — exponential backoff, idempotency keys, circuit breakers, transactional outbox
- **Multi-tenant** — all data is strictly scoped to a tenant; no cross-tenant data leakage
- **Observable** — structured JSON logs, Prometheus metrics, MDC-based tracing fields

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.3.4 |
| Database | MySQL 8 |
| Migrations | Flyway |
| Rate Limiting | Bucket4j 8.x (Token Bucket) |
| Circuit Breaker | Resilience4j 2.2 |
| CSV Parsing | OpenCSV 5.9 |
| Metrics | Micrometer + Prometheus |
| Logging | Logback + Logstash JSON encoder |
| Testing | JUnit 5, Mockito, Testcontainers |
| Build | Maven |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        HTTP Layer                                │
│   CampaignController   SuppressionController   Actuator         │
│         │                     │                                  │
│   BackPressureInterceptor (503 at ≥80% queue fill)              │
└──────────────────┬──────────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────────┐
│                    Campaign Management                            │
│   CampaignService — CSV streaming, recipient save,               │
│   NotificationJob creation, Outbox event write (atomic tx)       │
└──────────────────┬──────────────────────────────────────────────┘
                   │  @Scheduled poll (every 2s)
┌──────────────────▼──────────────────────────────────────────────┐
│                   Notification Delivery                           │
│                                                                   │
│   NotificationDispatchWorker                                      │
│        │                                                          │
│        ├── RuleEngine (Chain of Responsibility)                   │
│        │     1. GlobalSuppressionRule  → SKIP                     │
│        │     2. DndWindowRule          → DELAY                    │
│        │     3. TenantCreditCheckRule  → REJECT                   │
│        │     4. DeduplicationRule      → DISCARD                  │
│        │                                                          │
│        ├── BucketChannelRateLimiter (100 req/min per channel)     │
│        │                                                          │
│        └── ProviderCircuitBreakerDecorator                        │
│                  ├── EmailProvider (simulated)                    │
│                  ├── SmsProvider   (simulated)                    │
│                  └── PushProvider  (simulated)                    │
└─────────────────────────────────────────────────────────────────┘
                   │  @Scheduled poll (every 3s)
┌──────────────────▼──────────────────────────────────────────────┐
│              Transactional Outbox                                 │
│   OutboxPoller → ApplicationEventPublisher → CampaignEventListener│
└─────────────────────────────────────────────────────────────────┘
```

### Key Patterns Used

| Pattern | Implementation |
|---|---|
| Transactional Outbox | `OutboxEvent` table + `OutboxPoller` |
| Chain of Responsibility | `RuleEngine` + `NotificationRule` interface |
| Token Bucket | `BucketChannelRateLimiter` (Bucket4j) |
| Circuit Breaker | `ProviderCircuitBreakerDecorator` (Resilience4j) |
| Idempotency | `UNIQUE` constraint on `idempotency_key` |
| Back-pressure | `BackPressureInterceptor` (503 on queue saturation) |
| Value Objects | `EmailAddress`, `PhoneNumber`, `TenantId`, `IdempotencyKey` |

---

## Features

### Campaign Management
- Create campaigns with CSV recipient upload (streaming, row-by-row — no OOM risk)
- Track delivery progress: `totalRecipients`, `sentCount`, `failedCount`, `skippedCount`
- Campaign statuses: `DRAFT → PROCESSING → COMPLETED / PARTIAL_FAILURE`
- Retry all failed jobs for a campaign

### Notification Delivery
- Async worker polls for `PENDING` jobs every 2 seconds
- Dispatches up to 50 jobs per poll to a bounded thread pool (core=10, max=50, queue=500)
- Notification statuses: `PENDING → PROCESSING → SENT / FAILED / SKIPPED`
- Every delivery attempt saved to `delivery_attempts` table

### Retry & Resilience
- **Exponential backoff**: `2^(retryCount-1)` seconds, capped at 60s
- **Max retries**: 3 attempts before marking `FAILED`
- **Circuit breakers**: per-channel (EMAIL/SMS/PUSH), opens at 50% failure rate over 10 calls, 30s wait, 3 HALF_OPEN probes
- **Back-pressure**: `POST /campaigns` returns `503 Service Unavailable` when worker queue is ≥80% full

### Business Rules (Rule Engine)
| Rule | Level | Action | Condition |
|---|---|---|---|
| Global Suppression | Recipient | `SKIP` | Recipient opted out of channel |
| DND Window | Channel | `DELAY` | 10pm–8am in recipient's timezone (SMS/Push only) |
| Tenant Credit Check | Tenant | `REJECT` | Monthly campaign or message limit exceeded |
| Deduplication | Campaign | `DISCARD` | Campaign already sent within last 5 minutes |

- DND rule bypassed for campaigns flagged as `transactional` (e.g. OTPs)
- Rules short-circuit — first non-PASS result wins, remaining rules skipped

### Multi-Tenancy
- All API calls require `X-Tenant-Id` header
- Every database query is scoped to `tenant_id`
- No cross-tenant data access possible at the repository level

### Suppression List
- Manage opt-outs per recipient + channel
- `POST /suppression` — add suppression (idempotent)
- `DELETE /suppression/{externalId}/{channel}` — remove suppression

### Observability
- Structured JSON logs (Logstash format) with MDC fields: `tenantId`, `campaignId`, `notificationJobId`, `retryCount`, `channel`, `status`
- PII masking: emails → `j****@example.com`, phones → `+44****23`
- Prometheus metrics at `/actuator/prometheus`
- Circuit breaker states at `GET /actuator/circuit-breakers`
- Health check at `/actuator/health`

---

## Project Structure

```
src/
├── main/java/com/example/notifications/
│   ├── api/              # Controllers and DTOs
│   ├── backpressure/     # BackPressureInterceptor, WorkerQueueMetrics
│   ├── config/           # Spring configuration (async, MVC, rules, virtual threads)
│   ├── domain/           # JPA entities + Value Objects + Enums
│   ├── exception/        # Custom exceptions + global handler
│   ├── provider/         # Simulated channel providers (Email, SMS, Push)
│   ├── ratelimit/        # Token Bucket rate limiter
│   ├── repository/       # Spring Data JPA repositories
│   ├── resilience/       # Circuit breaker config + decorator
│   ├── rule/             # Rule engine + 4 rule implementations
│   ├── service/          # CampaignService, OutboxPoller, event listeners
│   └── worker/           # NotificationDispatchWorker, LoggingContext
├── main/resources/
│   ├── application.yml
│   ├── logback-spring.xml
│   └── db/migration/     # Flyway V1–V8
└── test/java/            # Unit tests (Mockito) + Integration tests (Testcontainers)
```

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- MySQL 8 (or Docker)
- Docker (for Testcontainers integration tests)

### 1. Start MySQL

```bash
docker run -d \
  --name notifications-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=notifications \
  -p 3306:3306 \
  mysql:8.0
```

### 2. Configure environment variables

```bash
export DB_URL=jdbc:mysql://localhost:3306/notifications?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
export DB_USER=root
export DB_PASS=root
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

The app starts on **http://localhost:8080**.

Flyway runs all migrations (V1–V8) automatically on startup and seeds two tenants:
- `id=1, slug=acme`
- `id=2, slug=globex`

---

## API Reference

All endpoints require the `X-Tenant-Id` header.

### Create Campaign

```http
POST /campaigns
Content-Type: multipart/form-data
X-Tenant-Id: 1

name=Summer Sale
channel=EMAIL
messageBody=Hello, check out our summer deals!
file=@recipients.csv
```

**CSV format:**
```csv
external_id,email,phone,push_token,timezone
user-001,alice@example.com,+60123456789,token123,Asia/Kuala_Lumpur
user-002,bob@example.com,+60198765432,,UTC
```

**Response:** `202 Accepted`
```json
{
  "id": 1,
  "tenantId": 1,
  "name": "Summer Sale",
  "channel": "EMAIL",
  "status": "PROCESSING",
  "totalRecipients": 2,
  "sentCount": 0,
  "failedCount": 0,
  "skippedCount": 0,
  "createdAt": "2025-01-15T10:00:00"
}
```

### List Campaigns

```http
GET /campaigns
X-Tenant-Id: 1
```

### Get Campaign by ID

```http
GET /campaigns/{id}
X-Tenant-Id: 1
```

### Retry Failed Jobs

```http
POST /campaigns/{id}/retry-failures
X-Tenant-Id: 1
```

**Response:** `202 Accepted`
```json
{
  "campaignId": 1,
  "jobsRequeued": 12,
  "message": "Successfully requeued 12 jobs"
}
```

### Add Suppression

```http
POST /suppression
X-Tenant-Id: 1
Content-Type: application/json

{
  "recipientExternalId": "user-001",
  "channel": "SMS"
}
```

**Response:** `201 Created` (or `200 OK` if already suppressed)

### Remove Suppression

```http
DELETE /suppression/{recipientExternalId}/{channel}
X-Tenant-Id: 1
```

**Response:** `204 No Content`

### Circuit Breaker Status

```http
GET /actuator/circuit-breakers
```

**Response:**
```json
{
  "email": "CLOSED",
  "sms": "CLOSED",
  "push": "CLOSED"
}
```

### Error Responses

All errors follow a standard format:
```json
{
  "timestamp": "2025-01-15T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Tenant not found with id: 99",
  "path": "/campaigns"
}
```

| Status | Cause |
|---|---|
| `400` | Invalid request (bad channel enum, blank fields) |
| `404` | Tenant or campaign not found |
| `422` | Constraint violation |
| `503` | Worker queue at capacity — retry after 30s |

---

## Configuration

Key settings in `application.yml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true          # Java 21 virtual threads for Tomcat

  servlet:
    multipart:
      max-file-size: 50MB    # Max CSV upload size
      max-request-size: 50MB

async:
  executor:
    core-pool-size: 10
    max-pool-size: 50
    queue-capacity: 500      # Back-pressure triggers at 80% (400 slots)
    thread-name-prefix: notif-worker-
```

All credentials use environment variables — no hardcoded secrets:

| Variable | Description | Default (local dev only) |
|---|---|---|
| `DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/notifications` |
| `DB_USER` | MySQL username | `root` |
| `DB_PASS` | MySQL password | `root` |

---

## Database Migrations

Flyway manages all schema changes. No manual SQL execution.

| Migration | Description |
|---|---|
| `V1__create_tenants.sql` | Tenants table + 2 seed rows |
| `V2__create_campaigns.sql` | Campaigns table |
| `V3__create_recipients.sql` | Recipients table |
| `V4__create_notification_jobs.sql` | Notification jobs + idempotency key |
| `V5__create_delivery_attempts.sql` | Delivery attempt audit log |
| `V6__add_transactional_flag_to_campaigns.sql` | `is_transactional`, `message_body_hash` |
| `V7__create_suppression_list.sql` | Suppression list + seed suppressions |
| `V8__create_outbox_events.sql` | Transactional outbox events table |

---

## Testing

### Run unit tests only

```bash
./mvnw test -Dtest="IdempotencyKeyTest,PhoneNumberMaskingTest,EmailAddressMaskingTest,ExponentialBackoffTest,GlobalSuppressionRuleTest,DndWindowRuleTest,DeduplicationRuleTest,RuleEngineTest,CircuitBreakerDecoratorTest,WorkerQueueMetricsTest,RetryFailuresResponseTest,BucketChannelRateLimiterTest"
```

### Run all tests (requires Docker for Testcontainers)

```bash
./mvnw test
```

Testcontainers automatically spins up a MySQL 8 container for each integration test class. No manual DB setup needed.

### Test coverage

| Test | Type | What it verifies |
|---|---|---|
| `IdempotencyKeyTest` | Unit | Key format `t:c:r:CHANNEL` |
| `PhoneNumberMaskingTest` | Unit | Phone masking logic |
| `EmailAddressMaskingTest` | Unit | Email masking + validation |
| `ExponentialBackoffTest` | Unit | Backoff formula at retry 1–4 |
| `GlobalSuppressionRuleTest` | Unit | SKIP when suppressed, PASS when not |
| `DndWindowRuleTest` | Unit | DELAY during 10pm–8am, PASS for EMAIL/transactional |
| `DeduplicationRuleTest` | Unit | DISCARD on recent success, PASS otherwise |
| `RuleEngineTest` | Unit | Short-circuit behaviour |
| `CircuitBreakerDecoratorTest` | Unit | OPEN after failures, no provider call when OPEN |
| `WorkerQueueMetricsTest` | Unit | Overload at ≥80% queue fill |
| `BucketChannelRateLimiterTest` | Unit | 100 tokens, 101st fails |
| `CampaignIntegrationTest` | Integration | Full campaign creation + job creation |
| `SuppressionIntegrationTest` | Integration | Suppressed recipient gets SKIPPED status |
| `OutboxIntegrationTest` | Integration | Outbox event created and processed |
| `MultiTenantIsolationTest` | Integration | Tenant B cannot see Tenant A's data |
| `BackPressureTest` | Integration | 503 returned when queue overloaded |
| `CircuitBreakerIntegrationTest` | Integration | CB state endpoint reflects forced OPEN |
| `RateLimiterIntegrationTest` | Integration | 100 acquires succeed, 101st fails |
| `VirtualThreadTest` | Integration | Thread names + virtual thread config |
| `LoadTest` | Integration | 1000 recipients processed within 120s |

---

## Known Limitations

1. **In-memory rate limiter** — not shared across JVM instances; production needs Redis + Bucket4j `RedisProxyManager`
2. **PROCESSING orphans on crash** — jobs stuck in `PROCESSING` on JVM crash need a startup reset job
3. **Large CSV in one transaction** — files with 100k+ rows hold a DB connection open for extended periods
4. **No campaign-level idempotency key** — duplicate `POST /campaigns` creates duplicate campaigns
5. **Deduplication scope** — currently discards all campaign jobs if any delivery succeeded in the last 5 minutes (should be per-recipient)
6. **Race condition on counters** — concurrent job processing can cause lost updates to `sentCount`/`failedCount`

See `ENGINEERING_NOTES.md` for full discussion of each limitation and production remediation paths.
