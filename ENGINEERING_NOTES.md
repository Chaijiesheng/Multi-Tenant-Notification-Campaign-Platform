# ENGINEERING_NOTES.md

Multi-Tenant Notification & Campaign Platform — Sprint 1–3 Implementation Notes

---

## 1. Architecture decisions and tradeoffs

### Domain-Driven Design

The bounded context is split into two cohesive areas:

**Campaign Management** (`CampaignService`, `Campaign`, `Recipient`, `CampaignController`) owns the lifecycle of creating a campaign, streaming the CSV into recipient and job rows, and presenting status to callers. It knows nothing about how notifications are dispatched.

**Notification Delivery** (`NotificationDispatchWorker`, `NotificationJob`, `DeliveryAttempt`, `ProviderCircuitBreakerDecorator`, `RuleEngine`) owns the mechanics of dispatch: polling for pending jobs, evaluating rules, acquiring rate-limit tokens, calling providers through circuit breakers, recording attempts, and applying exponential backoff.

Domain logic intentionally lives close to its data. `Campaign.computeHash()` is a `@PrePersist`/`@PreUpdate` lifecycle method on the entity itself — the SHA-256 hash of `messageBody` is an intrinsic property of the campaign, not a service concern. Similarly, `IdempotencyKey.of(tenantId, campaignId, recipientId, channel)` is a static factory on the `IdempotencyKey` value object because the key format (`"t:c:r:CHANNEL"`) is a domain rule, not infrastructure logic.

Value objects (`EmailAddress`, `PhoneNumber`, `TenantId`, `CampaignId`, `IdempotencyKey`) enforce type safety at construction time. `EmailAddress` validates regex format in its constructor and exposes a `masked()` method — PII masking is co-located with the data it guards. `PhoneNumber` masks in its own constructor so a raw phone number can never escape the domain layer unmasked. This eliminates an entire class of accidental PII leaks through logging or serialisation.

### Event-Driven Architecture via Transactional Outbox

The naïve approach — calling `ApplicationEventPublisher.publishEvent()` from inside `CampaignService.createCampaign()` — has a critical safety gap: if the JVM crashes after the database transaction commits but before `publishEvent()` executes, the `CampaignCreated` event is lost permanently. Neither the application nor its consumers have any way to recover it.

The Outbox pattern solves this by writing the event into the `outbox_events` table **within the same `@Transactional` boundary** as the `Campaign`, `Recipient`, and `NotificationJob` rows. A single commit either persists all five tables or none of them. `OutboxPoller` (scheduled every 3 seconds) reads rows where `processed = false`, calls `ApplicationEventPublisher`, and marks them `processed = true`. This gives **at-least-once delivery**: the event will eventually be published even if the process restarts between the DB write and the poller tick.

The trade-off is that at-least-once delivery requires consumers to be idempotent. `CampaignEventListener.onOutboxEvent()` handles `CampaignCreated` events by logging — a no-op that is inherently idempotent. Sprint 4 consumers must carry the same guarantee.

A secondary benefit: `outbox_events` provides a durable audit log of all domain events, queryable by `aggregate_type`, `aggregate_id`, and `event_type`.

### In-Process Worker vs Message Broker

`NotificationDispatchWorker` uses a `ThreadPoolTaskExecutor` (declared in `VirtualThreadConfig` as `asyncTaskExecutor`) combined with a `@Scheduled(fixedDelay = 2000)` poll loop. This was a deliberate choice to eliminate broker dependencies for a self-contained assessment platform.

**Advantages**: single deployable JAR, no Kafka/RabbitMQ setup, easy local development.

**Disadvantages**: jobs in `PROCESSING` status are orphaned if the JVM crashes mid-dispatch. The worker poll query (`findPendingJobs`) only selects `PENDING` jobs; PROCESSING jobs are invisible to the next restart. A production fix requires a startup job that resets `PROCESSING → PENDING` for jobs whose `updated_at` is older than a configurable threshold (e.g. 5 minutes), implemented as a `@PostConstruct` method or a Spring `ApplicationRunner`. This is documented in section 4 as a known limitation.

