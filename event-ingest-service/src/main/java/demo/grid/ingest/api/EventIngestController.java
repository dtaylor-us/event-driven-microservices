package demo.grid.ingest.api;

import demo.grid.schema.EventEnvelope;
import demo.grid.ingest.service.EventIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Events", description = "Ingest domain events into the grid")
@RestController
@RequestMapping("/api/events")
public class EventIngestController {

    private final EventIngestService ingestService;

    public EventIngestController(EventIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Operation(summary = "Ingest an event", description = "Publishes the event to Kafka. Requires X-API-Key header.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Event accepted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key"),
            @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    @PostMapping
    public ResponseEntity<IngestResponse> ingest(
            @Parameter(description = "Optional correlation ID for request tracing")
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody EventRequest request) {
        EventEnvelope envelope = ingestService.publish(request.eventType(), request.payload(), correlationId);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new IngestResponse(envelope.eventId().toString(), envelope.eventType(), "accepted"));
    }

    public record IngestResponse(String eventId, String eventType, String status) {}
}
