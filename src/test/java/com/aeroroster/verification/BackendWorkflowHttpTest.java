package com.aeroroster.verification;

import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
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

/** Committed real HTTP workflows; only this test's recorded IDs are removed afterward. */
@PostgresTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FixedClockConfiguration.class)
class BackendWorkflowHttpTest {
    @Value("${local.server.port}") int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private HttpClient client;
    private final List<Long> crews = new ArrayList<>(), planes = new ArrayList<>(), flights = new ArrayList<>();
    private final Map<String, Long> requirements = new HashMap<>();
    private String prefix;
    private int serial;
    private long type, source, destination;
    private static final String API = "/api/v1";

    @BeforeEach
    void catalogAndFixtures() throws Exception {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        prefix = "V25" + UUID.randomUUID().toString().substring(0, 6);
        JsonNode airports = call("GET", "/airports", null, 200);
        for (JsonNode airport : airports) {
            if (airport.get("iataCode").asString().equals("DEL")) source = airport.get("id").asLong();
            if (airport.get("iataCode").asString().equals("BOM")) destination = airport.get("id").asLong();
        }
        for (JsonNode aircraftType : call("GET", "/aircraft-types", null, 200)) {
            if (aircraftType.get("model").asString().equals("Airbus A320")) type = aircraftType.get("id").asLong();
        }
        assertTrue(type > 0 && source > 0 && destination > 0);
        for (JsonNode requirement : call("GET", "/aircraft-types/" + type + "/qualifications", null, 200)) {
            requirements.put(requirement.get("requiredRole").asString(), requirement.get("id").asLong());
        }
        plane(); plane();
    }