A second limitation: the in-process queue is not durable. If the executor's task queue holds 500 submitted jobs and the JVM dies, all 500 `Runnable` references are lost. The DB rows remain `PENDING` and are reprocessed on the next startup — no data loss, but the unprocessed in-memory queue is discarded.

For production, the in-process executor should be replaced with a Kafka consumer group (see section 5).

### Rule Engine

The rule engine uses the **Chain of Responsibility** pattern. `NotificationRule` is a single-method interface (`evaluate(job, recipient, tenant, campaign) → RuleResult`). `RuleEngine.evaluate()` iterates the injected `List<NotificationRule>` and **short-circuits** on the first non-`PASS` result, returning immediately without evaluating the remaining rules.

`RuleResult` is a Java record with three fields — `Outcome`, `reason`, and `retryAt` — so rule implementations can return structured decisions (`SKIP`, `DELAY`, `REJECT`, `DISCARD`) rather than side-effect-heavy booleans.

Rule ordering is declared explicitly in `RuleEngineConfig` via `@Order`:

1. **`GlobalSuppressionRule` (@Order 1)** — Queries `SuppressionRepository` by `(tenantId, externalId, channel)`. Runs first because there is no point computing DND windows, credit checks, or deduplication for a suppressed recipient.
2. **`DndWindowRule` (@Order 2)** — Computes the recipient's local time using `recipient.getTimezone()` and a `Clock` (injectable for testing). Returns `DELAY` with a `retryAt` of next 08:00 in the recipient's zone, converted to UTC. Bypassed for `EMAIL` channel and transactional campaigns.
3. **`TenantCreditCheckRule` (@Order 3)** — Counts campaigns and sent messages since the start of the month via `CampaignRepository.countCampaignsSince()` and `NotificationJobRepository.countSentMessagesSince()`. Returns `REJECT` if either limit is exceeded. Runs before deduplication because a rejected tenant should not consume a deduplication check.
4. **`DeduplicationRule` (@Order 4)** — Checks `DeliveryAttemptRepository.countSuccessfulAttemptsByCampaignSince()` for successful deliveries within the last 5 minutes. Returns `DISCARD` if any are found. Runs last because it requires DB I/O and should only execute when all cheaper checks pass.

`RuleEngine.evaluate()` is annotated `@Transactional(readOnly = true)`, ensuring all four rule DB reads share a single read transaction with no dirty reads.

### Rate Limiting

`BucketChannelRateLimiter` uses the **Token Bucket** algorithm (Bucket4j `Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)))`). Token Bucket was chosen over Fixed Window because it handles burst traffic smoothly: a burst of up to 100 tokens is allowed immediately, then refills at a steady rate. A Fixed Window would reject everything after the 100th call until the minute resets, causing thundering-herd spikes at window boundaries.

Rate limits are **per channel** (EMAIL, SMS, PUSH), not per tenant. This models provider capacity constraints: the downstream SMS gateway can handle 100 sends per minute regardless of which tenant is sending. Per-tenant fairness is a separate concern (see `TenantCreditCheckRule`).

Buckets are stored in a `ConcurrentHashMap<Channel, Bucket>` initialised in `@PostConstruct`. The `acquire(Channel)` method polls every 100 ms via `Thread.sleep(100)` — cheap on virtual threads because virtual threads block without consuming a platform thread.

The in-memory limitation is documented in section 4: in a multi-JVM deployment, each instance has its own 100 tokens per channel per minute, effectively multiplying the effective rate limit by instance count.

### Circuit Breaker

`ProviderCircuitBreakerDecorator` wraps provider calls with per-channel `CircuitBreaker` instances from Resilience4j, configured programmatically in `CircuitBreakerConfig` (using `CircuitBreakerRegistry.of(config)`) rather than via `application.yml` annotations. This choice gives full control over parameters and makes configuration testable in isolation.

**Configuration**: sliding window of 10 calls, 50% failure threshold, minimum 5 calls before computing the rate, 30-second wait in OPEN state, 3 probe calls in HALF_OPEN state, automatic transition from OPEN to HALF_OPEN.

**Why per channel?** Provider failure domains are independent: an SMS gateway outage should not block EMAIL delivery. Using one breaker per channel ensures isolation between provider types.

