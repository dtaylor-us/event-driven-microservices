package demo.grid.pricing.repository;

import demo.grid.pricing.domain.PricingEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PricingEventRepository extends JpaRepository<PricingEventEntity, UUID> {

    Page<PricingEventEntity> findAllByOrderByConsumedAtDesc(Pageable pageable);
}
