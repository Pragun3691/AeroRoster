package com.aeroroster.service;

import com.aeroroster.persistence.repository.AircraftRepository;
import com.aeroroster.persistence.repository.AircraftTypeRepository;
import com.aeroroster.persistence.repository.AirportRepository;
import com.aeroroster.persistence.entity.Aircraft;
import com.aeroroster.service.dto.AircraftRequest;
import com.aeroroster.service.dto.AircraftView;
import com.aeroroster.service.dto.FlightRequest;
import com.aeroroster.service.exception.BusinessConflictException;
import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@PostgresTest
@Import(FixedClockConfiguration.class)
class FlightConcurrencyIntegrationTest {
    @Autowired FlightService flights;
    @Autowired AircraftService aircraftService;
    @Autowired AircraftRepository aircraft;
    @Autowired AircraftTypeRepository types;
    @Autowired AirportRepository airports;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentAircraftSchedulingBlocksThenCommitsExactlyOneFlight() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long type = types.findByModel("Airbus A320").orElseThrow().getId();
        AircraftView plane = aircraftService.register(new AircraftRequest("T22-" + suffix, type));
        Long source = airports.findByIataCode("DEL").orElseThrow().getId();
        Long destination = airports.findByIataCode("BOM").orElseThrow().getId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            List<Future<Boolean>> results = transaction.execute(status -> {
                aircraft.findForUpdate(plane.id()).orElseThrow();
                Future<Boolean> first = executor.submit(() -> schedule(
                        new FlightRequest("A22-" + suffix, source, destination, plane.id(),
                                Instant.parse("2026-12-20T05:00:00Z"), Instant.parse("2026-12-20T07:00:00Z")), started));
                Future<Boolean> second = executor.submit(() -> schedule(
                        new FlightRequest("B22-" + suffix, source, destination, plane.id(),
                                Instant.parse("2026-12-20T06:00:00Z"), Instant.parse("2026-12-20T08:00:00Z")), started));
                awaitBothRequests(started);
                awaitDatabaseWaiters();
                assertFalse(first.isDone(), "First scheduling transaction must wait for the aircraft lock");
                assertFalse(second.isDone(), "Second scheduling transaction must wait for the aircraft lock");
                return List.of(first, second);
            });
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(15, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, jdbc.queryForObject("select count(*) from flight where aircraft_id = ?", Integer.class, plane.id()));
            assertEquals(1, jdbc.queryForObject("select count(*) from flight_audit a join flight f on f.id = a.flight_id where f.aircraft_id = ?", Integer.class, plane.id()));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS), "Scheduling workers must stop before fixture cleanup");
            // Delete only this test's committed rows. Never touch the shared seeded records.
            cleanup(plane);
        }
    }

    @Test
    void schedulingRefreshesAircraftCachedBeforeConcurrentDeactivation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long type = types.findByModel("Airbus A320").orElseThrow().getId();
        AircraftView plane = aircraftService.register(new AircraftRequest("T22-" + suffix, type));
        Long source = airports.findByIataCode("DEL").orElseThrow().getId();
        Long destination = airports.findByIataCode("BOM").orElseThrow().getId();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            BusinessConflictException exception = assertThrows(BusinessConflictException.class, () -> transaction.execute(status -> {
                Aircraft cached = aircraft.findById(plane.id()).orElseThrow();
                assertTrue(cached.isActive());
                Future<?> deactivation = executor.submit(() -> aircraftService.setActive(plane.id(), false));
                try {
                    deactivation.get(10, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new AssertionError("Concurrent aircraft deactivation must commit", failure);
                }
                assertTrue(cached.isActive(), "The outer persistence context still has the original value");
                return flights.create(new FlightRequest("C22-" + suffix, source, destination, plane.id(),
                        Instant.parse("2026-12-20T05:00:00Z"), Instant.parse("2026-12-20T07:00:00Z")));
            }));
            assertEquals("Aircraft must be active for flight creation or rescheduling", exception.getMessage());
            assertEquals(0, jdbc.queryForObject("select count(*) from flight where aircraft_id = ?", Integer.class, plane.id()));
            assertFalse(aircraftService.get(plane.id()).active());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
            cleanup(plane);
        }
    }

    private void cleanup(AircraftView plane) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<Long> flightIds = jdbc.queryForList("select id from flight where aircraft_id = ?", Long.class, plane.id());
            jdbc.update("delete from flight where aircraft_id = ?", plane.id());
            for (Long id : flightIds) {
                jdbc.update("delete from flight_audit where flight_id = ?", id);
            }
            jdbc.update("delete from aircraft where id = ? and registration = ?", plane.id(), plane.registration());
        });
    }

    private boolean schedule(FlightRequest request, CountDownLatch started) {
        started.countDown();
        try {
            flights.create(request);
            return true;
        } catch (BusinessConflictException exception) {
            assertEquals("Aircraft is already scheduled during this interval", exception.getMessage());
            return false;
        }
    }

    private void awaitBothRequests(CountDownLatch started) {
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS), "Both scheduling requests must start");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while starting scheduling requests", exception);
        }
    }

    private void awaitDatabaseWaiters() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("select pg_stat_clear_snapshot()");
            Integer waiting = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock'
                      and query like '%from aircraft%'
                    """, Integer.class);
            if (waiting != null && waiting >= 2) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while observing database locks", exception);
            }
        }
        fail("Both scheduling transactions must be observed waiting on PostgreSQL aircraft row locks");
    }
}