**HALF_OPEN probe**: after 30 seconds in OPEN state, the breaker automatically transitions to HALF_OPEN and allows 3 calls through. If those 3 calls succeed, it closes; if they fail, it reopens for another 30 seconds. This prevents a thundering herd of retried jobs from overwhelming a provider that just recovered from an outage.

**Soft failure recording**: `ProviderCircuitBreakerDecorator.send()` wraps the provider call in a lambda that throws `ProviderSoftFailureException` when `ProviderResponse.success() == false`. This means both hard failures (exceptions from providers) and soft failures (providers returning `false`) trip the circuit breaker's failure counter. Without this, providers that return failure responses without throwing would never open the circuit.

---

## 2. How the system scales

### Vertical scaling (current model)

The `ThreadPoolTaskExecutor` (`asyncTaskExecutor` bean in `VirtualThreadConfig`) uses core=10, max=50, queue=500. With `spring.threads.virtual.enabled=true`, Tomcat serves HTTP requests on virtual threads, enabling thousands of concurrent inbound connections on a single JVM without exhausting platform threads. The notification worker pool is intentionally bounded (max=50) to prevent runaway thread growth under extreme load.

Virtual threads make blocking I/O — provider `Thread.sleep()` calls simulating network latency, JDBC queries in rule evaluation — effectively free from the scheduler's perspective. Each blocked virtual thread suspends and yields its carrier platform thread to another runnable virtual thread.

`BackPressureInterceptor` guards the `POST /campaigns` endpoint by checking `WorkerQueueMetrics.isOverloaded()` (queue ≥ 80% utilisation). When overloaded, it returns HTTP 503 with `Retry-After: 30`. This prevents the caller from flooding the queue past capacity while the worker drains it.

### Horizontal scaling (production path)

Replace the in-process executor with a Kafka consumer group. Each JVM instance consumes from a `notification-jobs` topic. The `UNIQUE` constraint on `idempotency_key` in MySQL prevents duplicate sends even when multiple consumer instances race on the same job row. The Transactional Outbox pattern (V8 `outbox_events` table, `OutboxPoller`) already decouples event publication from the transaction, making it straightforward to replace `ApplicationEventPublisher` with a Kafka producer in the outbox poller.

### Database scaling

MySQL handles fan-out writes well at moderate scale. The indexes on `(tenant_id, status)` (campaigns), `(status, next_retry_at)` (notification_jobs), and `(tenant_id, recipient_external_id, channel)` (suppression_list) cover the three most frequent query patterns. For very high throughput (millions of jobs per day), partition `notification_jobs` by `created_at` range (monthly partitions) to keep the active partition small for the worker poll query.

### CSV streaming

`CSVReader.readNext()` in `CampaignService.parseCsvAndCreateJobs()` processes one row at a time with O(1) heap regardless of file size. The 50 MB multipart limit (`spring.servlet.multipart.max-file-size=50MB`) bounds worst-case input. This is enforced by inspection — no `CSVReader.readAll()` call exists in the codebase.

---

## 3. Failure scenarios considered

**Scenario 1 — Provider transient failure (15–25% simulated rate)**

The simulated providers (`EmailProvider`, `SmsProvider`, `PushProvider`) fail randomly using `ThreadLocalRandom`. When `ProviderCircuitBreakerDecorator.send()` receives a `ProviderResponse(false)`, it throws `ProviderSoftFailureException` so the circuit breaker records the outcome. The exception is caught and converted back to `ProviderResponse(false, ...)`. In `NotificationDispatchWorker.processJob()`, a failed response increments `retryCount` and computes `nextRetryAt = now + min(2^(retryCount-1), 60)` seconds. The job is set back to `PENDING` and picked up by the next poll cycle. After `maxRetries=3` attempts, the job transitions to `FAILED`. `POST /campaigns/{id}/retry-failures` resets all `FAILED` jobs to `PENDING` with `retryCount=0` and resets the campaign status to `PROCESSING`.

**Scenario 2 — Provider sustained failure (circuit breaker opens)**

