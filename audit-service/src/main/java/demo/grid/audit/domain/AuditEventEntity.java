package demo.grid.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record for every event consumed from grid.events.v1.
 * All event types are persisted; eventId is the primary key for idempotency.
 */
@Entity
@Table(name = "audit_event")
public class AuditEventEntity {

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

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "audited_at", nullable = false)
    private Instant auditedAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(UUID eventId, String eventType, Instant occurredAt, Instant producedAt,
                            String source, String correlationId, String payload, String version, Instant auditedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.producedAt = producedAt;
        this.source = source;
        this.correlationId = correlationId;
        this.payload = payload;
        this.version = version;
        this.auditedAt = auditedAt;
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getProducedAt() { return producedAt; }
    public String getSource() { return source; }
    public String getCorrelationId() { return correlationId; }
    public String getPayload() { return payload; }
    public String getVersion() { return version; }
    public Instant getAuditedAt() { return auditedAt; }
}
