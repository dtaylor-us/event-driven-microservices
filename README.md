# Event-Driven Microservices Demo

Java 21 + Spring Boot 3.4 event-driven demo with Kafka: ingest, consume to Postgres, alerting, and audit.

## Architecture (overview)

- **event-ingest-service**: REST API → publishes events to Kafka.
- **pricing-consumer-service**: Consumes events → writes to Postgres.
- **alerting-service**: Consumes events → triggers alerts; REST API to query alerts.
- **audit-service**: Consumes all events → immutable audit log.
- **events-schema**: Shared event envelope and types (library, no runtime).

Topic: versioned `grid.events.v1` with an event envelope (eventId, eventType, occurredAt, producedAt, source, correlationId, payload, version). Reliability: idempotent consumers, retries, DLT. Observability: OpenTelemetry, traces, metrics, correlationId in logs.

*(Diagram and full runbook will be expanded in later phases.)*

## Prerequisites

- **Java 21**
- **Docker** (for Kafka, Postgres, observability stack — Phase 3)
- **Gradle** (or use the project’s Gradle Wrapper)

## Project structure

```
event-driven-microservices/
├── build.gradle
├── settings.gradle.kts
├── docker-compose.yml       # Kafka, Postgres, OTel, Prometheus, Grafana (Phase 3)
├── start-infra.sh          # Optional: compose up + wait for Kafka/Postgres
├── .env.example             # Copy to .env for Postgres/Grafana overrides
├── docker/
│   ├── otel-collector-config.yaml
│   └── prometheus.yml
├── gradle/wrapper/
├── events-schema/           # shared event contract (library)
├── event-ingest-service/    # REST + Kafka producer (port 8080)
├── pricing-consumer-service/# Kafka consumer + Postgres (port 8081)
├── alerting-service/       # Kafka consumer + alerts API (port 8082)
├── audit-service/          # Kafka consumer + audit log (port 8083)
└── README.md
```

### Event contract (Phase 2)

All services depend on **events-schema**, which defines:

- **`EventEnvelope`**: `eventId` (UUID), `eventType`, `occurredAt`, `producedAt`, `source`, `correlationId`, `payload` (JSON), `version`. Used for every message on `grid.events.v1`.
- **`EventTypes`**: constants `PRICING`, `ALERT`, `AUDIT`, `GENERIC` for routing and filtering.

## How to run

### Start infrastructure (Phase 3), event-ingest-service (Phase 4), and pricing-consumer-service (Phase 5)

From the project root, start infra and both app services (builds images on first run):

```bash
docker compose up -d --build
```

To start only infrastructure (no ingest service), start these services (include **kafka-exporter** so the Grafana Kafka dashboard works):

```bash
docker compose up -d kafka postgres otel-collector kafka-exporter prometheus grafana
```

Or use the helper script (starts containers and waits for Kafka and Postgres to be ready):

```bash
./start-infra.sh
```

Optional: copy `.env.example` to `.env` to override Postgres password, host port, Grafana admin password, or `API_KEY` for the ingest service.

| Service           | Port(s)        | Purpose                          |
|-------------------|----------------|-----------------------------------|
| Kafka             | 9092           | Bootstrap for producers/consumers |
| Postgres          | 54320 (host)→5432 (container) | pricing-consumer, audit-service (set `POSTGRES_PORT` in .env to change host port) |
| OTel Collector   | 4317 (gRPC), 4318 (HTTP) | Receive traces/metrics from apps |
| Prometheus        | 9090           | Metrics (scrapes OTel, Kafka exporter) |
| Grafana           | 3000           | Dashboards (admin / admin); Prometheus + Kafka overview auto-provisioned |
| kafka-exporter    | 9308           | Kafka metrics for Prometheus (broker, topics, consumer lag) |
| event-ingest-service   | 8080        | REST API for event ingest (when run via compose) |
| pricing-consumer-service | 8081      | Consumes events from Kafka → Postgres; GET /api/pricing-events (Phase 5) |
| alerting-service         | 8082      | Consumes events → alerts in Postgres; GET /api/alerts (Phase 6) |