After 5 failures within a 10-call sliding window (failure rate ≥ 50%), the `CircuitBreaker` for that channel transitions to `OPEN`. Subsequent `decoratedSupplier.get()` calls throw `CallNotPermittedException` immediately, which the outer `catch (Exception e)` block converts to `ProviderResponse(false, "Circuit open or provider unavailable: ...")`. Jobs accumulate in `PENDING` with exponential backoff. After 30 seconds, the breaker transitions to `HALF_OPEN` and probes with 3 calls. On recovery it closes; on continued failure it reopens. The `/actuator/circuit-breakers` endpoint (served by `CircuitBreakerStatusController`) exposes real-time state for alerting.

**Scenario 3 — JVM crash during job processing**

Jobs in `PROCESSING` status at crash time are orphaned — the worker poll query (`findPendingJobs`) filters exclusively on `status = 'PENDING'`. These jobs will never be retried automatically after restart. The documented production fix is a startup `ApplicationRunner` that runs `UPDATE notification_jobs SET status = 'PENDING' WHERE status = 'PROCESSING' AND updated_at < NOW() - INTERVAL 5 MINUTE`. This is not implemented in the current sprints but is the correct remediation path.

**Scenario 4 — Large CSV upload causes OOM**

Mitigated by streaming CSV parsing (`CSVReader.readNext()`). Recipients are saved inside the same `@Transactional` call as the campaign. For files with tens of thousands of rows, this holds a JDBC connection and a Hibernate session open for seconds to minutes, risking `HikariCP` connection pool exhaustion. The production fix is to chunk inserts: process 1000 rows per transaction, update `campaign.totalRecipients` incrementally, and use a `DRAFT → INGESTING → PROCESSING` status flow so the caller can poll progress.

**Scenario 5 — Duplicate campaign submission (network retry by client)**

The `UNIQUE` constraint on `notification_jobs.idempotency_key` prevents duplicate job rows. `IdempotencyKey.of(tenantId, campaignId, recipientId, channel)` produces a key in `"t:c:r:CHANNEL"` format. If the same CSV is resubmitted with the same campaign, the second set of `Recipient` rows get new IDs, producing new idempotency keys — so a second campaign submission creates a second campaign and second set of jobs. Campaign-level idempotency (deduplication of the entire campaign, not just individual jobs) requires an `X-Idempotency-Key` header on `POST /campaigns`, checked before creating the `Campaign` entity. This is a known gap documented in section 4.

**Scenario 6 — Worker queue overflow (back-pressure)**

`BackPressureInterceptor.preHandle()` returns HTTP 503 with `Retry-After: 30` when `WorkerQueueMetrics.isOverloaded()` is true (queue ≥ 80% of capacity = 400/500 slots). The regex `".*/campaigns/?$"` with `method.equalsIgnoreCase("POST")` ensures only campaign creation is rate-limited; `GET /campaigns`, `GET /campaigns/{id}`, and `POST /campaigns/{id}/retry-failures` are unaffected.

**Scenario 7 — Outbox event delivery failure**

`OutboxPoller.poll()` wraps each event in its own `try/catch`. A failing event (e.g. `CampaignEventListener` throws) is logged and skipped; the loop continues to the next event. The row's `processed` flag remains `false`. On the next poll (3 seconds later), the event is retried. This guarantees at-least-once delivery. A perpetually failing event is retried indefinitely — the production fix is adding a `retry_count` column to `outbox_events` and moving events that exceed a threshold to a dead-letter table.

---

## 4. Known limitations

**1. PROCESSING orphans on JVM crash**
Jobs stuck in `PROCESSING` at crash time are never automatically retried. Fix: add a startup `ApplicationRunner` in `NotificationsApplication` that issues `UPDATE notification_jobs SET status = 'PENDING', updated_at = NOW() WHERE status = 'PROCESSING' AND updated_at < :threshold` (threshold = now − 5 minutes). This is idempotent and safe to run on every restart.

**2. In-memory rate limiter buckets not shared across JVM instances**
`BucketChannelRateLimiter` stores `Bucket` objects in a `ConcurrentHashMap` local to the JVM. In a multi-instance deployment, each instance has its own 100 tokens per channel per minute. Effective rate limit = 100 × instance count. Fix: Bucket4j `RedisProxyManager` with a Redis cluster backend for distributed token buckets shared across all instances.

