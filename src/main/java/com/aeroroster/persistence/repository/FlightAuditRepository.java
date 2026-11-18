package com.aeroroster.persistence.repository;

import com.aeroroster.persistence.entity.FlightAudit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Trigger-owned audit history. Deliberately exposes only read operations. */
@Transactional(readOnly = true)
public interface FlightAuditRepository extends Repository<FlightAudit, Long> {
    Optional<FlightAudit> findById(Long id);

    Page<FlightAudit> findAll(Pageable pageable);

    long count();

    List<FlightAudit> findByFlightIdOrderByActionTimeAscIdAsc(Long flightId);
}
