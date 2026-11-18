package com.aeroroster.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.aeroroster.persistence.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CrewRepository extends JpaRepository<Crew, Long> {
    List<Crew> findByActiveTrue();

    List<Crew> findByRole(CrewRole role);

    List<Crew> findByActiveTrueAndRole(CrewRole role);

    Optional<Crew> findByLicenseNo(String licenseNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Crew c where c.id = :id")
    Optional<Crew> findForUpdate(@Param("id") Long id);

    boolean existsByLicenseNoIgnoreCaseAndIdNot(String licenseNo, Long id);

    @Query("""
            select c from Crew c
            where (:role is null or c.role = :role)
              and (:active is null or c.active = :active)
              and (lower(c.name) like :pattern escape '!'
                   or lower(c.licenseNo) like :pattern escape '!')
            order by c.name, c.id
            """)
    Page<Crew> search(@Param("pattern") String pattern, @Param("role") CrewRole role,
            @Param("active") Boolean active, Pageable pageable);
}
