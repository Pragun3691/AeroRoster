package com.aeroroster.persistence.repository;

import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AircraftRepository extends JpaRepository<Aircraft, Long> {
    List<Aircraft> findByActiveTrue();

    List<Aircraft> findByAircraftTypeId(Long aircraftTypeId);

    Optional<Aircraft> findByRegistration(String registration);
}
