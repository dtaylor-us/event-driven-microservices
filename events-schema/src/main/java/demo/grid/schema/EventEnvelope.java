package demo.grid.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned event envelope used for all messages on grid.events.v1.
 * Carries identity, timing, source, correlation, and an opaque JSON payload.
 */
public record EventEnvelope(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("producedAt") Instant producedAt,
        @JsonProperty("source") String source,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("payload") JsonNode payload,
        @JsonProperty("version") String version) {

    @JsonCreator
    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(producedAt, "producedAt");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(version, "version");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventEnvelope that = (EventEnvelope) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}
