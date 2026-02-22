package demo.grid.audit.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.grid.audit.config.AuditProperties;
import demo.grid.audit.repository.AuditEventRepository;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditEventRepository repository;

    @Mock
    private AuditProperties properties;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuditEventConsumer consumer;

    @Test
    void persistsAnyEventType() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                EventTypes.AUDIT,
                now,
                now,
                "event-ingest-service",
                "corr-1",
                null,
                "1"
        );

        consumer.onEvent(envelope, "corr-1");

        ArgumentCaptor<demo.grid.audit.domain.AuditEventEntity> captor =
                ArgumentCaptor.forClass(demo.grid.audit.domain.AuditEventEntity.class);
        verify(repository).save(captor.capture());
        demo.grid.audit.domain.AuditEventEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getEventType()).isEqualTo(EventTypes.AUDIT);
        assertThat(saved.getCorrelationId()).isEqualTo("corr-1");
        assertThat(saved.getVersion()).isEqualTo("1");
    }
}