Stop: `docker compose down`. Data in Postgres/Prometheus/Grafana is in Docker volumes.

### Verify end-to-end (ingest → Kafka → pricing-consumer → Postgres)

Run these in order (project root; stack up: `docker compose up -d --build`).

1. **Containers up:** `docker compose ps` — all services show **Up**.
2. **Ingest accepts event:**  
   `curl -X POST http://localhost:8080/api/events -H "X-API-Key: dev-key" -H "Content-Type: application/json" -d '{"eventType":"PRICING","payload":{"price":99}}'`  
   Expect **HTTP 202** and JSON with `eventId`, `eventType`, `status: accepted`.
3. **Consumer stored it:** Wait 3–5 seconds, then  
   `curl -s http://localhost:8081/api/pricing-events`  
   Expect JSON with `content` array containing the event (`eventType` PRICING, same payload).
4. **Optional – get by ID:** Copy an `eventId` from step 3 and  
   `curl -s http://localhost:8081/api/pricing-events/<eventId>`  
   Expect single-event JSON.
5. **Optional – Postgres:**  
   `docker compose exec postgres psql -U grid -d grid -c 'SELECT event_id, event_type FROM pricing_event LIMIT 5;'`  
   Expect rows in `pricing_event`.

**One-liner:**  
`curl -X POST http://localhost:8080/api/events -H "X-API-Key: dev-key" -H "Content-Type: application/json" -d '{"eventType":"PRICING","payload":{"price":99}}' ; sleep 4 ; curl -s http://localhost:8081/api/pricing-events | head -20`

If ingest returns 401, use `X-API-Key: dev-key` (or your `.env` value). If pricing-events is empty, wait longer or run `docker compose logs pricing-consumer-service`.

**Kafka dashboard shows “No data” on every panel?**

That usually means **Prometheus is not scraping the Kafka exporter** (or Grafana is not querying this Prometheus).

1. **Check Prometheus targets**  
   Open **http://localhost:9090/targets**. Find the **kafka-exporter** target. If it is **DOWN** or missing, Prometheus cannot reach the exporter.

   **If the kafka-exporter target does not appear at all:** Prometheus is probably using an old config. Restart Prometheus so it reloads `docker/prometheus.yml`:
   ```bash
   docker compose restart prometheus
   ```
   Wait a few seconds, then open **http://localhost:9090/targets** again. You should see the **kafka-exporter** job. To confirm which config Prometheus loaded: `curl -s 'http://localhost:9090/api/v1/status/config' | grep -A2 kafka-exporter`.

   **If Prometheus reports "lookup kafka-exporter … no such host":** Ensure all stack containers are on the same Compose network. From the project root run:
   ```bash
   docker compose down
   docker compose up -d --force-recreate
   ```
   Then run `docker network inspect event-driven-microservices_default` and confirm `grid-kafka-exporter` and `grid-prometheus` appear under `Containers`. Reload Prometheus: `curl -X POST http://localhost:9090/-/reload`.

2. **Check Grafana’s Prometheus URL**  
   In Grafana: **Connections** → **Data sources** → **Prometheus**. The URL must be **`http://prometheus:9090`** (so Grafana in Docker can reach Prometheus). If it is **`http://localhost:9090`**, change it to **`http://prometheus:9090`** and **Save & test**.

3. **Confirm the exporter is up**  
   Run: `curl -s http://localhost:9308/metrics | head -5`  
   You should see Prometheus-style metrics. If the connection is refused, run: `docker compose ps kafka-exporter` and `docker compose up -d kafka-exporter` if needed.

4. **Reload Prometheus** (if you changed config)  
   `curl -X POST http://localhost:9090/-/reload`  
   Then check **Targets** again; **kafka-exporter** should be **UP**.

5. In Grafana, set time range to **Last 5 minutes** and refresh the Kafka overview dashboard.

