package com.aeroroster.service;

import com.aeroroster.domain.scheduling.SchedulingReason;
import com.aeroroster.domain.scheduling.SchedulingReasonCode;
import com.aeroroster.persistence.entity.CrewRole;
import com.aeroroster.persistence.entity.FlightStatus;
import com.aeroroster.persistence.repository.*;
import com.aeroroster.service.dto.*;
import com.aeroroster.service.exception.AssignmentRejectedException;
import com.aeroroster.service.exception.BusinessConflictException;
import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static com.aeroroster.domain.scheduling.SchedulingReasonCode.*;
import static org.junit.jupiter.api.Assertions.*;

@PostgresTest
@Import(FixedClockConfiguration.class)
class AssignmentConcurrencyIntegrationTest {
    @Autowired DutyAssignmentService duties;
    @Autowired CrewUnavailabilityService unavailable;
    @Autowired CrewService crewService;
    @Autowired AircraftService aircraftService;
    @Autowired FlightService flightService;
    @Autowired QualificationService qualificationService;
    @Autowired CrewRepository crew;
    @Autowired FlightRepository flights;
    @Autowired AirportRepository airports;
    @Autowired AircraftTypeRepository types;
    @Autowired QualificationRepository qualifications;
    @Autowired CrewQualificationRepository heldQualifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ServiceTransactions transactions;

    private final List<Long> crewIds = new ArrayList<>();
    private final List<Long> aircraftIds = new ArrayList<>();
    private final List<Long> flightIds = new ArrayList<>();
    private ExecutorService executor;
    private Long qualificationId;

    private record Attempt(boolean accepted, List<SchedulingReasonCode> reasons) {
    }

    @BeforeEach
    void createCommittedFixtures() {
        executor = Executors.newFixedThreadPool(2);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long type = types.findByModel("Airbus A320").orElseThrow().getId();
        qualificationId = qualifications.findByAircraftTypeIdAndRequiredRole(type, CrewRole.PILOT).orElseThrow().getId();
        Long source = airports.findByIataCode("DEL").orElseThrow().getId();
        Long destination = airports.findByIataCode("BOM").orElseThrow().getId();
        for (String label : List.of("A", "B")) {
            CrewView member = crewService.create(new CrewRequest("Scheduling race pilot", CrewRole.PILOT,
                    "S23-" + label + "-" + suffix, 3, "9000000000"));
            crewIds.add(member.id());
            qualificationService.grant(member.id(), new QualificationGrantRequest(qualificationId, LocalDate.of(2027, 12, 31)));
            AircraftView plane = aircraftService.register(new AircraftRequest("S23-" + label + "-" + suffix, type));
            aircraftIds.add(plane.id());
            Instant departure = Instant.parse(label.equals("A") ? "2026-12-10T05:00:00Z" : "2026-12-10T06:00:00Z");
            FlightView flight = flightService.create(new FlightRequest("F23-" + label + "-" + suffix,
                    source, destination, plane.id(), departure, departure.plusSeconds(7200)));
            flightIds.add(flight.id());
        }
    }

