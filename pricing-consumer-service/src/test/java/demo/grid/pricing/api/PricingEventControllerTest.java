package demo.grid.pricing.api;

import demo.grid.pricing.domain.PricingEventEntity;
import demo.grid.pricing.repository.PricingEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PricingEventController.class)
class PricingEventControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PricingEventRepository repository;

    @Test
    void listEventsReturnsPage() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        PricingEventEntity entity = new PricingEventEntity(
                id, "PRICING", now, now, "ingest", "corr", "{}", now
        );
        Page<PricingEventEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
        when(repository.findAllByOrderByConsumedAtDesc(any())).thenReturn(page);

        mvc.perform(get("/api/pricing-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].eventId").value(id.toString()))
                .andExpect(jsonPath("$.content[0].eventType").value("PRICING"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(repository).findAllByOrderByConsumedAtDesc(any());
    }

    @Test
    void getEventReturns404WhenNotFound() throws Exception {
        when(repository.findById(any(UUID.class))).thenReturn(Optional.empty());

        mvc.perform(get("/api/pricing-events/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEventReturnsEventWhenFound() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        PricingEventEntity entity = new PricingEventEntity(
                id, "PRICING", now, now, "ingest", "c1", "{\"x\":1}", now
        );
        when(repository.findById(eq(id))).thenReturn(Optional.of(entity));

        mvc.perform(get("/api/pricing-events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(id.toString()))
                .andExpect(jsonPath("$.payload").value("{\"x\":1}"));
    }
}
