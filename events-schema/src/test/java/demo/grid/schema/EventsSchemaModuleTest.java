package demo.grid.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test that the schema module and its public API are usable.
 */
class EventsSchemaModuleTest {

    @Test
    void eventEnvelopeAndEventTypes_areAvailable() {
        assertNotNull(EventTypes.PRICING);
        assertNotNull(EventEnvelope.class);
    }
}