    @AfterEach
    void removeOnlyOwnedFixtures() throws InterruptedException {
        if (executor != null) {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS), "Workers must stop before cleanup");
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Long id : crewIds) {
                jdbc.update("delete from duty_assignment where crew_id = ?", id);
                jdbc.update("delete from crew_unavailability where crew_id = ?", id);
                jdbc.update("delete from crew_qualification where crew_id = ?", id);
            }
            for (Long id : flightIds) {
                jdbc.update("delete from flight where id = ?", id);
                jdbc.update("delete from flight_audit where flight_id = ?", id);
            }
            for (Long id : aircraftIds) {
                jdbc.update("delete from aircraft where id = ?", id);
            }
            for (Long id : crewIds) {
                jdbc.update("delete from crew where id = ?", id);
            }
        });
    }

    @Test
    void simultaneousAssignmentsCannotDoubleBookCrew() throws Exception {
        List<Attempt> results = race(() -> assign(0, 0), () -> assign(0, 1),
                () -> crew.findForUpdate(crewIds.getFirst()).orElseThrow(), "crew");
        assertOneWinner(results);
        assertTrue(results.stream().filter(value -> !value.accepted()).findFirst().orElseThrow().reasons().contains(CREW_TIME_CONFLICT));
        assertEquals(1, activeCount());
        assertEquals(1, duties.activeForCrew(crewIds.getFirst()).size());
    }

    @Test
    void simultaneousRequestsCannotOverfillLastPilotSlot() throws Exception {
        List<Attempt> results = race(() -> assign(0, 0), () -> assign(1, 0),
                () -> flights.findForUpdate(flightIds.getFirst()).orElseThrow(), "flight");
        assertOneWinner(results);
        assertTrue(results.stream().filter(value -> !value.accepted()).findFirst().orElseThrow().reasons().contains(FLIGHT_ROLE_FILLED));
        assertEquals(1, activeCount());
        assertEquals(1, duties.forFlight(flightIds.getFirst()).size());
    }

    @Test
    void assignmentAndUnavailabilityCannotBothCommit() throws Exception {
        List<Attempt> results = race(() -> assign(0, 0), () -> administer(() -> unavailable.add(crewIds.getFirst(),
                new UnavailabilityRequest(Instant.parse("2026-12-10T04:00:00Z"), Instant.parse("2026-12-10T08:00:00Z"), "Training")), "existing active duty"),
                () -> crew.findForUpdate(crewIds.getFirst()).orElseThrow(), "crew");
        assertOneWinner(results);
        assertEquals(1, activeCount() + unavailable.list(crewIds.getFirst()).size());
        if (!results.getFirst().accepted()) {
            assertTrue(results.getFirst().reasons().contains(CREW_UNAVAILABLE));
        }
    }

    @Test
    void assignmentAndCrewDeactivationRemainConsistent() throws Exception {
        List<Attempt> results = race(() -> assign(0, 0), () -> administer(() -> crewService.setActive(crewIds.getFirst(), false), "ongoing or future active duties"),
                () -> crew.findForUpdate(crewIds.getFirst()).orElseThrow(), "crew");
        assertOneWinner(results);
        assertEquals(results.getFirst().accepted(), crewService.get(crewIds.getFirst()).active());
        assertEquals(results.getFirst().accepted() ? 1 : 0, activeCount());
        if (!results.getFirst().accepted()) {
            assertTrue(results.getFirst().reasons().contains(CREW_INACTIVE));
        }
    }

    @Test
    void assignmentAndQualificationExpiryChangeRemainConsistent() throws Exception {
        List<Attempt> results = race(() -> assign(0, 0), () -> administer(() -> qualificationService.updateExpiry(
                crewIds.getFirst(), qualificationId, new QualificationExpiryRequest(LocalDate.of(2026, 12, 9))), "Expiry cannot be shortened"),
                () -> crew.findForUpdate(crewIds.getFirst()).orElseThrow(), "crew");
        assertOneWinner(results);
        if (results.getFirst().accepted()) {
            assertEquals(LocalDate.of(2027, 12, 31), qualificationService.forCrew(crewIds.getFirst()).getFirst().validUntil());
        } else {
            assertTrue(results.getFirst().reasons().contains(QUALIFICATION_EXPIRED));
            assertEquals(0, activeCount());
        }
    }

    @Test
    void assignmentAndQualificationRevocationRemainConsistent() throws Exception {
        List<Attempt> results = race(() -> assign(0, 0), () -> administer(() -> qualificationService.revoke(
                crewIds.getFirst(), qualificationId), "assignment history"),
                () -> crew.findForUpdate(crewIds.getFirst()).orElseThrow(), "crew");
        assertOneWinner(results);
        assertEquals(results.getFirst().accepted() ? 1 : 0, qualificationService.forCrew(crewIds.getFirst()).size());
        assertEquals(results.getFirst().accepted() ? 1 : 0, activeCount());
        if (!results.getFirst().accepted()) {
            assertTrue(results.getFirst().reasons().contains(MISSING_QUALIFICATION));
        }
    }

    @Test
    void assignmentAndFlightCancellationRemainConsistent() throws Exception {
        List<Attempt> results = race(() -> assign(0, 0), () -> administer(() -> flightService.cancel(flightIds.getFirst()), "active duty assignments"),
                () -> flights.findForUpdate(flightIds.getFirst()).orElseThrow(), "flight");
        assertOneWinner(results);
        if (results.getFirst().accepted()) {
            assertEquals(FlightStatus.SCHEDULED, flightService.get(flightIds.getFirst()).status());
            assertEquals(1, activeCount());
        } else {
            assertTrue(results.getFirst().reasons().contains(FLIGHT_NOT_SCHEDULED));
            assertEquals(FlightStatus.CANCELLED, flightService.get(flightIds.getFirst()).status());
            assertEquals(0, activeCount());
        }
    }

    @Test
    void eligibilityReadsFreshQualificationDespiteOuterPersistenceCache() throws Exception {
        AssignmentRejectedException exception = assertThrows(AssignmentRejectedException.class,
                () -> new TransactionTemplate(transactionManager).execute(status -> {
                    var cached = heldQualifications.findByCrewId(crewIds.getFirst()).getFirst();
                    Future<?> update = executor.submit(() -> qualificationService.updateExpiry(crewIds.getFirst(), qualificationId,
                            new QualificationExpiryRequest(LocalDate.of(2026, 12, 9))));
                    try {
                        update.get(10, TimeUnit.SECONDS);
                    } catch (Exception failure) {
                        throw new AssertionError("Qualification update must commit", failure);
                    }
                    assertEquals(LocalDate.of(2027, 12, 31), cached.getValidUntil());
                    return duties.assign(request(0, 0));
                }));
        assertTrue(exception.getDecision().reasons().stream().map(SchedulingReason::code).toList().contains(QUALIFICATION_EXPIRED));
        assertEquals(0, activeCount());
    }

    @Test
    void outerTransactionFailureRollsBackFlushedAssignment() {
        assertThrows(BusinessConflictException.class, () -> transactions.write(() -> {
            duties.assign(request(0, 0));
            assertEquals(1, activeCount());
            throw new BusinessConflictException("Abort the complete operation after the assignment flush");
        }));
        assertEquals(0, activeCount());
        assertTrue(duties.historyForFlight(flightIds.getFirst()).isEmpty());
    }

    @Test
    void writesRejectAnOuterRepeatableReadSnapshot() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        BusinessConflictException exception = assertThrows(BusinessConflictException.class,
                () -> outer.execute(status -> duties.assign(request(0, 0))));
        assertEquals("Write operations require READ_COMMITTED transaction isolation", exception.getMessage());
        assertEquals(0, activeCount());
    }

    @Test
    void writesRejectAnOuterReadOnlyTransaction() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        outer.setReadOnly(true);
        BusinessConflictException exception = assertThrows(BusinessConflictException.class,
                () -> outer.execute(status -> duties.assign(request(0, 0))));
        assertEquals("Write operations cannot join a read-only transaction", exception.getMessage());
        assertEquals(0, activeCount());
    }

    @Test
    void expiryMutationRefreshesCachedQualificationAndHistoryDeparture() {
        qualificationService.updateExpiry(crewIds.getFirst(), qualificationId,
                new QualificationExpiryRequest(LocalDate.of(2026, 12, 8)));
        BusinessConflictException exception = assertThrows(BusinessConflictException.class,
                () -> new TransactionTemplate(transactionManager).execute(status -> {
                    var cachedQualification = heldQualifications.findByCrewId(crewIds.getFirst()).getFirst();
                    var cachedFlight = flights.findById(flightIds.getFirst()).orElseThrow();
                    Future<?> changes = executor.submit(() -> {
                        qualificationService.updateExpiry(crewIds.getFirst(), qualificationId,
                                new QualificationExpiryRequest(LocalDate.of(2027, 12, 31)));
                        FlightView current = flightService.get(flightIds.getFirst());
                        flightService.update(current.id(), new FlightRequest(current.flightNumber(), current.sourceAirportId(),
                                current.destinationAirportId(), current.aircraftId(), Instant.parse("2026-12-20T05:00:00Z"),
                                Instant.parse("2026-12-20T07:00:00Z")));
                        duties.assign(request(0, 0));
                    });
                    try {
                        changes.get(10, TimeUnit.SECONDS);
                    } catch (Exception failure) {
                        throw new AssertionError("Concurrent renewal, reschedule and assignment must commit", failure);
                    }
                    assertEquals(LocalDate.of(2026, 12, 8), cachedQualification.getValidUntil());
                    assertEquals(Instant.parse("2026-12-10T05:00:00Z"), cachedFlight.getDepartureTime());
                    return qualificationService.updateExpiry(crewIds.getFirst(), qualificationId,
                            new QualificationExpiryRequest(LocalDate.of(2026, 12, 15)));
                }));
        assertTrue(exception.getMessage().contains("Expiry cannot be shortened"));
        assertEquals(LocalDate.of(2027, 12, 31), qualificationService.forCrew(crewIds.getFirst()).getFirst().validUntil());
        assertEquals(1, activeCount());
    }

    private AssignmentRequest request(int crewIndex, int flightIndex) {
        return new AssignmentRequest(crewIds.get(crewIndex), flightIds.get(flightIndex), CrewRole.PILOT);
    }

    private Attempt assign(int crewIndex, int flightIndex) {
        try {
            duties.assign(request(crewIndex, flightIndex));
            return new Attempt(true, List.of());
        } catch (AssignmentRejectedException exception) {
            return new Attempt(false, exception.getDecision().reasons().stream().map(SchedulingReason::code).toList());
        }
    }

    private Attempt administer(Runnable operation, String conflictMessage) {
        try {
            operation.run();
            return new Attempt(true, List.of());
        } catch (BusinessConflictException exception) {
            assertTrue(exception.getMessage().contains(conflictMessage), exception.getMessage());
            return new Attempt(false, List.of());
        }
    }

    private List<Attempt> race(Supplier<Attempt> first, Supplier<Attempt> second, Runnable holdLock, String table) throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        List<Future<Attempt>> pending = new TransactionTemplate(transactionManager).execute(status -> {
            holdLock.run();
            Future<Attempt> one = executor.submit(() -> {
                started.countDown();
                return first.get();
            });
            Future<Attempt> two = executor.submit(() -> {
                started.countDown();
                return second.get();
            });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while starting competing requests", exception);
            }
            awaitWaiters(table);
            assertFalse(one.isDone());
            assertFalse(two.isDone());
            return List.of(one, two);
        });
        return List.of(pending.getFirst().get(15, TimeUnit.SECONDS), pending.get(1).get(15, TimeUnit.SECONDS));
    }

    private void awaitWaiters(String table) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("select pg_stat_clear_snapshot()");
            Integer count = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock' and query like ?
                    """, Integer.class, "%from " + table + " %");
            if (count != null && count >= 2) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while observing row-lock waits", exception);
            }
        }
        fail("Both transactions must be observed waiting for the " + table + " row lock");
    }

    private void assertOneWinner(List<Attempt> results) {
        assertEquals(1, results.stream().filter(Attempt::accepted).count());
    }

    private int activeCount() {
        int count = 0;
        for (Long id : crewIds) {
            count += jdbc.queryForObject("select count(*) from duty_assignment where crew_id = ? and status = 'ACTIVE'", Integer.class, id);
        }
        return count;
    }
}
