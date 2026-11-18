package com.aeroroster.persistence.repository;

import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DutyAssignmentRepository extends JpaRepository<DutyAssignment, Long> {
    List<DutyAssignment> findByCrewIdAndStatus(Long crewId, AssignmentStatus status);

    List<DutyAssignment> findByFlightIdAndStatus(Long flightId, AssignmentStatus status);

    List<DutyAssignment> findByCrewIdAndFlightIdAndStatus(Long crewId, Long flightId, AssignmentStatus status);
}
