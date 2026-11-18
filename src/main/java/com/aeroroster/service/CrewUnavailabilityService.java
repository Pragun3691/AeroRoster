package com.aeroroster.service;

import com.aeroroster.persistence.entity.Crew;
import com.aeroroster.persistence.entity.CrewUnavailability;
import com.aeroroster.persistence.repository.CrewRepository;
import com.aeroroster.persistence.repository.CrewUnavailabilityRepository;
import com.aeroroster.persistence.repository.DutyAssignmentRepository;
import com.aeroroster.service.dto.UnavailabilityRequest;
import com.aeroroster.service.dto.UnavailabilityView;
import com.aeroroster.service.exception.BusinessConflictException;
import com.aeroroster.service.exception.ResourceNotFoundException;
import com.aeroroster.service.validation.ServiceValidation;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class CrewUnavailabilityService {
    private final CrewRepository crew;
    private final CrewUnavailabilityRepository unavailability;
    private final DutyAssignmentRepository assignments;
    private final ServiceLocks locks;
    private final ServiceTransactions transactions;
    private final ServiceValidation validation;

    public CrewUnavailabilityService(CrewRepository crew, CrewUnavailabilityRepository unavailability,
            DutyAssignmentRepository assignments, ServiceLocks locks, ServiceTransactions transactions,
            ServiceValidation validation) {
        this.crew = crew;
        this.unavailability = unavailability;
        this.assignments = assignments;
        this.locks = locks;
        this.transactions = transactions;
        this.validation = validation;
    }

    public UnavailabilityView add(Long crewId, UnavailabilityRequest request) {
        validation.id(crewId);
        validate(request);
        return transactions.write(() -> {
            Crew member = locks.crew(crewId);
            checkConflicts(crewId, request, null);
            CrewUnavailability period = new CrewUnavailability();
            period.setCrew(member);
            apply(period, request);
            return view(unavailability.saveAndFlush(period));
        });
    }

    public List<UnavailabilityView> list(Long crewId) {
        validation.id(crewId);
        return transactions.read(() -> {
            requireCrew(crewId);
            return unavailability.findByCrewIdOrderByStartTimeAsc(crewId).stream().map(this::view).toList();
        });
    }

    public UnavailabilityView update(Long crewId, Long id, UnavailabilityRequest request) {
        validation.id(crewId);
        validation.id(id);
        validate(request);
        return transactions.write(() -> {
            locks.crew(crewId);
            CrewUnavailability period = locks.unavailability(crewId, id);
            checkConflicts(crewId, request, id);
            apply(period, request);
            unavailability.flush();
            return view(period);
        });
    }

    public void remove(Long crewId, Long id) {
        validation.id(crewId);
        validation.id(id);
        transactions.write(() -> {
            locks.crew(crewId);
            CrewUnavailability period = locks.unavailability(crewId, id);
            unavailability.delete(period);
            unavailability.flush();
            return null;
        });
    }

    public boolean conflicts(Long crewId, Instant start, Instant end) {
        validation.id(crewId);
        validation.window(start, end);
        return transactions.read(() -> {
            requireCrew(crewId);
            return !unavailability.findWindows(crewId, start, end, null).isEmpty();
        });
    }

    private void validate(UnavailabilityRequest request) {
        validation.request(request);
        validation.window(request.startTime(), request.endTime());
    }

    private void checkConflicts(Long crewId, UnavailabilityRequest request, Long excludedId) {
        if (!unavailability.findWindows(crewId, request.startTime(), request.endTime(), excludedId).isEmpty()) {
            throw new BusinessConflictException("Crew unavailability periods must not overlap");
        }
        if (!assignments.findActiveWindows(crewId, request.startTime(), request.endTime()).isEmpty()) {
            throw new BusinessConflictException("Unavailability conflicts with an existing active duty");
        }
    }

    private void requireCrew(Long id) {
        if (!crew.existsById(id)) {
            throw new ResourceNotFoundException("Crew member was not found");
        }
    }

    private void apply(CrewUnavailability period, UnavailabilityRequest request) {
        period.setStartTime(request.startTime());
        period.setEndTime(request.endTime());
        period.setReason(request.reason());
    }

    private UnavailabilityView view(CrewUnavailability period) {
        return new UnavailabilityView(period.getId(), period.getCrew().getId(), period.getStartTime(),
                period.getEndTime(), period.getReason());
    }

    public Page<UnavailabilityView> list(Long crewId, Pageable pageable) {
        validation.id(crewId);
        validation.pageable(pageable);
        return transactions.read(() -> {
            requireCrew(crewId);
            return unavailability.findByCrewIdOrderByStartTimeAscIdAsc(crewId, pageable).map(this::view);
        });
    }

    public UnavailabilityView get(Long crewId, Long id) {
        validation.id(crewId);
        validation.id(id);
        return transactions.read(() -> {
            requireCrew(crewId);
            return view(unavailability.findByIdAndCrewId(id, crewId)
                    .orElseThrow(() -> new ResourceNotFoundException("Crew unavailability was not found")));
        });
    }
}
