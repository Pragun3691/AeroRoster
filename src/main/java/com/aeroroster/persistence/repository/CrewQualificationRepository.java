package com.aeroroster.persistence.repository;

import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import com.aeroroster.domain.scheduling.QualificationValidity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrewQualificationRepository extends JpaRepository<CrewQualification, Long> {
    @Query("""
            select new com.aeroroster.domain.scheduling.QualificationValidity(cq.validUntil)
            from CrewQualification cq
            where cq.crew.id = :crewId and cq.qualification.aircraftType.id = :typeId
              and cq.qualification.requiredRole = :role
            """)
    List<QualificationValidity> findValidity(@Param("crewId") Long crewId, @Param("typeId") Long typeId,
            @Param("role") CrewRole role);
    List<CrewQualification> findByCrewId(Long crewId);

    Optional<CrewQualification> findByCrewIdAndQualificationId(Long crewId, Long qualificationId);
}
