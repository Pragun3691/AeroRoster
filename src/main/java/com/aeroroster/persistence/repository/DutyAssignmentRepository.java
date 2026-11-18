package com.aeroroster.persistence.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import com.aeroroster.domain.DutyWindow;
import com.aeroroster.domain.scheduling.AssignmentIdentity;

public interface DutyAssignmentRepository extends JpaRepository<DutyAssignment, Long> {
    boolean existsByCrewIdAndFlightIdAndStatus(Long crewId, Long flightId, AssignmentStatus status);

    long countByFlightIdAndAssignedRoleAndStatus(Long flightId, CrewRole role, AssignmentStatus status);

    List<DutyAssignment> findByFlightIdOrderByDutyStartTimeAscIdAsc(Long flightId);

    @Query("select new com.aeroroster.domain.scheduling.AssignmentIdentity(a.crew.id, a.flight.id) from DutyAssignment a where a.id = :id")
    Optional<AssignmentIdentity> findIdentity(@Param("id") Long id);

    @Query("""
            select new com.aeroroster.domain.DutyWindow(a.dutyStartTime, a.dutyEndTime)
            from DutyAssignment a
            where a.crew.id = :crewId
              and a.status = com.aeroroster.persistence.entity.AssignmentStatus.ACTIVE
              and a.dutyStartTime < :end and a.dutyEndTime > :start
            order by a.dutyStartTime, a.id
            """)
    List<DutyWindow> findActiveWindows(@Param("crewId") Long crewId, @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            select a.assignedRole from DutyAssignment a where a.flight.id = :flightId
              and a.status = com.aeroroster.persistence.entity.AssignmentStatus.ACTIVE
            """)
    List<CrewRole> findActiveRoles(@Param("flightId") Long flightId);

    @Query("""
            select a.flight.departureTime from DutyAssignment a
            where a.crew.id = :crewId and a.flight.aircraft.aircraftType.id = :typeId
              and a.assignedRole = :role
            """)
    List<Instant> findQualificationDepartureTimes(@Param("crewId") Long crewId,
            @Param("typeId") Long typeId, @Param("role") CrewRole role);
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

    @EntityGraph(attributePaths = "flight")
    Page<DutyAssignment> findByCrewIdOrderByDutyStartTimeAscIdAsc(Long crewId, Pageable pageable);

    @EntityGraph(attributePaths = "flight")
    Page<DutyAssignment> findByFlightIdOrderByDutyStartTimeAscIdAsc(Long flightId, Pageable pageable);

    @EntityGraph(attributePaths = "flight")
    Page<DutyAssignment> findByCrewIdAndStatusOrderByDutyStartTimeAscIdAsc(Long crewId, AssignmentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "flight")
    Page<DutyAssignment> findByFlightIdAndStatusOrderByDutyStartTimeAscIdAsc(Long flightId, AssignmentStatus status, Pageable pageable);
}
