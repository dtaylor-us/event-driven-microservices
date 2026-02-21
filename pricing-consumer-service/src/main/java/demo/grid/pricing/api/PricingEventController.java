package demo.grid.pricing.api;

import demo.grid.pricing.domain.PricingEventEntity;
import demo.grid.pricing.repository.PricingEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only API to query persisted pricing events (for demo and debugging).
 */
@RestController
@RequestMapping("/api")
public class PricingEventController {

    private final PricingEventRepository repository;

    public PricingEventController(PricingEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/pricing-events")
    public ResponseEntity<PricingEventPage> listEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        Page<PricingEventEntity> p = repository.findAllByOrderByConsumedAtDesc(PageRequest.of(page, size));
        List<PricingEventResponse> content = p.getContent().stream()
                .map(PricingEventResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new PricingEventPage(
                content,
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages()
        ));
    }

    @GetMapping("/pricing-events/{eventId}")
    public ResponseEntity<PricingEventResponse> getEvent(@PathVariable UUID eventId) {
        return repository.findById(eventId)
                .map(PricingEventResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record PricingEventResponse(
            UUID eventId,
            String eventType,
            String occurredAt,
            String producedAt,
            String source,
            String correlationId,
            String payload,
            String consumedAt
    ) {
        static PricingEventResponse from(PricingEventEntity e) {
            return new PricingEventResponse(
                    e.getEventId(),
                    e.getEventType(),
                    e.getOccurredAt() != null ? e.getOccurredAt().toString() : null,
                    e.getProducedAt() != null ? e.getProducedAt().toString() : null,
                    e.getSource(),
                    e.getCorrelationId(),
                    e.getPayload(),
                    e.getConsumedAt() != null ? e.getConsumedAt().toString() : null
            );
        }
    }

    public record PricingEventPage(
            List<PricingEventResponse> content,
            int number,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
