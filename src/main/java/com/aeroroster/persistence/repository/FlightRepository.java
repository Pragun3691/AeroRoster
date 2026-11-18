package com.aeroroster.persistence.repository;

import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface FlightRepository extends JpaRepository<Flight, Long> {
    List<Flight> findByStatus(FlightStatus status);

    @Query("select f from Flight f where f.departureTime >= :start and f.departureTime < :end order by f.departureTime, f.id")
    List<Flight> findDepartingInRange(@Param("start") Instant start, @Param("end") Instant end);

    List<Flight> findByAircraftIdOrderByDepartureTimeAsc(Long aircraftId);
}
