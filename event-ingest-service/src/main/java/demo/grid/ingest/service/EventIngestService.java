package demo.grid.ingest.service;

import demo.grid.schema.EventEnvelope;
import demo.grid.ingest.config.IngestProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

@Service
public class EventIngestService {

    private static final String SOURCE = "event-ingest-service";
    private static final String VERSION = "1";

    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;
    private final IngestProperties properties;

    public EventIngestService(
            KafkaTemplate<String, EventEnvelope> kafkaTemplate,
            IngestProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public EventEnvelope publish(String eventType, JsonNode payload, String correlationId) {
        Instant now = Instant.now();
        UUID eventId = UUID.randomUUID();
        String correlation = correlationId != null && !correlationId.isBlank() ? correlationId : eventId.toString();

        EventEnvelope envelope = new EventEnvelope(
                eventId,
                eventType != null && !eventType.isBlank() ? eventType : "GENERIC",
                now,
                now,
                SOURCE,
                correlation,
                payload,
                VERSION
        );

        Message<EventEnvelope> message = MessageBuilder
                .withPayload(envelope)
                .setHeader(KafkaHeaders.TOPIC, properties.getKafka().getTopic())
                .setHeader(KafkaHeaders.KEY, correlation)
                .setHeader("correlationId", correlation)
                .setHeader("eventId", eventId.toString())
                .build();

        kafkaTemplate.send(message);
        return envelope;
    }
}
