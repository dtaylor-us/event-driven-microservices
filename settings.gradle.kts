rootProject.name = "event-driven-microservices"

include(
    "events-schema",
    "event-ingest-service",
    "pricing-consumer-service",
    "alerting-service",
    "audit-service"
)
