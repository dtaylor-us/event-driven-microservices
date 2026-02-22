package demo.grid.audit.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.grid.audit.config.AuditProperties;
import demo.grid.audit.domain.AuditEventEntity;
import demo.grid.audit.repository.AuditEventRepository;
import demo.grid.schema.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.time.Instant;

/**
 * Consumes every event from grid.events.v1 and persists to the audit log (all event types).
 * Idempotent by eventId.
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditEventRepository repository;
    private final AuditProperties properties;
    private final ObjectMapper objectMapper;

    public AuditEventConsumer(AuditEventRepository repository,
                              AuditProperties properties,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    private static final String MDC_CORRELATION_ID = "correlationId";

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            exclude = {DataIntegrityViolationException.class}
    )
    @KafkaListener(topics = "${app.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(@Payload EventEnvelope envelope,
                        @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        String correlationId = envelope.correlationId() != null ? envelope.correlationId() : envelope.eventId().toString();
        MDC.put(MDC_CORRELATION_ID, correlationId);
        try {
            String payloadJson = serializePayload(envelope);
            Instant auditedAt = Instant.now();

            AuditEventEntity entity = new AuditEventEntity(
                    envelope.eventId(),
                    envelope.eventType(),
                    envelope.occurredAt(),
                    envelope.producedAt(),
                    envelope.source(),
                    envelope.correlationId(),
                    payloadJson,
                    envelope.version(),
                    auditedAt
            );

            try {
                repository.save(entity);
                log.info("Audited event eventId={} type={} correlationId={}",
                        envelope.eventId(), envelope.eventType(), envelope.correlationId());
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate audit ignored (idempotent) eventId={}", envelope.eventId());
            }
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    public String topic() {
        return properties.topic();
    }

    private String serializePayload(EventEnvelope envelope) {
        if (envelope.payload() == null) return null;
        try {
            return objectMapper.writeValueAsString(envelope.payload());
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize payload for eventId={}", envelope.eventId(), e);
            return envelope.payload().toString();
        }
    }
}
