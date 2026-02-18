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
├── gradle/
│   └── wrapper/
├── events-schema/           # shared event contract (library)
│   └── EventEnvelope, EventTypes
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

## How to run (Phase 1)

Infrastructure (Kafka, Postgres, etc.) is added in Phase 3. For Phase 1 you only build and run tests.

### Build everything

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

*Sample curl commands, Docker Compose, and failure-simulation steps will be added in later phases.*