    @AfterEach
    void removeOwnedRows() {
        if (client != null) client.close();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            for (long id : crews) {
                jdbc.update("delete from duty_assignment where crew_id=?", id);
                jdbc.update("delete from crew_unavailability where crew_id=?", id);
                jdbc.update("delete from crew_qualification where crew_id=?", id);
            }
            for (long id : flights) {
                jdbc.update("delete from flight where id=?", id);
                jdbc.update("delete from flight_audit where flight_id=?", id);
            }
            for (long id : planes) jdbc.update("delete from aircraft where id=?", id);
            for (long id : crews) jdbc.update("delete from crew where id=?", id);
        });
    }

    @Test
    void normalOperationsPersistCompleteQualifiedRosterAndDerivedDutyWindows() throws Exception {
        long flight = flight(0, "2026-12-20T05:00:00Z", "2026-12-20T07:00:00Z");
        staff(flight);
        JsonNode coverage = call("GET", "/flights/" + flight + "/coverage", null, 200);
        assertTrue(coverage.get("minimumStaffingSatisfied").asBoolean());
        assertTrue(coverage.get("policyNotice").asString().contains("Illustrative"));
        assertEquals(4, call("GET", "/flights/" + flight + "/assignments", null, 200).get("totalElements").asInt());
        for (long crew : crews) {
            assertEquals(1, call("GET", "/crew/" + crew + "/assignments?activeOnly=true", null, 200).get("totalElements").asInt());
            assertEquals(1, call("GET", "/crew/" + crew + "/assignments", null, 200).get("totalElements").asInt());
        }
        assertEquals(4, jdbc.queryForObject("""
                select count(*) from duty_assignment d join crew c on c.id=d.crew_id
                join flight f on f.id=d.flight_id join aircraft a on a.id=f.aircraft_id
                join qualification q on q.aircraft_type_id=a.aircraft_type_id and q.required_role=d.assigned_role
                join crew_qualification cq on cq.crew_id=c.id and cq.qualification_id=q.id
                where f.id=? and c.role=d.assigned_role and d.status='ACTIVE'
                and d.duty_start_time=f.departure_time-interval '1 hour'
                and d.duty_end_time=f.arrival_time+interval '30 minutes'
                and cq.valid_until >= (f.departure_time at time zone 'UTC')::date
                """, Integer.class, flight));
    }

    @Test
    void invalidOperationsLeaveSafeCommittedState() throws Exception {
        long pilot = crew("PILOT", true), second = crew("PILOT", true), copilot = crew("COPILOT", true);
        long missing = crew("PILOT", false);
        JsonNode originalCrew = call("GET", "/crew/" + pilot, null, 200);
        call("POST", "/crew", Map.of("name", "Duplicate", "role", "PILOT", "licenseNo", originalCrew.get("licenseNo").asString(),
                "experienceYears", 3, "contactNo", "9000000000"), 409);
        JsonNode plane = call("GET", "/aircraft/" + planes.getFirst(), null, 200);
        call("POST", "/aircraft", Map.of("registration", plane.get("registration").asString(), "aircraftTypeId", type), 409);
        Map<String, Object> invalid = flightBody(0, "2026-12-20T05:00:00Z", "2026-12-20T07:00:00Z");
        invalid.put("sourceAirportId", Long.MAX_VALUE);
        call("POST", "/flights", invalid, 404);
        long flight = flight(0, "2026-12-20T05:00:00Z", "2026-12-20T07:00:00Z");
        call("POST", "/flights", flightBody(0, "2026-12-20T06:00:00Z", "2026-12-20T08:00:00Z"), 409);
        reject(copilot, flight, "PILOT", "ROLE_MISMATCH");
        reject(missing, flight, "PILOT", "MISSING_QUALIFICATION");
        expiry(second, "2026-12-19", 200);
        reject(second, flight, "PILOT", "QUALIFICATION_EXPIRED");
        expiry(pilot, "2026-12-20", 200); // Inclusive UTC departure date.
        assign(pilot, flight, "PILOT");
        reject(pilot, flight, "PILOT", "DUPLICATE_ACTIVE_ASSIGNMENT");
        expiry(second, "2027-12-31", 200);
        reject(second, flight, "PILOT", "FLIGHT_ROLE_FILLED");
        assign(copilot, flight, "COPILOT");
        reject(crew("COPILOT", true), flight, "COPILOT", "FLIGHT_ROLE_FILLED");
        call("PATCH", "/crew/" + pilot + "/activity", Map.of("active", false), 409);
        call("PATCH", "/aircraft/" + planes.getFirst() + "/activity", Map.of("active", false), 409);
        call("DELETE", "/crew/" + pilot + "/qualifications/" + requirements.get("PILOT"), null, 409);
        expiry(pilot, "2026-12-19", 409);
        call("PUT", "/flights/" + flight, flightBody(0, "2026-12-21T05:00:00Z", "2026-12-21T07:00:00Z"), 409);
        call("PUT", "/flights/" + flight + "/cancellation", Map.of(), 409);
        call("PATCH", "/flights/" + flight + "/status", Map.of("status", "COMPLETED"), 409);
        assertEquals(2, jdbc.queryForObject("select count(*) from duty_assignment where flight_id=? and status='ACTIVE'", Integer.class, flight));
        assertEquals("2026-12-20T05:00:00Z", call("GET", "/flights/" + flight, null, 200).get("departureTime").asString());
    }

    @Test
    void rollingLimitsRestHalfOpenBoundariesAndMidnightUnavailabilityUseHttp() throws Exception {
        long pilot = crew("PILOT", true);
        long first = flight(0, "2026-12-20T01:00:00Z", "2026-12-20T05:30:00Z"); // Duty 00-06.
        assign(pilot, first, "PILOT");
        reject(pilot, flight(1, "2026-12-20T05:00:00Z", "2026-12-20T07:00:00Z"), "PILOT", "CREW_TIME_CONFLICT");
        long adjacent = flight(0, "2026-12-20T07:00:00Z", "2026-12-20T09:00:00Z"); // Duty starts 06.
        JsonNode error = reject(pilot, adjacent, "PILOT", "INSUFFICIENT_REST");
        assertFalse(error.get("eligibility").get("reasons").toString().contains("CREW_TIME_CONFLICT"));
        reject(pilot, flight(0, "2026-12-20T17:00:00Z", "2026-12-20T21:30:00Z"), "PILOT", "ROLLING_24_HOUR_LIMIT");
        long midnightPilot = crew("PILOT", true);
        long midnight = flight(0, "2026-12-21T23:00:00Z", "2026-12-22T01:00:00Z");
        JsonNode unavailable = call("POST", "/crew/" + midnightPilot + "/unavailability", Map.of(
                "startTime", "2026-12-22T00:00:00Z", "endTime", "2026-12-22T00:30:00Z", "reason", "Verification"), 201);
        reject(midnightPilot, midnight, "PILOT", "CREW_UNAVAILABLE");
        call("DELETE", "/crew/" + midnightPilot + "/unavailability/" + unavailable.get("id").asLong(), null, 204);
        JsonNode assigned = assign(midnightPilot, midnight, "PILOT");
        assertEquals("2026-12-21T22:00:00Z", assigned.get("dutyStartTime").asString());
        assertEquals("2026-12-22T01:30:00Z", assigned.get("dutyEndTime").asString());
        long weekly = crew("PILOT", true);
        for (int day = 23; day <= 27; day++) {
            assign(weekly, flight(0, "2026-12-" + day + "T01:00:00Z", "2026-12-" + day + "T07:30:00Z"), "PILOT");
        }
        reject(weekly, flight(0, "2026-12-28T01:00:00Z", "2026-12-28T07:30:00Z"), "PILOT", "ROLLING_7_DAY_LIMIT");
        assertEquals(5, call("GET", "/crew/" + weekly + "/assignments?activeOnly=true", null, 200).get("totalElements").asInt());
    }

    @Test
    void assignmentCancellationReassignmentAndFlightCancellationPreserveHistory() throws Exception {
        long pilot = crew("PILOT", true);
        long flight = flight(0, "2026-12-20T05:00:00Z", "2026-12-20T07:00:00Z");
        long original = assign(pilot, flight, "PILOT").get("id").asLong();
        for (int attempt = 0; attempt < 2; attempt++) {
            assertEquals("CANCELLED", call("PUT", "/assignments/" + original + "/cancellation", Map.of(), 200).get("status").asString());
        }
        assertEquals(0, call("GET", "/flights/" + flight + "/assignments", null, 200).get("totalElements").asInt());
        assertEquals(1, call("GET", "/flights/" + flight + "/coverage", null, 200).get("roles").get(0).get("missing").asInt());
        long replacement = call("POST", "/assignments/" + original + "/reassignments", Map.of(), 201).get("id").asLong();
        assertNotEquals(original, replacement);
        reject(pilot, flight, "PILOT", "DUPLICATE_ACTIVE_ASSIGNMENT");
        assertEquals(2, call("GET", "/crew/" + pilot + "/assignments", null, 200).get("totalElements").asInt());
        assertEquals(1, call("GET", "/crew/" + pilot + "/assignments?activeOnly=true", null, 200).get("totalElements").asInt());
        call("PUT", "/assignments/" + replacement + "/cancellation", Map.of(), 200);
        call("PUT", "/flights/" + flight + "/cancellation", Map.of(), 200);
        call("POST", "/assignments/" + original + "/reassignments", Map.of(), 409);
        assertEquals(2, jdbc.queryForObject("select count(*) from duty_assignment where flight_id=? and status='CANCELLED'", Integer.class, flight));
    }

    @Test
    void completedAndCancelledFlightTransitionsHaveTransactionalAuditEvidence() throws Exception {
        long past = flight(0, "2026-11-17T05:00:00Z", "2026-11-17T07:00:00Z");
        staff(past);
        assertEquals("COMPLETED", call("PATCH", "/flights/" + past + "/status", Map.of("status", "COMPLETED"), 200).get("status").asString());
        call("PATCH", "/flights/" + past + "/status", Map.of("status", "SCHEDULED"), 409);
        call("PUT", "/flights/" + past + "/cancellation", Map.of(), 409);
        call("PUT", "/flights/" + past, flightBody(0, "2026-11-17T06:00:00Z", "2026-11-17T08:00:00Z"), 409);
        long cancelled = flight(0, "2026-12-20T05:00:00Z", "2026-12-20T07:00:00Z");
        call("PUT", "/flights/" + cancelled + "/cancellation", Map.of(), 200);
        call("PUT", "/flights/" + cancelled + "/cancellation", Map.of(), 200);
        call("PATCH", "/flights/" + cancelled + "/status", Map.of("status", "COMPLETED"), 409);
        for (long flight : List.of(past, cancelled)) {
            assertEquals(List.of("INSERT", "UPDATE"), jdbc.queryForList(
                    "select action from flight_audit where flight_id=? order by id", String.class, flight));
        }
        assertEquals(4, call("GET", "/flights/" + past + "/assignments?activeOnly=false", null, 200).get("totalElements").asInt());
    }

    private void staff(long flight) throws Exception {
        for (String role : List.of("PILOT", "COPILOT", "CABIN_CREW", "CABIN_CREW")) {
            long crew = crew(role, true);
            assertTrue(call("POST", "/assignments/eligibility", assignment(crew, flight, role), 200).get("eligible").asBoolean());
            assign(crew, flight, role);
        }
    }

    private long crew(String role, boolean qualified) throws Exception {
        long id = call("POST", "/crew", Map.of("name", "Verification " + role, "role", role, "licenseNo", prefix + (++serial),
                "experienceYears", 3, "contactNo", "9000000000"), 201).get("id").asLong();
        crews.add(id);
        if (qualified) call("POST", "/crew/" + id + "/qualifications", Map.of("qualificationId", requirements.get(role), "validUntil", "2027-12-31"), 201);
        return id;
    }

    private void plane() throws Exception {
        planes.add(call("POST", "/aircraft", Map.of("registration", prefix + (++serial), "aircraftTypeId", type), 201).get("id").asLong());
    }

    private Map<String, Object> flightBody(int plane, String departure, String arrival) {
        return new HashMap<>(Map.of("flightNumber", prefix + (++serial), "aircraftId", planes.get(plane),
                "sourceAirportId", source, "destinationAirportId", destination, "departureTime", departure, "arrivalTime", arrival));
    }

    private long flight(int plane, String departure, String arrival) throws Exception {
        long id = call("POST", "/flights", flightBody(plane, departure, arrival), 201).get("id").asLong();
        flights.add(id);
        return id;
    }

    private Map<String, Object> assignment(long crew, long flight, String role) {
        return Map.of("crewId", crew, "flightId", flight, "assignedRole", role);
    }

    private JsonNode assign(long crew, long flight, String role) throws Exception {
        return call("POST", "/assignments", assignment(crew, flight, role), 201);
    }

    private JsonNode reject(long crew, long flight, String role, String reason) throws Exception {
        JsonNode error = call("POST", "/assignments", assignment(crew, flight, role), 409);
        assertEquals("ASSIGNMENT_REJECTED", error.get("code").asString());
        assertTrue(error.get("eligibility").get("reasons").toString().contains(reason), error.toString());
        return error;
    }

    private void expiry(long crew, String date, int status) throws Exception {
        call("PUT", "/crew/" + crew + "/qualifications/" + requirements.get("PILOT") + "/expiry", Map.of("validUntil", date), status);
    }

    private JsonNode call(String method, String path, Object body, int expected) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + API + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json").header("Accept", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, response.statusCode(), method + " " + path + ": " + response.body());
        if (expected == 201) assertTrue(response.headers().firstValue("Location").orElseThrow().startsWith(API + "/"));
        return response.body().isBlank() ? null : json.readTree(response.body());
    }
}
