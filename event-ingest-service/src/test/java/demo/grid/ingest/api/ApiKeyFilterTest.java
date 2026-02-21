package demo.grid.ingest.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import demo.grid.schema.EventEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.api-key=test-key",
    "spring.kafka.bootstrap-servers=127.0.0.1:9999"
})
class ApiKeyFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaTemplate<String, EventEnvelope> kafkaTemplate;

    @Test
    void postWithoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"PRICING\",\"payload\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithWrongApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"PRICING\",\"payload\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithCorrectApiKey_returns202() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"PRICING\",\"payload\":{\"price\":100}}"))
                .andExpect(status().isAccepted());
    }
}
