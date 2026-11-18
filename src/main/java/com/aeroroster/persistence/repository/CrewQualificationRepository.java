package com.aeroroster.persistence.repository;

import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CrewQualificationRepository extends JpaRepository<CrewQualification, Long> {
    List<CrewQualification> findByCrewId(Long crewId);

    Optional<CrewQualification> findByCrewIdAndQualificationId(Long crewId, Long qualificationId);
}