**3. Large CSV in single transaction**
`CampaignService.parseCsvAndCreateJobs()` saves all recipients and jobs in one `@Transactional` method. A 50 MB file with ~500,000 rows holds a connection and Hibernate session open for many minutes. Fix: chunk inserts into batches of 1000, each in its own `REQUIRES_NEW` transaction, with a `DRAFT → INGESTING → PROCESSING` status machine on `Campaign`.

**4. No campaign-level idempotency key**
A client that retries `POST /campaigns` after a timeout creates a duplicate campaign and duplicate notification jobs (the job-level `idempotency_key` UNIQUE constraint only deduplicates within the same campaign). Fix: add an optional `X-Idempotency-Key` header; store it in a `campaign_idempotency_keys` table with a UNIQUE constraint; return the existing campaign response on conflict.

**5. Outbox events retried indefinitely on listener failure**
`OutboxPoller` has no dead-letter mechanism. A consistently failing event will be retried on every 3-second poll cycle forever, consuming CPU and DB reads. Fix: add `retry_count INT NOT NULL DEFAULT 0` and `dead_lettered TINYINT(1) NOT NULL DEFAULT 0` to `outbox_events`; move events with `retry_count >= 5` to `processed = true` and `dead_lettered = true`.

**6. Suppression list has no bulk import endpoint**
Loading a large suppression list requires N individual `POST /suppression` calls. For a suppression list of 100,000 entries, this is impractical. Fix: add `POST /suppression/bulk` accepting a CSV, similar to the campaign CSV endpoint, with the same streaming-parse pattern.

**7. DND delay is re-evaluated on retry**
If a job is delayed until 08:00 local time and the polling loop runs at 07:59:59, `DndWindowRule` re-evaluates and delays the job again by one more day. The window is at most 2 seconds (the poll interval) but is non-zero. Fix: use a monotonically increasing `next_retry_at` with a small grace margin (e.g. evaluate DND as "hour >= 22 OR hour < 7") to absorb poll-cycle jitter.

---

## 5. What would change in production

**1. Message broker**
Replace `NotificationDispatchWorker`'s `ThreadPoolTaskExecutor` with a Kafka consumer. `NotificationJob` rows become Kafka messages (keyed by `idempotency_key`). Consumer group members provide horizontal scaling. Kafka's offset tracking and log compaction replace the `status`-based polling query. The `outbox_events` table's `OutboxPoller` would publish to a Kafka topic instead of `ApplicationEventPublisher`.

**2. Distributed rate limiting**
Bucket4j with a `RedisProxyManager` backed by a Redis cluster. One bucket per channel, shared across all consumer group members. The bucket key is `"rate-limit:channel:{EMAIL|SMS|PUSH}"`.

**3. Database**
Add MySQL read replicas for read-heavy operations: `GET /campaigns` list, rule engine reads (`countCampaignsSince`, `countSentMessagesSince`, `existsByTenantIdAndRecipientExternalIdAndChannel`). Route read traffic via Spring `AbstractRoutingDataSource`. Partition `notification_jobs` by `created_at` month for archival; move partitions older than 90 days to cold storage. Consider a columnar store (ClickHouse, BigQuery) for delivery analytics over the `delivery_attempts` table.

**4. Secret management**
Replace `${DB_URL}`, `${DB_USER}`, `${DB_PASS}` environment variables with AWS Secrets Manager or HashiCorp Vault. Spring Cloud Vault provides transparent secret injection at context startup. Credentials rotate without redeployment.

**5. Observability**
Structured JSON logs (Logback + `logstash-logback-encoder`) and Prometheus metrics (`micrometer-registry-prometheus`) are already in place. Add OpenTelemetry distributed tracing: instrument `CampaignService.createCampaign()`, `NotificationDispatchWorker.processJob()`, and `ProviderCircuitBreakerDecorator.send()` with `@WithSpan`. The existing MDC fields (`tenantId`, `campaignId`, `notificationJobId`) become trace attributes, enabling end-to-end trace correlation in Jaeger or Grafana Tempo.

