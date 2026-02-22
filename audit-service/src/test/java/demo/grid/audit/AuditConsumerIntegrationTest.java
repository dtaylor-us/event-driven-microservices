package demo.grid.audit;

import demo.grid.audit.repository.AuditEventRepository;
import demo.grid.schema.EventEnvelope;
import demo.grid.schema.EventTypes;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AuditConsumerIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("grid")
            .withUsername("grid")
            .withPassword("grid-secret");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @TestConfiguration
    static class TestKafkaProducerConfig {
        @Bean
        KafkaTemplate<String, EventEnvelope> testKafkaTemplate(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            Map<String, Object> props = Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                    JsonSerializer.ADD_TYPE_INFO_HEADERS, false
            );
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }
    }

    @Autowired
    KafkaTemplate<String, EventEnvelope> testKafkaTemplate;

    @Autowired
    AuditEventRepository repository;

    @Value("${app.kafka.topic}")
    String topic;

    @Test
    void anyEventTypeIsAudited() throws Exception {
        UUID eventId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(
                eventId, EventTypes.PRICING, Instant.now(), Instant.now(),
                "test-source", "corr-audit-1", null, "1");

        testKafkaTemplate.send(topic, eventId.toString(), envelope).get(5, TimeUnit.SECONDS);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(repository.findById(eventId))
                        .isPresent()
                        .get()
                        .satisfies(e -> {
                            assertThat(e.getEventType()).isEqualTo(EventTypes.PRICING);
                            assertThat(e.getEventId()).isEqualTo(eventId);
                        })
        );
    }

    @Test
    void allEventTypesAreAudited() throws Exception {
        UUID pricingId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();

        for (UUID id : new UUID[]{pricingId, alertId, auditId}) {
            String type = id == pricingId ? EventTypes.PRICING
                    : id == alertId ? EventTypes.ALERT : EventTypes.AUDIT;
            testKafkaTemplate.send(topic, id.toString(),
                    new EventEnvelope(id, type, Instant.now(), Instant.now(), "test", null, null, "1"))
                    .get(5, TimeUnit.SECONDS);
        }

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(repository.findById(pricingId)).isPresent();
            assertThat(repository.findById(alertId)).isPresent();
            assertThat(repository.findById(auditId)).isPresent();
        });
    }
}
