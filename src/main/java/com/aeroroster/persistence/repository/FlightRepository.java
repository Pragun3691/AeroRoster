package com.aeroroster.persistence.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Flight f where f.id = :id")
    Optional<Flight> findForUpdate(@Param("id") Long id);

    boolean existsByFlightNumberIgnoreCaseAndDepartureTimeAndIdNot(String flightNumber, Instant departureTime, Long id);

    boolean existsByAircraftId(Long aircraftId);

    boolean existsByAircraftIdAndStatusAndArrivalTimeAfter(Long aircraftId, FlightStatus status, Instant now);

    @Query("""
            select (count(f) > 0) from Flight f
            where f.aircraft.id = :aircraftId
              and f.status <> com.aeroroster.persistence.entity.FlightStatus.CANCELLED
              and f.departureTime < :end and f.arrivalTime > :start
              and (:excludedId is null or f.id <> :excludedId)
            """)
    boolean hasAircraftOverlap(@Param("aircraftId") Long aircraftId, @Param("start") Instant start,
            @Param("end") Instant end, @Param("excludedId") Long excludedId);

    @EntityGraph(attributePaths = {"sourceAirport", "destinationAirport", "aircraft", "aircraft.aircraftType"})
    @Query("""
            select f from Flight f
            where lower(f.flightNumber) like :pattern escape '!'
              and (:status is null or f.status = :status)
              and (:aircraftId is null or f.aircraft.id = :aircraftId)
              and (cast(:fromTime as timestamp) is null or f.departureTime >= :fromTime)
              and (cast(:untilTime as timestamp) is null or f.departureTime < :untilTime)
            order by f.departureTime, f.id
            """)
    Page<Flight> search(@Param("pattern") String pattern, @Param("status") FlightStatus status,
            @Param("aircraftId") Long aircraftId, @Param("fromTime") Instant fromTime,
            @Param("untilTime") Instant untilTime, Pageable pageable);

    @EntityGraph(attributePaths = {"sourceAirport", "destinationAirport", "aircraft", "aircraft.aircraftType"})
    Page<Flight> findByAircraftIdOrderByDepartureTimeAscIdAsc(Long aircraftId, Pageable pageable);
}
