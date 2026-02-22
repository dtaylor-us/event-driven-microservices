package demo.grid.audit.repository;

import demo.grid.audit.domain.AuditEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    Page<AuditEventEntity> findAllByOrderByAuditedAtDesc(Pageable pageable);
}
