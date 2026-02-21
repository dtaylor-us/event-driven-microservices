package demo.grid.ingest.api;

import demo.grid.schema.EventEnvelope;
import demo.grid.ingest.service.EventIngestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventIngestController {

    private final EventIngestService ingestService;

    public EventIngestController(EventIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    public ResponseEntity<IngestResponse> ingest(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody EventRequest request) {
        EventEnvelope envelope = ingestService.publish(request.eventType(), request.payload(), correlationId);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new IngestResponse(envelope.eventId().toString(), envelope.eventType(), "accepted"));
    }

    public record IngestResponse(String eventId, String eventType, String status) {}
}
