package com.aeroroster.persistence.repository;

import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CrewRepository extends JpaRepository<Crew, Long> {
    List<Crew> findByActiveTrue();

    List<Crew> findByRole(CrewRole role);

    List<Crew> findByActiveTrueAndRole(CrewRole role);

    Optional<Crew> findByLicenseNo(String licenseNo);
}
