package com.aeroroster.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AircraftRepository extends JpaRepository<Aircraft, Long> {
    List<Aircraft> findByActiveTrue();

    List<Aircraft> findByAircraftTypeId(Long aircraftTypeId);

    Optional<Aircraft> findByRegistration(String registration);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Aircraft a where a.id = :id")
    Optional<Aircraft> findForUpdate(@Param("id") Long id);

    boolean existsByRegistrationIgnoreCaseAndIdNot(String registration, Long id);

    @EntityGraph(attributePaths = "aircraftType")
    @Query("""
            select a from Aircraft a
            where (:active is null or a.active = :active)
              and (:typeId is null or a.aircraftType.id = :typeId)
              and (lower(a.registration) like :pattern escape '!'
                   or lower(a.aircraftType.model) like :pattern escape '!')
            order by a.registration, a.id
            """)
    Page<Aircraft> search(@Param("pattern") String pattern, @Param("active") Boolean active,
            @Param("typeId") Long typeId, Pageable pageable);
}
