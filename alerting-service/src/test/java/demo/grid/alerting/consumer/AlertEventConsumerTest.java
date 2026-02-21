package demo.grid.alerting.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.grid.alerting.config.AlertingProperties;
import demo.grid.alerting.repository.AlertRepository;
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
class AlertEventConsumerTest {

    @Mock
    private AlertRepository repository;

    @Mock
    private AlertingProperties properties;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AlertEventConsumer consumer;

    @Test
    void persistsAlertEvent() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                EventTypes.ALERT,
                now,
                now,
                "event-ingest-service",
                "corr-1",
                null,
                "1"
        );

        consumer.onEvent(envelope, "corr-1");

        ArgumentCaptor<demo.grid.alerting.domain.AlertEntity> captor =
                ArgumentCaptor.forClass(demo.grid.alerting.domain.AlertEntity.class);
        verify(repository).save(captor.capture());
        demo.grid.alerting.domain.AlertEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getEventType()).isEqualTo(EventTypes.ALERT);
        assertThat(saved.getSeverity()).isEqualTo("HIGH");
        assertThat(saved.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void skipsNonAlertEventType() {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.PRICING,
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
