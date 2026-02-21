package demo.grid.alerting.api;

import demo.grid.alerting.domain.AlertEntity;
import demo.grid.alerting.repository.AlertRepository;
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

@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AlertRepository repository;

    @Test
    void listAlertsReturnsPage() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AlertEntity entity = new AlertEntity(
                id, "ALERT", "HIGH", "summary", "ingest", "corr", now
        );
        Page<AlertEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
        when(repository.findAllByOrderByCreatedAtDesc(any())).thenReturn(page);

        mvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].eventId").value(id.toString()))
                .andExpect(jsonPath("$.content[0].eventType").value("ALERT"))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(repository).findAllByOrderByCreatedAtDesc(any());
    }

    @Test
    void getAlertReturns404WhenNotFound() throws Exception {
        when(repository.findById(any(UUID.class))).thenReturn(Optional.empty());

        mvc.perform(get("/api/alerts/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAlertReturnsAlertWhenFound() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AlertEntity entity = new AlertEntity(
                id, "ALERT", "HIGH", "test summary", "ingest", "c1", now
        );
        when(repository.findById(eq(id))).thenReturn(Optional.of(entity));

        mvc.perform(get("/api/alerts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(id.toString()))
                .andExpect(jsonPath("$.summary").value("test summary"));
    }
}
