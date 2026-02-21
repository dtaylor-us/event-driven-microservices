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

### Start infrastructure (Phase 3) and event-ingest-service (Phase 4)

From the project root, start infra and the ingest service (builds the app image on first run):

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
| event-ingest-service | 8080        | REST API for event ingest (when run via compose) |

Stop: `docker compose down`. Data in Postgres/Prometheus/Grafana is in Docker volumes.

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

*Failure-simulation (poison message, DLT) will be added in later phases.*
