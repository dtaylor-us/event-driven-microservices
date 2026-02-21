package demo.grid.alerting.repository;

import demo.grid.alerting.domain.AlertEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {

    Page<AlertEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
