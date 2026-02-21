package demo.grid.ingest.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Incoming event payload for POST /api/events.
 */
public record EventRequest(String eventType, JsonNode payload) {
}
