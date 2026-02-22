# Event-Driven Microservices

A **Java 21 + Spring Boot 3.4** event-driven demo using **Apache Kafka**: REST ingest, multiple consumers (pricing, alerting, audit), PostgreSQL persistence, and observability (Prometheus, Grafana, OpenAPI/Swagger).

---

## Table of Contents

- [Architecture](#architecture)
- [Event Flow](#event-flow)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Running the System](#running-the-system)
- [Testing](#testing)
- [API Documentation](#api-documentation)
- [Observability](#observability)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)
- [Development](#development)

---

## Architecture

### High-Level Overview

```
                    ┌─────────────────────────────────────────────────────────────┐
                    │                    Apache Kafka (grid.events.v1)             │
                    └─────────────────────────────────────────────────────────────┘
                                              │
     ┌──────────────┐                         │                    ┌─────────────────┐
     │   Clients    │                         │                    │   Prometheus    │
     └──────┬───────┘                         │                    │   Grafana       │
            │ POST /api/events                 │                    │   kafka-exporter│
            │ X-API-Key                        │                    └────────┬────────┘
            ▼                                 │                             │ scrape
┌───────────────────────┐                    │                    ┌────────▼────────┐
│ event-ingest-service  │────────────────────┼────────────────────│  OTel Collector │
│ (8080)                │  publish           │                    └─────────────────┘
│ REST → Kafka producer │  EventEnvelope     │
└───────────────────────┘                    │
                                            │
              ┌─────────────────────────────┼─────────────────────────────┐
              │                             │                             │
              ▼                             ▼                             ▼
┌───────────────────────┐     ┌───────────────────────┐     ┌───────────────────────┐
│ pricing-consumer      │     │ alerting-service      │     │ audit-service         │
│ (8081)                │     │ (8082)                │     │ (8083)                │
│ PRICING, GENERIC →    │     │ ALERT, GENERIC →      │     │ All events →          │
│ pricing_event         │     │ alert                 │     │ audit_event           │
│ GET /api/pricing-     │     │ GET /api/alerts       │     │ GET /api/audit-events  │
│ events                │     │                       │     │                        │
└───────────┬───────────┘     └───────────┬───────────┘     └───────────┬───────────┘
            │                             │                             │
            └─────────────────────────────┼─────────────────────────────┘
                                          ▼
                              ┌───────────────────────┐
                              │ PostgreSQL (grid)     │
                              │ pricing_event, alert, │
                              │ audit_event           │
                              └───────────────────────┘
```

### Components

| Component | Role |
|-----------|------|
| **events-schema** | Shared library: `EventEnvelope` and `EventTypes`. No runtime; used by all services. |
| **event-ingest-service** | REST API (port 8080). Accepts events via `POST /api/events`, validates API key, publishes to Kafka topic `grid.events.v1`. |
| **pricing-consumer-service** | Consumes from `grid.events.v1` (group `pricing-consumer-group`). Persists **PRICING** and **GENERIC** to `pricing_event`. Exposes read API on 8081. |
| **alerting-service** | Consumes same topic (group `alerting-consumer-group`). Persists **ALERT** and **GENERIC** to `alert`. Exposes read API on 8082. |
| **audit-service** | Consumes same topic (group `audit-consumer-group`). Persists **all** event types to `audit_event` (immutable audit log). Exposes read API on 8083. |

### Event Contract

All messages on **`grid.events.v1`** use the **EventEnvelope** (defined in `events-schema`):

| Field | Type | Description |
|-------|------|-------------|
| `eventId` | UUID | Unique event identifier (used for idempotency). |
| `eventType` | String | One of `PRICING`, `ALERT`, `AUDIT`, `GENERIC` (see `EventTypes`). |
| `occurredAt` | Instant | When the business event occurred. |
| `producedAt` | Instant | When the ingest service published to Kafka. |
| `source` | String | Source system (e.g. `event-ingest-service`). |
| `correlationId` | String | Optional; propagated from `X-Correlation-Id` header for tracing. |
| `payload` | JSON | Opaque event payload. |
| `version` | String | Schema version (e.g. `1`). |

Consumers filter by `eventType` and persist only the events relevant to them; **audit-service** stores every event.

---

## Event Flow

1. **Client** sends `POST /api/events` with JSON body `{ "eventType": "PRICING", "payload": { ... } }` and header `X-API-Key: <key>` (and optionally `X-Correlation-Id`).
2. **event-ingest-service** validates the API key, builds an `EventEnvelope`, and publishes to Kafka topic **grid.events.v1**.
3. **Kafka** delivers the message to each consumer group (pricing, alerting, audit).
4. **pricing-consumer-service**: if `eventType` is PRICING or GENERIC, inserts into `pricing_event` (idempotent by `event_id`); otherwise skips.
5. **alerting-service**: if ALERT or GENERIC, inserts into `alert`; otherwise skips.
6. **audit-service**: inserts every event into `audit_event`.
7. Clients can query stored data via **GET /api/pricing-events**, **GET /api/alerts**, **GET /api/audit-events** (paginated).

Idempotency is enforced by using `event_id` as the primary key: duplicate deliveries (e.g. after retries) result in a constraint violation that is handled and the message is acknowledged.

---

## Project Structure

```
event-driven-microservices/
├── build.gradle                    # Root build: Spring Boot BOM, dependency management
├── settings.gradle.kts
├── docker-compose.yml              # Full stack: Kafka, Postgres, apps, Prometheus, Grafana
├── start-infra.sh                  # Optional: compose up + wait for Kafka/Postgres
├── .env.example                    # Copy to .env for overrides
├── docker/
│   ├── prometheus.yml              # Scrape configs: apps, kafka-exporter, otel-collector
│   ├── otel-collector-config.yaml
│   └── grafana/
│       └── provisioning/           # Datasources + dashboards (Kafka, Event Grid)
├── events-schema/                  # Shared EventEnvelope, EventTypes (library)
├── event-ingest-service/           # REST ingest → Kafka (8080)
├── pricing-consumer-service/       # Consumer + Postgres + REST (8081)
├── alerting-service/               # Consumer + Postgres + REST (8082)
└── audit-service/                  # Consumer + Postgres + REST (8083)
```

Each app module has its own `build.gradle.kts`, `Dockerfile`, and `src/main` / `src/test`.

---

## Prerequisites

- **Java 21**
- **Docker** and **Docker Compose** (for Kafka, Postgres, and optional full stack)
- **Gradle** (or use the project’s `./gradlew` wrapper)

---

## Quick Start

1. **Start the full stack** (infrastructure + all four app services):

   ```bash
   docker compose up -d --build
   ```

2. **Verify** containers: `docker compose ps` — all services should be **Up**.

3. **Send an event**:

   ```bash
   curl -X POST http://localhost:8080/api/events \
     -H "X-API-Key: dev-key" \
     -H "Content-Type: application/json" \
     -d '{"eventType":"PRICING","payload":{"price":99,"currency":"USD"}}'
   ```

   Expect **HTTP 202** with `eventId`, `eventType`, `status: accepted`.

4. **Query the consumer** (after a few seconds):

   ```bash
   curl -s http://localhost:8081/api/pricing-events
   ```

   You should see the event in the `content` array.

5. **Open Swagger UI**: http://localhost:8080/swagger-ui.html (ingest) or http://localhost:8081/swagger-ui.html (pricing).

6. **Open Grafana**: http://localhost:3000 (admin / admin) → Dashboards → **Event Grid - Overview** or **Kafka overview**.

---

## Running the System

### Full stack with Docker

```bash
docker compose up -d --build
```

Starts: Kafka, Postgres, OTel Collector, kafka-exporter, Prometheus, Grafana, and the four application services. Data is stored in Docker volumes; stop with `docker compose down`.

### Infrastructure only (no app services)

```bash
docker compose up -d kafka postgres otel-collector kafka-exporter prometheus grafana
```

Then run apps locally (see below) or start them later with:

```bash
docker compose up -d --build event-ingest-service pricing-consumer-service alerting-service audit-service
```

### Wait for infrastructure to be ready

```bash
./start-infra.sh
```

Starts all Compose services and waits for Kafka (port 9092) and Postgres to be ready.

### Run a single service locally (e.g. for development)

With Kafka and Postgres already running (e.g. via Docker):

```bash
./gradlew :event-ingest-service:bootRun
# Or: :pricing-consumer-service:bootRun, :alerting-service:bootRun, :audit-service:bootRun
```

Use `KAFKA_BOOTSTRAP_SERVERS=localhost:9092` and `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:54320/grid` (or your Postgres host/port) if needed. Ingest default API key: `dev-key`.

### Port reference

| Service | Port | Purpose |
|---------|------|---------|
| Kafka | 9092 | Bootstrap for producers/consumers |
| Postgres | 54320 (host) → 5432 (container) | DB for pricing, alerting, audit |
| event-ingest-service | 8080 | REST ingest API |
| pricing-consumer-service | 8081 | GET /api/pricing-events |
| alerting-service | 8082 | GET /api/alerts |
| audit-service | 8083 | GET /api/audit-events |
| Prometheus | 9090 | Metrics |
| Grafana | 3000 | Dashboards (admin / admin) |
| kafka-exporter | 9308 | Kafka metrics for Prometheus |
| OTel Collector | 4317 (gRPC), 4318 (HTTP) | Traces/metrics (optional) |

---

## Testing

### Run all tests

```bash
./gradlew test
# Or: ./gradlew check
```

### Run tests per module

```bash
./gradlew :events-schema:test
./gradlew :event-ingest-service:test
./gradlew :pricing-consumer-service:test
./gradlew :alerting-service:test
./gradlew :audit-service:test
```

### Test types

- **Unit tests**: Controllers, consumers, filters (mocked dependencies).
- **Context/slice tests**: `@SpringBootTest` with in-memory or test config.
- **Integration tests**: Pricing, Alerting, and Audit services each have an integration test that uses **Testcontainers** (Kafka + PostgreSQL). These tests are **skipped** when Docker is not available (`@Testcontainers(disabledWithoutDocker = true)`). If Docker is running, they start containers and verify end-to-end consume → persist → query.

### Build (including tests)

```bash
./gradlew clean build
```

---

## API Documentation

Each application exposes **OpenAPI 3** and **Swagger UI**:

| Service | Swagger UI | OpenAPI JSON |
|---------|------------|---------------|
| event-ingest-service | http://localhost:8080/swagger-ui.html | http://localhost:8080/v3/api-docs |
| pricing-consumer-service | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |
| alerting-service | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| audit-service | http://localhost:8083/swagger-ui.html | http://localhost:8083/v3/api-docs |

- **Ingest**: `POST /api/events` — body `{ "eventType": "PRICING"|"ALERT"|"AUDIT"|"GENERIC", "payload": { ... } }`; required header `X-API-Key`.
- **Consumers**: `GET /api/pricing-events`, `/api/alerts`, `/api/audit-events` — optional query params `page`, `size`; `GET .../api/...-events/{eventId}` or `.../api/alerts/{eventId}` for a single record.

---

## Observability

- **Metrics**: Each app exposes **Micrometer** metrics and **Prometheus** at `/actuator/prometheus`. Prometheus scrapes the apps and kafka-exporter (see `docker/prometheus.yml`).
- **Grafana**: Pre-provisioned datasource (Prometheus) and dashboards:
  - **Event Grid - Overview**: Service status, HTTP rate/errors/latency, Kafka message rate and consumer lag, JVM.
  - **Kafka overview**: Topics, partitions, consumer lag from kafka-exporter.
- **Health**: `GET /actuator/health` on each app (e.g. http://localhost:8080/actuator/health).
- **Logs**: Correlation ID is set from `X-Correlation-Id` (ingest) and from the event envelope (consumers) and included in the log pattern for tracing requests and messages across services.

---

## Configuration

### Environment variables

- **Docker Compose** (apps): `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC`, `SPRING_DATASOURCE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `API_KEY` (ingest), `OTEL_EXPORTER_OTLP_ENDPOINT` (optional).
- **Infrastructure**: Copy `.env.example` to `.env` to override `POSTGRES_PORT`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `GRAFANA_ADMIN_PASSWORD`.

### Application config

- Each service uses `application.yml` for Kafka, datasource, JPA, actuator, and SpringDoc (OpenAPI). Topic default: `grid.events.v1`.

---

## Troubleshooting

### Ingest returns 401

Use header `X-API-Key: dev-key` (or the value set in `API_KEY` / `.env`).

### pricing-events (or alerts/audit-events) empty after sending an event

- Wait a few more seconds; consumers may be processing.
- Ensure all containers are up: `docker compose ps`. If app containers exited, check logs: `docker compose logs pricing-consumer-service --tail 50`.
- Confirm Kafka is reachable from the consumer and the topic exists: send one event, then check `docker compose logs pricing-consumer-service` for errors.

### Grafana / Kafka dashboard shows "No data"

1. **Prometheus targets**: Open http://localhost:9090/targets. Ensure **kafka-exporter** and the app jobs are **UP**. If kafka-exporter is missing, restart Prometheus so it reloads `docker/prometheus.yml`: `docker compose restart prometheus`.
2. **Grafana datasource**: In Grafana → Connections → Data sources → Prometheus, set URL to **`http://prometheus:9090`** (not localhost) so Grafana in Docker can reach Prometheus.
3. **Time range**: Set Grafana time range to **Last 5–15 minutes** and refresh.
4. **Topic exists**: Send at least one event so `grid.events.v1` exists; then `curl -s http://localhost:9308/metrics | grep grid.events` should show the topic.

### Testcontainers integration test fails with CommandLine / InvalidExitValueException

Docker may be unavailable or restricted (e.g. CI/sandbox). The integration tests use `@Testcontainers(disabledWithoutDocker = true)` and will be **skipped** when Docker is not available. Run tests with Docker running to execute them, or accept skipped integration tests in environments without Docker.

---

## Development

### Build

```bash
./gradlew clean build
```

### Generate Gradle wrapper (if missing)

```bash
gradle wrapper
```

### Run one service

```bash
./gradlew :event-ingest-service:bootRun
```

Point it at local Kafka/Postgres (e.g. started with `docker compose up -d kafka postgres`).

### Conventions

- **Event contract**: All event payloads use `EventEnvelope` from `events-schema`; add new `EventTypes` and consumer logic as needed.
- **Idempotency**: Consumers use `event_id` as primary key and handle duplicate key violations by acknowledging the message without re-inserting.
- **API versioning**: Topic name `grid.events.v1` allows future schema evolution (e.g. v2) on a new topic.

### Verification runbook (summary)

| Phase | What to do | Expected |
|-------|------------|----------|
| Build | `./gradlew clean build` | All modules compile and tests pass. |
| Infra | `docker compose up -d kafka postgres ...` | Kafka 9092, Postgres ready. |
| Ingest | `POST /api/events` with `X-API-Key: dev-key` | 202, JSON with eventId. |
| Pricing | After ingest, `GET /api/pricing-events` | Event in `content` for PRICING. |
| Alerting | `POST` with `eventType: "ALERT"`, then `GET /api/alerts` | Alert with severity HIGH. |
| Audit | Any event, then `GET /api/audit-events` | All events in audit log. |
| Observability | Grafana → Event Grid - Overview | Services UP, metrics and lag visible. |

---

*Event-Driven Microservices — Java 21, Spring Boot 3.4, Kafka, PostgreSQL.*
