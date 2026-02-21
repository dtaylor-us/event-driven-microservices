package demo.grid.alerting.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert")
public class AlertEntity {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "summary", columnDefinition = "TEXT", length = 65535)
    private String summary;

    @Column(name = "source", nullable = false, length = 256)
    private String source;

    @Column(name = "correlation_id", length = 256)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AlertEntity() {
    }

    public AlertEntity(UUID eventId, String eventType, String severity, String summary,
                       String source, String correlationId, Instant createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.severity = severity;
        this.summary = summary;
        this.source = source;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getSeverity() { return severity; }
    public String getSummary() { return summary; }
    public String getSource() { return source; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
}
