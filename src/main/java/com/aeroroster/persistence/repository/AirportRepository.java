package com.aeroroster.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AirportRepository extends JpaRepository<Airport, Long> {
    Optional<Airport> findByIataCode(String iataCode);

    @Query("""
            select a from Airport a
            where lower(a.iataCode) like :pattern escape '!'
               or lower(a.name) like :pattern escape '!'
               or lower(a.city) like :pattern escape '!'
               or lower(a.country) like :pattern escape '!'
            order by a.iataCode
            """)
    List<Airport> search(@Param("pattern") String pattern);
}
