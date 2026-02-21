package demo.grid.pricing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted event record for pricing (and optionally generic) events consumed from grid.events.v1.
 * eventId is the primary key for idempotent consumption (duplicate events are skipped).
 */
@Entity
@Table(name = "pricing_event")
public class PricingEventEntity {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "produced_at", nullable = false)
    private Instant producedAt;

    @Column(name = "source", nullable = false, length = 256)
    private String source;

    @Column(name = "correlation_id", length = 256)
    private String correlationId;

    @Column(name = "payload", columnDefinition = "TEXT", length = 65535)
    private String payload;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected PricingEventEntity() {
    }

    public PricingEventEntity(UUID eventId, String eventType, Instant occurredAt, Instant producedAt,
                              String source, String correlationId, String payload, Instant consumedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.producedAt = producedAt;
        this.source = source;
        this.correlationId = correlationId;
        this.payload = payload;
        this.consumedAt = consumedAt;
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getProducedAt() { return producedAt; }
    public String getSource() { return source; }
    public String getCorrelationId() { return correlationId; }
    public String getPayload() { return payload; }
    public Instant getConsumedAt() { return consumedAt; }
}
