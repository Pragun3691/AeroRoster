package com.aeroroster.persistence.repository;

import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.aeroroster.domain.DutyWindow;

public interface CrewUnavailabilityRepository extends JpaRepository<CrewUnavailability, Long> {
    Optional<CrewUnavailability> findByIdAndCrewId(Long id, Long crewId);

    @Query("""
            select new com.aeroroster.domain.DutyWindow(u.startTime, u.endTime)
            from CrewUnavailability u
            where u.crew.id = :crewId and u.startTime < :end and u.endTime > :start
              and (:excludedId is null or u.id <> :excludedId)
            """)
    List<DutyWindow> findWindows(@Param("crewId") Long crewId, @Param("start") Instant start,
            @Param("end") Instant end, @Param("excludedId") Long excludedId);
    List<CrewUnavailability> findByCrewIdOrderByStartTimeAsc(Long crewId);

    @Query("select u from CrewUnavailability u where u.crew.id = :crewId and u.startTime < :end and u.endTime > :start order by u.startTime, u.id")
    List<CrewUnavailability> findOverlapping(@Param("crewId") Long crewId, @Param("start") Instant start, @Param("end") Instant end);
}
