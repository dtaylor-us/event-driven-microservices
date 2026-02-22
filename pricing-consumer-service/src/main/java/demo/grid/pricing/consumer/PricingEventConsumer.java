package demo.grid.pricing.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.grid.pricing.config.PricingProperties;
import demo.grid.pricing.domain.PricingEventEntity;
import demo.grid.pricing.repository.PricingEventRepository;
import demo.grid.schema.EventTypes;
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
 * Consumes events from grid.events.v1 and persists PRICING and GENERIC events to Postgres.
 * Uses eventId as primary key for idempotency: duplicate deliveries are skipped (constraint violation).
 */
@Component
public class PricingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PricingEventConsumer.class);

    private final PricingEventRepository repository;
    private final PricingProperties properties;
    private final ObjectMapper objectMapper;

    public PricingEventConsumer(PricingEventRepository repository,
                                PricingProperties properties,
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
            String eventType = envelope.eventType();
            if (!EventTypes.PRICING.equals(eventType) && !EventTypes.GENERIC.equals(eventType)) {
                log.debug("Skipping non-pricing event type: {}", eventType);
                return;
            }

            String payloadJson = serializePayload(envelope);
            Instant consumedAt = Instant.now();
            PricingEventEntity entity = new PricingEventEntity(
                    envelope.eventId(),
                    envelope.eventType(),
                    envelope.occurredAt(),
                    envelope.producedAt(),
                    envelope.source(),
                    envelope.correlationId(),
                    payloadJson,
                    consumedAt
            );

            try {
                repository.save(entity);
                log.info("Persisted pricing event eventId={} type={} correlationId={}",
                        envelope.eventId(), eventType, envelope.correlationId());
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate event ignored (idempotent) eventId={}", envelope.eventId());
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
