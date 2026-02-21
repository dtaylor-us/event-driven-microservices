package demo.grid.ingest.api;

import demo.grid.schema.EventEnvelope;
import demo.grid.ingest.service.EventIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventIngestController.class)
@TestPropertySource(properties = {"app.api-key=test-key"})
class EventIngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventIngestService ingestService;

    @Test
    void ingest_returns202AndResponse() throws Exception {
        UUID eventId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                "PRICING",
                Instant.now(),
                Instant.now(),
                "event-ingest-service",
                "corr-1",
                null,
                "1"
        );
        when(ingestService.publish(eq("PRICING"), any(), eq("corr-1"))).thenReturn(envelope);

        mockMvc.perform(post("/api/events")
                        .header("X-API-Key", "test-key")
                        .header("X-Correlation-Id", "corr-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"PRICING\",\"payload\":{\"price\":100}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.eventType").value("PRICING"))
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(ingestService).publish(eq("PRICING"), any(), eq("corr-1"));
    }

    @Test
    void ingest_withoutCorrelationId_callsServiceWithNullCorrelation() throws Exception {
        UUID eventId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                "GENERIC",
                Instant.now(),
                Instant.now(),
                "event-ingest-service",
                eventId.toString(),
                null,
                "1"
        );
        when(ingestService.publish(eq("GENERIC"), any(), isNull())).thenReturn(envelope);

        mockMvc.perform(post("/api/events")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"GENERIC\"}"))
                .andExpect(status().isAccepted());

        verify(ingestService).publish(eq("GENERIC"), any(), isNull());
    }
}