**6. Graceful shutdown**
`ThreadPoolTaskExecutor` is configured with `waitForTasksToCompleteOnShutdown=true` and `awaitTerminationSeconds=30`. On SIGTERM, Spring calls `executor.shutdown()`, the executor stops accepting new tasks, and the JVM waits up to 30 seconds for in-flight jobs to finish before exiting. For jobs that do not finish within 30 seconds, the PROCESSING-orphan recovery (section 4, limitation 1) handles them on the next instance's startup.

**7. Multi-region DND timezone normalisation**
`DndWindowRule` already handles invalid timezones gracefully via `ZoneId.of(timezone)` in a try-catch (falling back to UTC with a warning). In production, timezone strings should be normalised to IANA format (e.g. `"America/New_York"`, not `"EST"` or `"US/Eastern"`) at ingestion time in `CampaignService.parseCsvAndCreateJobs()` before persisting the `Recipient`.

**8. Tenant onboarding API**
Tenants are seeded via Flyway V1 (`INSERT INTO tenants ...`). A production system requires a `POST /tenants` API with admin-level authentication, returning an API key scoped to the new tenant. The `X-Tenant-Id` header would be resolved from an API key lookup rather than trusted as a raw long integer.

**9. Security**
The `X-Tenant-Id` request header is trusted as-is, which is only safe in a server-to-server context with network-level controls. In production, add a Spring Security filter that validates the inbound JWT or API key and resolves `tenantId` from the authenticated principal, rejecting requests with a mismatched or absent credential.

---

## 6. What parts used AI assistance

The following parts of this implementation were produced with AI assistance (Claude, Anthropic):

**Sprint planning and prompt engineering**
The three sprint plans and agent prompts were structured with AI assistance, breaking the full specification into ordered, dependency-aware deliverables. Each sprint's prompt was written to avoid regenerating Sprint 1 and Sprint 2 files unchanged, saving context and reducing noise.

**Boilerplate generation**
JPA `@Entity` classes (`Tenant`, `Campaign`, `Recipient`, `NotificationJob`, `DeliveryAttempt`, `OutboxEvent`, `SuppressionEntry`), Spring Data repository interfaces, DTO records (`CampaignResponse`, `RetryFailuresResponse`), and all five Flyway migration SQL files (V1–V8) were generated via AI and reviewed for correctness against the specification. The SHA-256 hash computation in `Campaign.computeHash()` and the JSON string construction in `OutboxEvent.campaignCreated()` (without Jackson) were AI-generated and verified.

**Algorithm implementation**
The exponential backoff formula (`min(2^(retryCount-1), 60)` seconds in `NotificationDispatchWorker`), Token Bucket configuration (`Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)))` in `BucketChannelRateLimiter`), and Resilience4j `CircuitBreakerConfig` programmatic setup (sliding window, failure threshold, wait duration, HALF_OPEN probe count) were written with AI assistance and cross-checked against Bucket4j 8.x and Resilience4j 2.x API documentation.

**Pattern implementation**
The Transactional Outbox pattern (`OutboxPoller`, `OutboxEventPublished`, `CampaignEventListener`), the Chain of Responsibility rule engine (`NotificationRule`, `RuleEngine`, `RuleEngineConfig` with `@Order`), and the `PiiMaskingConverter` Logback extension were AI-assisted implementations of established patterns applied to this domain.

**Test scaffolding**
Testcontainers setup (`@Container MySQLContainer`, `@DynamicPropertySource`), Awaitility assertions (`await().atMost(120, SECONDS).untilAsserted(...)`), Mockito spy patterns (`@SpyBean WorkerQueueMetrics`, `doReturn(true).when(spy).isOverloaded()`), and `DndWindowRuleTest` clock injection (`Clock.fixed(Instant.parse(...), ZoneId.UTC)`) were AI-assisted. All test scenarios and assertions were manually reviewed to ensure they cover the specified behaviour and are not merely tautological.

**ENGINEERING_NOTES.md**
This document was drafted with AI assistance based on the actual implementation decisions made during the sprints. All technical claims (class names, algorithm names, configuration values, database indexes, migration version numbers) reflect the real source code and can be cross-referenced against it. No vague generalisations were included without a corresponding concrete class or configuration to point to.

All AI-generated code was reviewed for correctness, security (no credentials in source, PII masking enforced in `PiiMaskingConverter` and in provider log statements, all queries tenant-scoped), and conformance to the specification before being committed.
