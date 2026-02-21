package demo.grid.pricing.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.grid.pricing.config.PricingProperties;
import demo.grid.pricing.repository.PricingEventRepository;
import demo.grid.schema.EventEnvelope;
import demo.grid.schema.EventTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingEventConsumerTest {

    @Mock
    private PricingEventRepository repository;

    @Mock
    private PricingProperties properties;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PricingEventConsumer consumer;

    @Test
    void persistsPricingEvent() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                EventTypes.PRICING,
                now,
                now,
                "event-ingest-service",
                "corr-1",
                null,
                "1"
        );

        consumer.onEvent(envelope, "corr-1");

        ArgumentCaptor<demo.grid.pricing.domain.PricingEventEntity> captor =
                ArgumentCaptor.forClass(demo.grid.pricing.domain.PricingEventEntity.class);
        verify(repository).save(captor.capture());
        demo.grid.pricing.domain.PricingEventEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getEventType()).isEqualTo(EventTypes.PRICING);
        assertThat(saved.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void skipsNonPricingEventType() {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.AUDIT,
                Instant.now(),
                Instant.now(),
                "source",
                null,
                null,
                "1"
        );

        consumer.onEvent(envelope, null);

        verify(repository, never()).save(any());
    }
}
