package demo.grid.alerting.api;

import demo.grid.alerting.domain.AlertEntity;
import demo.grid.alerting.repository.AlertRepository;
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

@RestController
@RequestMapping("/api")
public class AlertController {

    private final AlertRepository repository;

    public AlertController(AlertRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/alerts")
    public ResponseEntity<AlertPage> listAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        Page<AlertEntity> p = repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        List<AlertResponse> content = p.getContent().stream()
                .map(AlertResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new AlertPage(
                content,
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages()
        ));
    }

    @GetMapping("/alerts/{eventId}")
    public ResponseEntity<AlertResponse> getAlert(@PathVariable UUID eventId) {
        return repository.findById(eventId)
                .map(AlertResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record AlertResponse(
            UUID eventId,
            String eventType,
            String severity,
            String summary,
            String source,
            String correlationId,
            String createdAt
    ) {
        static AlertResponse from(AlertEntity e) {
            return new AlertResponse(
                    e.getEventId(),
                    e.getEventType(),
                    e.getSeverity(),
                    e.getSummary(),
                    e.getSource(),
                    e.getCorrelationId(),
                    e.getCreatedAt() != null ? e.getCreatedAt().toString() : null
            );
        }
    }

    public record AlertPage(
            List<AlertResponse> content,
            int number,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
