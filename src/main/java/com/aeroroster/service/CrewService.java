package com.aeroroster.service;

import com.aeroroster.persistence.entity.AssignmentStatus;
import com.aeroroster.persistence.entity.Crew;
import com.aeroroster.persistence.repository.CrewQualificationRepository;
import com.aeroroster.persistence.repository.CrewRepository;
import com.aeroroster.persistence.repository.DutyAssignmentRepository;
import com.aeroroster.service.dto.AssignmentView;
import com.aeroroster.service.dto.CrewFilter;
import com.aeroroster.service.dto.CrewRequest;
import com.aeroroster.service.dto.CrewView;
import com.aeroroster.service.exception.BusinessConflictException;
import com.aeroroster.service.exception.DuplicateResourceException;
import com.aeroroster.service.exception.ResourceNotFoundException;
import com.aeroroster.service.validation.InputValues;
import com.aeroroster.service.validation.ServiceValidation;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class CrewService {
    private final CrewRepository crew;
    private final CrewQualificationRepository qualifications;
    private final DutyAssignmentRepository assignments;
    private final ServiceValidation validation;
    private final ServiceTransactions transactions;
    private final ServiceLocks locks;
    private final Clock clock;

    public CrewService(CrewRepository crew, CrewQualificationRepository qualifications,
            DutyAssignmentRepository assignments, ServiceValidation validation,
            ServiceTransactions transactions, ServiceLocks locks, Clock clock) {
        this.crew = crew;
        this.qualifications = qualifications;
        this.assignments = assignments;
        this.validation = validation;
        this.transactions = transactions;
        this.locks = locks;
        this.clock = clock;
    }

    public CrewView create(CrewRequest request) {
        validation.request(request);
        return transactions.write(() -> {
            checkLicense(request.licenseNo(), -1L);
            Crew member = new Crew();
            apply(member, request);
            return ServiceViews.crew(crew.saveAndFlush(member));
        });
    }

    public CrewView get(Long id) {
        validation.id(id);
        return transactions.read(() -> ServiceViews.crew(crew.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Crew member was not found"))));
    }

    public Page<CrewView> search(CrewFilter filter, Pageable pageable) {
        validation.request(filter);
        validation.pageable(pageable);
        return transactions.read(() -> crew.search(InputValues.searchPattern(filter.query()),
                filter.role(), filter.active(), pageable).map(ServiceViews::crew));
    }

    public CrewView update(Long id, CrewRequest request) {
        return update(id, request, null);
    }

    public CrewView update(Long id, CrewRequest request, ExpectedVersion expected) {
        validation.id(id);
        validation.request(request);
        return transactions.write(() -> {
            Crew member = locks.crew(id);
            ExpectedVersion.check(expected, member.getId(), member.getVersion());
            if (member.getRole() != request.role()
                    && (!qualifications.findByCrewId(id).isEmpty() || assignments.existsByCrewId(id))) {
                throw new BusinessConflictException("Crew role cannot change while qualifications or assignment history exist");
            }
            checkLicense(request.licenseNo(), id);
            apply(member, request);
            crew.flush();
            return ServiceViews.crew(member);
        });
    }

    public CrewView setActive(Long id, boolean active) {
        return setActive(id, active, null);
    }

    public CrewView setActive(Long id, boolean active, ExpectedVersion expected) {
        validation.id(id);
        return transactions.write(() -> {
            Crew member = locks.crew(id);
            ExpectedVersion.check(expected, member.getId(), member.getVersion());
            if (!active && assignments.existsByCrewIdAndStatusAndDutyEndTimeAfter(
                    id, AssignmentStatus.ACTIVE, clock.instant())) {
                throw new BusinessConflictException("Crew member has ongoing or future active duties. Resolve them before deactivation");
            }
            member.setActive(active);
            crew.flush();
            return ServiceViews.crew(member);
        });
    }

    public List<AssignmentView> assignments(Long crewId) {
        validation.id(crewId);
        return transactions.read(() -> {
            if (!crew.existsById(crewId)) {
                throw new ResourceNotFoundException("Crew member was not found");
            }
            return assignments.findByCrewIdOrderByDutyStartTimeAscIdAsc(crewId)
                    .stream().map(ServiceViews::assignment).toList();
        });
    }

    private void checkLicense(String license, Long excludedId) {
        if (crew.existsByLicenseNoIgnoreCaseAndIdNot(license, excludedId)) {
            throw new DuplicateResourceException("Crew license number already exists");
        }
    }

    private void apply(Crew member, CrewRequest request) {
        member.setName(request.name());
        member.setRole(request.role());
        member.setLicenseNo(request.licenseNo());
        member.setExperienceYears(request.experienceYears());
        member.setContactNo(request.contactNo());
    }
}
