package com.aeroroster.persistence.repository;

import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface CrewUnavailabilityRepository extends JpaRepository<CrewUnavailability, Long> {
    List<CrewUnavailability> findByCrewIdOrderByStartTimeAsc(Long crewId);

    @Query("select u from CrewUnavailability u where u.crew.id = :crewId and u.startTime < :end and u.endTime > :start order by u.startTime, u.id")
    List<CrewUnavailability> findOverlapping(@Param("crewId") Long crewId, @Param("start") Instant start, @Param("end") Instant end);
}
