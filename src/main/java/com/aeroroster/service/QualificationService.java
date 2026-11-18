package com.aeroroster.service;

import com.aeroroster.persistence.entity.Crew;
import com.aeroroster.persistence.entity.CrewQualification;
import com.aeroroster.persistence.entity.Qualification;
import com.aeroroster.persistence.repository.CrewQualificationRepository;
import com.aeroroster.persistence.repository.CrewRepository;
import com.aeroroster.persistence.repository.DutyAssignmentRepository;
import com.aeroroster.persistence.repository.QualificationRepository;
import com.aeroroster.persistence.repository.AircraftTypeRepository;
import com.aeroroster.service.dto.CrewQualificationView;
import com.aeroroster.service.dto.QualificationExpiryRequest;
import com.aeroroster.service.dto.QualificationGrantRequest;
import com.aeroroster.service.dto.QualificationView;
import com.aeroroster.service.exception.BusinessConflictException;
import com.aeroroster.service.exception.DuplicateResourceException;
import com.aeroroster.service.exception.ResourceNotFoundException;
import com.aeroroster.service.validation.ServiceValidation;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class QualificationService {
    private final QualificationRepository requirements;
    private final CrewQualificationRepository heldQualifications;
    private final CrewRepository crew;
    private final DutyAssignmentRepository assignments;
    private final ServiceValidation validation;
    private final ServiceTransactions transactions;
    private final ServiceLocks locks;
    private final AircraftTypeRepository aircraftTypes;

    public QualificationService(QualificationRepository requirements, CrewQualificationRepository heldQualifications,
            CrewRepository crew, DutyAssignmentRepository assignments, ServiceValidation validation,
            ServiceTransactions transactions, ServiceLocks locks, AircraftTypeRepository aircraftTypes) {
        this.requirements = requirements;
        this.heldQualifications = heldQualifications;
        this.crew = crew;
        this.assignments = assignments;
        this.validation = validation;
        this.transactions = transactions;
        this.locks = locks;
        this.aircraftTypes = aircraftTypes;
    }

    public List<QualificationView> requirements() {
        return transactions.read(() -> requirements.findAll(Sort.by("id"))
                .stream().map(ServiceViews::qualification).toList());
    }

    public List<CrewQualificationView> forCrew(Long crewId) {
        validation.id(crewId);
        return transactions.read(() -> {
            if (!crew.existsById(crewId)) {
                throw new ResourceNotFoundException("Crew member was not found");
            }
            return heldQualifications.findByCrewId(crewId).stream().map(ServiceViews::crewQualification).toList();
        });
    }

    public CrewQualificationView grant(Long crewId, QualificationGrantRequest request) {
        validation.id(crewId);
        validation.request(request);
        return transactions.write(() -> {
            Crew member = locks.crew(crewId);
            Qualification qualification = requirements.findById(request.qualificationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Qualification requirement was not found"));
            checkRole(member, qualification);
            if (heldQualifications.findByCrewIdAndQualificationId(crewId, qualification.getId()).isPresent()) {
                throw new DuplicateResourceException("Crew member already holds this qualification");
            }
            CrewQualification held = new CrewQualification();
            held.setCrew(member);
            held.setQualification(qualification);
            held.setValidUntil(request.validUntil());
            return ServiceViews.crewQualification(heldQualifications.saveAndFlush(held));
        });
    }

    public CrewQualificationView updateExpiry(Long crewId, Long qualificationId, QualificationExpiryRequest request) {
        return updateExpiry(crewId, qualificationId, request, null);
    }

    public CrewQualificationView updateExpiry(Long crewId, Long qualificationId, QualificationExpiryRequest request, ExpectedVersion expected) {
        validation.id(crewId);
        validation.id(qualificationId);
        validation.request(request);
        return transactions.write(() -> {
            Crew member = locks.crew(crewId);
            CrewQualification held = locks.qualification(crewId, qualificationId);
            ExpectedVersion.check(expected, held.getId(), held.getVersion());
            checkRole(member, held.getQualification());
            LocalDate expiry = request.validUntil();
            if (expiry != null && (held.getValidUntil() == null || expiry.isBefore(held.getValidUntil()))) {
                boolean invalidatesHistory = history(held).stream().anyMatch(departure -> expiry.isBefore(
                        departure.atZone(ZoneOffset.UTC).toLocalDate()));
                if (invalidatesHistory) {
                    throw new BusinessConflictException("Expiry cannot be shortened past a referenced flight's UTC departure date");
                }
            }
            held.setValidUntil(expiry);
            heldQualifications.flush();
            return ServiceViews.crewQualification(held);
        });
    }

    public void revoke(Long crewId, Long qualificationId) {
        revoke(crewId, qualificationId, null);
    }

    public void revoke(Long crewId, Long qualificationId, ExpectedVersion expected) {
        validation.id(crewId);
        validation.id(qualificationId);
        transactions.write(() -> {
            locks.crew(crewId);
            CrewQualification held = locks.qualification(crewId, qualificationId);
            ExpectedVersion.check(expected, held.getId(), held.getVersion());
            if (!history(held).isEmpty()) {
                throw new BusinessConflictException("Qualification has assignment history and must be retained");
            }
            heldQualifications.delete(held);
            heldQualifications.flush();
            return null;
        });
    }

    private void checkRole(Crew crew, Qualification qualification) {
        if (crew.getRole() != qualification.getRequiredRole()) {
            throw new BusinessConflictException("Qualification role must match the crew member's role");
        }
    }

    private List<Instant> history(CrewQualification held) {
        Qualification qualification = held.getQualification();
        return assignments.findQualificationDepartureTimes(held.getCrew().getId(),
                qualification.getAircraftType().getId(), qualification.getRequiredRole());
    }

    public List<QualificationView> requirementsForAircraftType(Long aircraftTypeId) {
        validation.id(aircraftTypeId);
        return transactions.read(() -> {
            if (!aircraftTypes.existsById(aircraftTypeId)) {
                throw new ResourceNotFoundException("Aircraft type was not found");
            }
            return requirements.findByAircraftTypeId(aircraftTypeId).stream().map(ServiceViews::qualification).toList();
        });
    }

    public CrewQualificationView getForCrew(Long crewId, Long qualificationId) {
        validation.id(crewId);
        validation.id(qualificationId);
        return transactions.read(() -> {
            if (!crew.existsById(crewId)) {
                throw new ResourceNotFoundException("Crew member was not found");
            }
            return ServiceViews.crewQualification(heldQualifications.findByCrewIdAndQualificationId(crewId, qualificationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Crew qualification was not found")));
        });
    }
}
