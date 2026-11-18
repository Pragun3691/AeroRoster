package com.aeroroster.persistence.repository;

import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QualificationRepository extends JpaRepository<Qualification, Long> {
    List<Qualification> findByAircraftTypeId(Long aircraftTypeId);

    Optional<Qualification> findByAircraftTypeIdAndRequiredRole(Long aircraftTypeId, CrewRole requiredRole);
}
