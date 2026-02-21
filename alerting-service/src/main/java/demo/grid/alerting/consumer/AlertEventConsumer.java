package demo.grid.alerting.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.grid.alerting.config.AlertingProperties;
import demo.grid.alerting.domain.AlertEntity;
import demo.grid.alerting.repository.AlertRepository;
import demo.grid.schema.EventEnvelope;
import demo.grid.schema.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consumes events from grid.events.v1 and persists ALERT and GENERIC events as alerts.
 * Idempotent by eventId (duplicate deliveries skipped).
 */
@Component
public class AlertEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertEventConsumer.class);
    private static final String SEVERITY_NORMAL = "NORMAL";
    private static final String SEVERITY_HIGH = "HIGH";
    private static final int SUMMARY_MAX_LEN = 2000;

    private final AlertRepository repository;
    private final AlertingProperties properties;
    private final ObjectMapper objectMapper;

    public AlertEventConsumer(AlertRepository repository,
                              AlertingProperties properties,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "#{__listener.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(@Payload EventEnvelope envelope,
                        @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        String eventType = envelope.eventType();
        if (!EventTypes.ALERT.equals(eventType) && !EventTypes.GENERIC.equals(eventType)) {
            log.debug("Skipping non-alert event type: {}", eventType);
            return;
        }

        String severity = EventTypes.ALERT.equals(eventType) ? SEVERITY_HIGH : SEVERITY_NORMAL;
        String summary = buildSummary(envelope);
        Instant createdAt = Instant.now();

        AlertEntity entity = new AlertEntity(
                envelope.eventId(),
                envelope.eventType(),
                severity,
                summary,
                envelope.source(),
                envelope.correlationId(),
                createdAt
        );

        try {
            repository.save(entity);
            log.info("Persisted alert eventId={} type={} severity={} correlationId={}",
                    envelope.eventId(), eventType, severity, envelope.correlationId());
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate alert ignored (idempotent) eventId={}", envelope.eventId());
        }
    }

    public String topic() {
        return properties.topic();
    }

    private String buildSummary(EventEnvelope envelope) {
        if (envelope.payload() == null || envelope.payload().isNull())
            return envelope.eventType() + " " + envelope.eventId();
        try {
            String json = objectMapper.writeValueAsString(envelope.payload());
            if (json.length() > SUMMARY_MAX_LEN) json = json.substring(0, SUMMARY_MAX_LEN) + "...";
            return json;
        } catch (JsonProcessingException e) {
            return envelope.eventType() + " " + envelope.eventId();
        }
    }
}
