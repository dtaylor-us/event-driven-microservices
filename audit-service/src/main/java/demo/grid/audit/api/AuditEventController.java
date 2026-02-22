package demo.grid.audit.api;

import demo.grid.audit.domain.AuditEventEntity;
import demo.grid.audit.repository.AuditEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Audit events", description = "Read audited grid events")
@RestController
@RequestMapping("/api")
public class AuditEventController {

    private final AuditEventRepository repository;

    public AuditEventController(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "List audit events", description = "Paginated list, newest first")
    @GetMapping("/audit-events")
    public ResponseEntity<AuditEventPage> listEvents(
            @Parameter(description = "Page index (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        Page<AuditEventEntity> p = repository.findAllByOrderByAuditedAtDesc(PageRequest.of(page, size));
        List<AuditEventResponse> content = p.getContent().stream()
                .map(AuditEventResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new AuditEventPage(
                content,
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages()
        ));
    }

    @Operation(summary = "Get audit event by ID")
    @GetMapping("/audit-events/{eventId}")
    public ResponseEntity<AuditEventResponse> getEvent(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        return repository.findById(eventId)
                .map(AuditEventResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record AuditEventResponse(
            UUID eventId,
            String eventType,
            String occurredAt,
            String producedAt,
            String source,
            String correlationId,
            String payload,
            String version,
            String auditedAt
    ) {
        static AuditEventResponse from(AuditEventEntity e) {
            return new AuditEventResponse(
                    e.getEventId(),
                    e.getEventType(),
                    e.getOccurredAt() != null ? e.getOccurredAt().toString() : null,
                    e.getProducedAt() != null ? e.getProducedAt().toString() : null,
                    e.getSource(),
                    e.getCorrelationId(),
                    e.getPayload(),
                    e.getVersion(),
                    e.getAuditedAt() != null ? e.getAuditedAt().toString() : null
            );
        }
    }

    public record AuditEventPage(
            List<AuditEventResponse> content,
            int number,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