**First panel shows UP but "Topics discovered" (or other panels) show No data?**  
Exporter is scraped but may not see Kafka or no topics exist yet. 1) Same network: `docker network inspect event-driven-microservices_default` should list `grid-kafka-exporter` and `grid-kafka`. 2) Restart exporter after Kafka is up: `docker compose restart kafka-exporter`. 3) Send one event so topic `grid.events.v1` exists (sample curl in README). 4) Confirm topic metrics: `curl -s http://localhost:9308/metrics | grep kafka_topic_partitions`.

**Kafka dashboard shows UP but “no messages” for your topic?**

1. Send an event, then run: `curl -s http://localhost:9308/metrics | grep grid.events` — you should see `topic="grid.events.v1"`.
2. Restart the exporter and resend: `docker compose restart kafka-exporter`, send an event again, wait ~30s, refresh the dashboard.

### Build and run apps

```bash
./gradlew clean build
```

If the Gradle Wrapper is not present yet (e.g. no `gradlew` or `gradle/wrapper/gradle-wrapper.jar`), generate it once (requires a local Gradle install, e.g. SDKMAN, Homebrew, or from [gradle.org](https://gradle.org/install/)):

```bash
gradle wrapper
```

Then:

```bash
./gradlew clean build
```

### Run a single service

```bash
./gradlew :event-ingest-service:bootRun
# Or: pricing-consumer-service, alerting-service, audit-service
```

### Run tests only

```bash
./gradlew check
# Or per module:
./gradlew :events-schema:test
./gradlew :event-ingest-service:test
./gradlew :pricing-consumer-service:test
./gradlew :alerting-service:test
./gradlew :audit-service:test
```

## Runbook — Phase 1: Skeleton

| Step | Command / action | Expected outcome |
|------|------------------|------------------|
| 1 | `./gradlew projects` | Lists root + 5 modules: events-schema, event-ingest-service, pricing-consumer-service, alerting-service, audit-service. |
| 2 | `./gradlew clean build` | All modules compile; all tests pass. |
| 3 | `./gradlew :event-ingest-service:bootRun` | Service starts on port 8080; no Kafka yet so no producer. |
| 4 | `./gradlew check` | All unit/context-load tests pass. |

**Definition of done for Phase 1:** All modules build; each service has a runnable Boot app; at least one test per module (context load or placeholder); README and this runbook section in place.

---

## Runbook — Phase 2: Event contract

| Step | Command / action | Expected outcome |
|------|------------------|------------------|
| 1 | `./gradlew :events-schema:build` | events-schema compiles and tests pass (EventEnvelope + EventTypes, JSON round-trip). |
| 2 | `./gradlew clean build` | All modules build; services compile against events-schema. |
| 3 | `./gradlew :events-schema:test` | EventEnvelopeTest and EventsSchemaModuleTest pass. |

**Definition of done for Phase 2:** EventEnvelope and EventTypes are in events-schema; JSON serialization/deserialization tested; all services depend on events-schema; README and runbook updated.

---

## Runbook — Phase 3: Local infrastructure

| Step | Command / action | Expected outcome |
|------|------------------|------------------|
| 1 | `docker compose up -d` | Containers start: kafka, postgres, otel-collector, kafka-exporter, prometheus, grafana. |
| 2 | `docker compose ps` | All 5 services show as running (Up). |
| 3 | `./start-infra.sh` (optional) | Same as 1, then waits until Kafka and Postgres report healthy. |
| 4 | Open http://localhost:9090 | Prometheus UI; can run a query (e.g. `up`). |
| 5 | Open http://localhost:3000 | Grafana login (admin / admin). Prometheus is auto-provisioned; open **Dashboards** → **Kafka overview** for Kafka metrics. |
| 6 | `docker compose down` | Containers and networks removed; volumes kept. |

**Definition of done for Phase 3:** One command (`docker compose up -d` or `./start-infra.sh`) brings up Kafka, Postgres, OTel Collector, Prometheus, and Grafana; ports and env are documented; README and runbook updated.

---

## Runbook — Phase 4: Event ingest service

| Step | Command / action | Expected outcome |
|------|------------------|------------------|
| 1 | Start infra: `docker compose up -d` (or `./start-infra.sh`) | Kafka and Postgres running. |
| 2 | `./gradlew :event-ingest-service:bootRun` | Ingest service starts on port 8080. |
| 3 | `curl -X POST http://localhost:8080/api/events -H "Content-Type: application/json" -H "X-API-Key: dev-key" -H "X-Correlation-Id: my-corr-1" -d '{"eventType":"PRICING","payload":{"price":100,"currency":"USD"}}'` | HTTP 202; response has `eventId`, `eventType`, `status: accepted`. |
| 4 | Same request without `X-API-Key` (or wrong key) | HTTP 401. |
| 5 | `./gradlew :event-ingest-service:test` | EventIngestControllerTest and ApiKeyFilterTest pass. |

**Definition of done for Phase 4:** REST POST to `/api/events` with API key publishes an EventEnvelope to `grid.events.v1`; correlationId in headers; API key required; tests and runbook updated.

### Sample curl (Phase 4)

```bash
# Ingest an event (default API key is dev-key)
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-key" \
  -H "X-Correlation-Id: my-correlation-123" \
  -d '{"eventType":"PRICING","payload":{"price":100,"currency":"USD"}}'
```

Override API key or Kafka via env: `API_KEY=my-secret ./gradlew :event-ingest-service:bootRun` or `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`.

---

## Phase 5: pricing-consumer-service (Kafka consumer + Postgres)

**What was implemented**

- **Kafka consumer** that subscribes to `grid.events.v1` (same topic the ingest service publishes to) in consumer group `pricing-consumer-group`. It deserializes messages as `EventEnvelope` (JSON) and **filters by event type**: only **PRICING** and **GENERIC** events are persisted; ALERT and AUDIT are skipped (handled by other services).
- **Persistence** in Postgres via Spring Data JPA. Each consumed event is stored in table **`pricing_event`** with: `event_id` (UUID, primary key), `event_type`, `occurred_at`, `produced_at`, `source`, `correlation_id`, `payload` (JSON text), `consumed_at`. **Idempotency** is enforced by using `event_id` as the primary key: if the same event is redelivered (e.g. after a consumer restart or retry), the insert would violate the unique constraint and the consumer catches `DataIntegrityViolationException` and skips the duplicate (message is still acknowledged so it is not redelivered indefinitely).
- **REST API** (read-only) on port **8081**:
  - **GET /api/pricing-events** — paginated list of stored events (newest first). Query params: `page` (default 0), `size` (default 20, max 100).
  - **GET /api/pricing-events/{eventId}** — fetch a single event by ID (404 if not found).
- **Configuration**: `application.yml` wires Kafka consumer (bootstrap servers, group-id, JsonDeserializer for `EventEnvelope` with trusted package `demo.grid.schema`), Postgres datasource (URL, user, password from env), and JPA (ddl-auto: update, PostgreSQL dialect). Topic name is configurable via `app.kafka.topic` (default `grid.events.v1`).
- **Docker**: `pricing-consumer-service/Dockerfile` (multi-stage build from repo root); **docker-compose** runs the service with `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`, `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/grid`, and Postgres credentials from `.env`. The service **depends_on** kafka and postgres so it starts after they are up.

**How it fits in the overall system**

1. **event-ingest-service** receives REST calls and publishes `EventEnvelope` messages to Kafka topic **grid.events.v1**.
2. **pricing-consumer-service** is one of several **consumers** of that same topic. It runs in its own consumer group (`pricing-consumer-group`), so it gets a copy of every message; it only persists PRICING and GENERIC events. Other consumers (alerting-service, audit-service, in later phases) will consume the same stream for their own use cases.
3. Events are **durable** in Postgres: you can query them via the REST API or directly in the database. The primary key on `event_id` ensures at-most-once persistence per event even if Kafka redelivers.
4. **End-to-end flow**:  
   `POST /api/events` (ingest) → Kafka `grid.events.v1` → pricing-consumer reads message → persists to `pricing_event` → **GET /api/pricing-events** returns the stored events.

**Runbook — Phase 5**

| Step | Command / action | Expected outcome |
|------|------------------|------------------|
| 1 | Start infra + apps: `docker compose up -d --build` | event-ingest-service and pricing-consumer-service (and Kafka, Postgres, etc.) running. |
| 2 | Send a PRICING event: `curl -X POST http://localhost:8080/api/events -H "X-API-Key: dev-key" -H "Content-Type: application/json" -d '{"eventType":"PRICING","payload":{"price":99}}'` | HTTP 202 from ingest. |
| 3 | After a few seconds: `curl -s http://localhost:8081/api/pricing-events` | JSON with `content` array containing the persisted event (eventId, eventType PRICING, payload, consumedAt, etc.). |
| 4 | `GET /api/pricing-events?page=0&size=5` | Paginated list; `totalElements` and `totalPages` in response. |
| 5 | `./gradlew :pricing-consumer-service:test` | Unit tests (PricingEventConsumerTest, PricingEventControllerTest) and context-load test pass. |

**Definition of done for Phase 5:** pricing-consumer-service consumes from `grid.events.v1`, persists PRICING and GENERIC events to Postgres with idempotency by eventId, exposes GET /api/pricing-events and GET /api/pricing-events/{id}; runs in Docker with Kafka and Postgres; tests and README/runbook updated.

---

## Phase 6: alerting-service (Kafka consumer + Postgres alerts API)

**What was implemented**

- **Kafka consumer** subscribing to `grid.events.v1` in consumer group **alerting-consumer-group**. Filters by event type: only **ALERT** and **GENERIC** events are persisted as alerts; PRICING and AUDIT are skipped.
- **Persistence** in Postgres: table **`alert`** with `event_id` (UUID, PK), `event_type`, `severity` (ALERT→HIGH, GENERIC→NORMAL), `summary` (payload JSON or fallback), `source`, `correlation_id`, `created_at`. **Idempotency** by `event_id` (duplicate deliveries skipped).
- **REST API** (port **8082**): **GET /api/alerts** (paginated, newest first; `page`, `size`), **GET /api/alerts/{eventId}** (single alert or 404).
- **Configuration**: Same pattern as pricing-consumer (Kafka consumer, Postgres, JPA, `app.kafka.topic`). Same Postgres DB (`grid`) and credentials.
- **Docker**: `alerting-service/Dockerfile`; **docker-compose** service **alerting-service** with Kafka and Postgres env, depends_on kafka and postgres.

**How it fits in the system**

- Ingest publishes to `grid.events.v1`; **alerting-service** consumes a copy (its own group), persists ALERT and GENERIC as alerts. **pricing-consumer-service** and **alerting-service** both consume the same topic independently.
- Flow: `POST /api/events` with `eventType: "ALERT"` → Kafka → alerting-consumer → row in `alert` → **GET /api/alerts** returns it (severity HIGH). Same for GENERIC (severity NORMAL).

**Runbook — Phase 6**

| Step | Command / action | Expected outcome |
|------|------------------|------------------|
| 1 | `docker compose up -d --build` | alerting-service (and others) running. |
| 2 | Send an ALERT event: `curl -X POST http://localhost:8080/api/events -H "X-API-Key: dev-key" -H "Content-Type: application/json" -d '{"eventType":"ALERT","payload":{"message":"High load"}}'` | HTTP 202. |
| 3 | After a few seconds: `curl -s http://localhost:8082/api/alerts` | JSON with `content` array; one alert, `eventType` ALERT, `severity` HIGH, `summary` contains payload. |
| 4 | `./gradlew :alerting-service:test` | AlertEventConsumerTest, AlertControllerTest, context-load pass. |

**Definition of done for Phase 6:** alerting-service consumes from `grid.events.v1`, persists ALERT and GENERIC events to Postgres as alerts (idempotent by eventId), exposes GET /api/alerts and GET /api/alerts/{eventId}; runs in Docker; tests and README updated.

---

*Failure-simulation (poison message, DLT) will be added in later phases.*
