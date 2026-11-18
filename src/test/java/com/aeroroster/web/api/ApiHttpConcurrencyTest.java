package com.aeroroster.web.api;

import com.aeroroster.persistence.repository.*;
import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@PostgresTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FixedClockConfiguration.class)
class ApiHttpConcurrencyTest {
    @Value("${local.server.port}") int port;
    @Autowired JsonMapper json;
    @Autowired CrewRepository crew;
    @Autowired FlightRepository flights;
    @Autowired AirportRepository airports;
    @Autowired AircraftTypeRepository types;
    @Autowired QualificationRepository qualifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private final List<Long> crewIds = new ArrayList<>();
    private final List<Long> aircraftIds = new ArrayList<>();
    private final List<Long> flightIds = new ArrayList<>();
    private HttpClient client;
    private ExecutorService workers;

    @BeforeEach
    void createOwnedFixturesOverHttp() throws Exception {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        workers = Executors.newFixedThreadPool(2);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long type = types.findByModel("Airbus A320").orElseThrow().getId();
        Long qualification = qualifications.findByAircraftTypeIdAndRequiredRole(type,
                com.aeroroster.persistence.entity.CrewRole.PILOT).orElseThrow().getId();
        Long source = airports.findByIataCode("DEL").orElseThrow().getId();
        Long destination = airports.findByIataCode("BOM").orElseThrow().getId();
        for (String label : List.of("A", "B")) {
            String naturalId = "H24-" + label + "-" + suffix;
            long crewId = created("/api/v1/crew", Map.of("name", "HTTP race pilot", "role", "PILOT",
                    "licenseNo", naturalId, "experienceYears", 3, "contactNo", "9000000000"));
            crewIds.add(crewId);
            created("/api/v1/crew/" + crewId + "/qualifications", Map.of("qualificationId", qualification, "validUntil", "2027-12-31"));
            long planeId = created("/api/v1/aircraft", Map.of("registration", naturalId, "aircraftTypeId", type));
            aircraftIds.add(planeId);
            long flightId = created("/api/v1/flights", Map.of("flightNumber", naturalId, "aircraftId", planeId,
                    "sourceAirportId", source, "destinationAirportId", destination,
                    "departureTime", label.equals("A") ? "2026-12-20T05:00:00Z" : "2026-12-20T06:00:00Z",
                    "arrivalTime", label.equals("A") ? "2026-12-20T07:00:00Z" : "2026-12-20T08:00:00Z"));
            flightIds.add(flightId);
        }
    }

    @AfterEach
    void cleanupOwnedCommittedData() throws Exception {
        if (workers != null) {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(25, TimeUnit.SECONDS));
        }
        if (client != null) client.close();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            for (Long id : crewIds) {
                jdbc.update("delete from duty_assignment where crew_id=?", id);
                jdbc.update("delete from crew_unavailability where crew_id=?", id);
                jdbc.update("delete from crew_qualification where crew_id=?", id);
            }
            for (Long id : flightIds) {
                jdbc.update("delete from flight where id=?", id);
                jdbc.update("delete from flight_audit where flight_id=?", id);
            }
            for (Long id : aircraftIds) jdbc.update("delete from aircraft where id=?", id);
            for (Long id : crewIds) jdbc.update("delete from crew where id=?", id);
        });
    }

    @Test
    void concurrentHttpRequestsCannotDoubleBookCrew() throws Exception {
        List<HttpResponse<String>> responses = race(0, 1, 0, 0,
                () -> crew.findForUpdate(crewIds.getFirst()).orElseThrow(), "crew");
        verifyWinner(responses, "CREW_TIME_CONFLICT");
        assertEquals(1, jdbc.queryForObject("select count(*) from duty_assignment where crew_id=? and status='ACTIVE'", Integer.class, crewIds.getFirst()));
    }

    @Test
    void concurrentHttpRequestsCannotOverfillPilotSlot() throws Exception {
        List<HttpResponse<String>> responses = race(0, 0, 0, 1,
                () -> flights.findForUpdate(flightIds.getFirst()).orElseThrow(), "flight");
        verifyWinner(responses, "FLIGHT_ROLE_FILLED");
        assertEquals(1, jdbc.queryForObject("select count(*) from duty_assignment where flight_id=? and assigned_role='PILOT' and status='ACTIVE'", Integer.class, flightIds.getFirst()));
    }

    private List<HttpResponse<String>> race(int firstFlight, int secondFlight, int firstCrew, int secondCrew,
            Runnable lock, String table) throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        List<Future<HttpResponse<String>>> pending = new TransactionTemplate(manager).execute(status -> {
            lock.run();
            Future<HttpResponse<String>> first = workers.submit(() -> {
                started.countDown();
                return assign(firstCrew, firstFlight);
            });
            Future<HttpResponse<String>> second = workers.submit(() -> {
                started.countDown();
                return assign(secondCrew, secondFlight);
            });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            awaitWaiters(table);
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            return List.of(first, second);
        });
        return List.of(pending.getFirst().get(20, TimeUnit.SECONDS), pending.get(1).get(20, TimeUnit.SECONDS));
    }

    private void awaitWaiters(String table) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            jdbc.execute("select pg_stat_clear_snapshot()");
            Integer waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like ?",
                    Integer.class, "%from " + table + " %");
            if (waiting != null && waiting >= 2) return;
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
        fail("Both independent HTTP/database transactions must wait on the held parent lock");
    }

    private void verifyWinner(List<HttpResponse<String>> responses, String reason) throws Exception {
        assertEquals(1, responses.stream().filter(response -> response.statusCode() == 201).count());
        HttpResponse<String> rejected = responses.stream().filter(response -> response.statusCode() != 201).findFirst().orElseThrow();
        assertEquals(409, rejected.statusCode(), rejected.body());
        JsonNode error = json.readTree(rejected.body());
        assertEquals("ASSIGNMENT_REJECTED", error.get("code").asString());
        assertTrue(error.get("eligibility").get("reasons").toString().contains(reason));
        for (HttpResponse<String> response : responses) {
            if (response.statusCode() == 201) {
                assertTrue(response.headers().firstValue("Location").orElseThrow().startsWith("/api/v1/assignments/"));
            }
        }
    }

    private HttpResponse<String> assign(int crew, int flight) throws Exception {
        return post("/api/v1/assignments", Map.of("crewId", crewIds.get(crew), "flightId", flightIds.get(flight), "assignedRole", "PILOT"));
    }

    private long created(String path, Object body) throws Exception {
        HttpResponse<String> response = post(path, body);
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body()).get("id").asLong();
    }

    private HttpResponse<String> post(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
