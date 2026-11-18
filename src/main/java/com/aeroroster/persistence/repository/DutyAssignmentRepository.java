package com.aeroroster.persistence.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DutyAssignmentRepository extends JpaRepository<DutyAssignment, Long> {
    List<DutyAssignment> findByCrewIdAndStatus(Long crewId, AssignmentStatus status);

    List<DutyAssignment> findByFlightIdAndStatus(Long flightId, AssignmentStatus status);

    List<DutyAssignment> findByCrewIdAndFlightIdAndStatus(Long crewId, Long flightId, AssignmentStatus status);

    boolean existsByCrewId(Long crewId);

    boolean existsByFlightIdAndStatus(Long flightId, AssignmentStatus status);

    boolean existsByCrewIdAndStatusAndDutyEndTimeAfter(Long crewId, AssignmentStatus status, Instant now);

    List<DutyAssignment> findByCrewIdOrderByDutyStartTimeAscIdAsc(Long crewId);

    @Query("""
            select a from DutyAssignment a
            where a.crew.id = :crewId and a.flight.aircraft.aircraftType.id = :typeId
              and a.assignedRole = :role
            order by a.dutyStartTime, a.id
            """)
    List<DutyAssignment> findQualificationHistory(@Param("crewId") Long crewId,
            @Param("typeId") Long typeId, @Param("role") CrewRole role);
}
