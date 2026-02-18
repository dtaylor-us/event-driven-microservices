package demo.grid.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventEnvelopeTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void roundTrip_serializesAndDeserializes() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2025-02-18T12:00:00Z");
        Instant producedAt = Instant.parse("2025-02-18T12:00:01Z");
        ObjectNode payload = objectMapper.createObjectNode().put("price", 100).put("currency", "USD");

        EventEnvelope envelope = new EventEnvelope(
                eventId,
                EventTypes.PRICING,
                occurredAt,
                producedAt,
                "event-ingest-service",
                "corr-123",
                payload,
                "1"
        );

        String json = objectMapper.writeValueAsString(envelope);
        assertNotNull(json);
        assertNotNull(objectMapper.readTree(json).get("eventId"));
        assertNotNull(objectMapper.readTree(json).get("occurredAt"));

        EventEnvelope restored = objectMapper.readValue(json, EventEnvelope.class);
        assertEquals(envelope.eventId(), restored.eventId());
        assertEquals(envelope.eventType(), restored.eventType());
        assertEquals(envelope.occurredAt(), restored.occurredAt());
        assertEquals(envelope.producedAt(), restored.producedAt());
        assertEquals(envelope.source(), restored.source());
        assertEquals(envelope.correlationId(), restored.correlationId());
        assertEquals(envelope.version(), restored.version());
        assertEquals(envelope.payload().get("price").asInt(), restored.payload().get("price").asInt());
    }

    @Test
    void roundTrip_withNullCorrelationIdAndPayload() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        EventEnvelope envelope = new EventEnvelope(
                eventId,
                EventTypes.GENERIC,
                now,
                now,
                "test-source",
                null,
                null,
                "1"
        );

        String json = objectMapper.writeValueAsString(envelope);
        EventEnvelope restored = objectMapper.readValue(json, EventEnvelope.class);
        assertEquals(envelope.eventId(), restored.eventId());
        assertNull(restored.correlationId());
        // Jackson may deserialize null payload as NullNode instead of Java null
        assertTrue(restored.payload() == null || restored.payload().isNull());
    }

    @Test
    void eventTypes_areNonEmptyConstants() {
        assertEquals("PRICING", EventTypes.PRICING);
        assertEquals("ALERT", EventTypes.ALERT);
        assertEquals("AUDIT", EventTypes.AUDIT);
        assertEquals("GENERIC", EventTypes.GENERIC);
    }
}
