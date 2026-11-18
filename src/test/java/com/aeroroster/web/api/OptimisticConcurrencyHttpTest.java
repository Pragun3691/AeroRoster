package com.aeroroster.web.api;

import com.aeroroster.persistence.entity.Crew;
import com.aeroroster.persistence.repository.CrewRepository;
import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
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

@PostgresTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FixedClockConfiguration.class)
class OptimisticConcurrencyHttpTest {
    @Value("${local.server.port}") int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired CrewRepository crewRepository;
    @Autowired EntityManagerFactory emf;
    private HttpClient client;
    private ExecutorService workers;
    private final List<Long> crews = new ArrayList<>(), planes = new ArrayList<>(), flights = new ArrayList<>();
    private long crew, plane, flight, qualification, type;
    private String tag;

    @BeforeEach
    void fixtures() throws Exception {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        workers = Executors.newFixedThreadPool(2);
        tag = ("V26" + UUID.randomUUID().toString().substring(0, 7)).toUpperCase(Locale.ROOT);
        type = jdbc.queryForObject("select id from aircraft_type where model='Airbus A320'", Long.class);
        qualification = jdbc.queryForObject("select id from qualification where aircraft_type_id=? and required_role='PILOT'", Long.class, type);
        crew = call("POST", "/crew", crewBody("Original pilot"), 201, null).get("id").asLong();
        crews.add(crew);
        call("POST", "/crew/" + crew + "/qualifications", Map.of("qualificationId", qualification, "validUntil", "2027-12-31"), 201, null);
        plane = call("POST", "/aircraft", Map.of("registration", tag, "aircraftTypeId", type), 201, null).get("id").asLong();
        planes.add(plane);
        long source = jdbc.queryForObject("select id from airport where iata_code='DEL'", Long.class);
        long destination = jdbc.queryForObject("select id from airport where iata_code='BOM'", Long.class);
        flight = call("POST", "/flights", Map.of("flightNumber", tag, "aircraftId", plane,
                "sourceAirportId", source, "destinationAirportId", destination,
                "departureTime", "2026-12-29T05:00:00Z", "arrivalTime", "2026-12-29T07:00:00Z"), 201, null).get("id").asLong();
        flights.add(flight);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (workers != null) {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(25, TimeUnit.SECONDS));
        }
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
    void twoClientsCannotOverwriteCrewAircraftOrFlightEdits() throws Exception {
        String crewPath = "/crew/" + crew;
        JsonNode before = get(crewPath);
        JsonNode after = call("PUT", crewPath, crewBody("First administrator"), 200, token(before));
        stale("PUT", crewPath, crewBody("Stale administrator"), token(before));
        assertEquals("First administrator", get(crewPath).get("name").asString());
        assertEquals(before.get("version").asLong() + 1, after.get("version").asLong());

        String planePath = "/aircraft/" + plane;
        before = get(planePath);
        call("PUT", planePath, Map.of("registration", tag + "A", "aircraftTypeId", type), 200, token(before));
        stale("PUT", planePath, Map.of("registration", tag + "B", "aircraftTypeId", type), token(before));
        assertEquals(tag + "A", get(planePath).get("registration").asString());

        String flightPath = "/flights/" + flight;
        before = get(flightPath);
        Map<String, Object> edit = flightBody(before);
        edit.put("flightNumber", tag + "A");
        after = call("PUT", flightPath, edit, 200, token(before));
        edit.put("flightNumber", tag + "B");
        stale("PUT", flightPath, edit, token(before));
        assertEquals(tag + "A", get(flightPath).get("flightNumber").asString());
        assertEquals(before.get("version").asLong() + 1, after.get("version").asLong());
        assertEquals(List.of("INSERT", "UPDATE"), jdbc.queryForList("select action from flight_audit where flight_id=? order by id", String.class, flight));
    }

    @Test
    void activityStatusAndFlightCancellationRejectStaleCommandsBeforeStateChanges() throws Exception {
        String crewPath = "/crew/" + crew;
        JsonNode before = get(crewPath);
        call("PATCH", crewPath + "/activity", Map.of("active", false), 200, token(before));
        stale("PATCH", crewPath + "/activity", Map.of("active", true), token(before));
        assertFalse(get(crewPath).get("active").asBoolean());

        String planePath = "/aircraft/" + plane;
        before = get(planePath);
        call("PUT", planePath, Map.of("registration", tag + "A", "aircraftTypeId", type), 200, token(before));
        stale("PATCH", planePath + "/activity", Map.of("active", false), token(before));
        assertTrue(get(planePath).get("active").asBoolean());

        String path = "/flights/" + flight;
        before = get(path);
        JsonNode cancelled = call("PUT", path + "/cancellation", Map.of(), 200, token(before));
        stale("PUT", path + "/cancellation", Map.of(), token(before));
        stale("PATCH", path + "/status", Map.of("status", "SCHEDULED"), token(before));
        JsonNode noOp = call("PUT", path + "/cancellation", Map.of(), 200, token(cancelled));
        assertEquals(cancelled.get("version"), noOp.get("version"));
        assertEquals("CANCELLED", get(path).get("status").asString());
        assertEquals(2, jdbc.queryForObject("select count(*) from flight_audit where flight_id=?", Integer.class, flight));
    }

    @Test
    void qualificationExpiryRevocationAndRegrantProtectOriginalRowIdentity() throws Exception {
        String path = "/crew/" + crew + "/qualifications/" + qualification;
        JsonNode before = get(path);
        JsonNode renewed = call("PUT", path + "/expiry", Map.of("validUntil", "2028-12-31"), 200, token(before));
        stale("DELETE", path, null, token(before));
        stale("PUT", path + "/expiry", Map.of("validUntil", "2026-12-01"), token(before));
        assertEquals("2028-12-31", get(path).get("validUntil").asString());
        call("DELETE", path, null, 204, token(renewed));
        JsonNode recreated = call("POST", "/crew/" + crew + "/qualifications",
                Map.of("qualificationId", qualification, "validUntil", "2027-12-31"), 201, null);
        assertNotEquals(before.get("id"), recreated.get("id"));
        assertEquals(0, recreated.get("version").asLong());
        stale("DELETE", path, null, token(before));
        stale("PUT", path + "/expiry", Map.of("validUntil", "2028-12-31"), token(before));
        assertEquals(recreated.get("id"), get(path).get("id"));
        assertEquals(1, jdbc.queryForObject("select count(*) from crew_qualification where crew_id=?", Integer.class, crew));
    }

    @Test
    void unavailabilityReplacementAndDeletionCannotDiscardNewerChanges() throws Exception {
        String base = "/crew/" + crew + "/unavailability";
        Map<String, Object> edit = new HashMap<>(Map.of("startTime", "2027-01-01T00:00:00Z",
                "endTime", "2027-01-01T02:00:00Z", "reason", "Original"));
        JsonNode before = call("POST", base, edit, 201, null);
        String path = base + "/" + before.get("id").asLong();
        edit.put("reason", "Approved revision");
        JsonNode after = call("PUT", path, edit, 200, token(before));
        edit.put("reason", "Stale revision");
        stale("PUT", path, edit, token(before));
        stale("DELETE", path, null, token(before));
        assertEquals("Approved revision", get(path).get("reason").asString());
        call("DELETE", path, null, 204, token(after));
        call("GET", path, null, 404, null);
    }

    @Test
    void assignmentCancellationAndReassignmentRequireOriginalEditVersion() throws Exception {
        JsonNode before = call("POST", "/assignments", Map.of("crewId", crew, "flightId", flight, "assignedRole", "PILOT"), 201, null);
        String path = "/assignments/" + before.get("id").asLong();
        call("PUT", path + "/cancellation", Map.of(), 428, null);
        JsonNode cancelled = call("PUT", path + "/cancellation", Map.of(), 200, token(before));
        stale("PUT", path + "/cancellation", Map.of(), token(before));
        stale("POST", path + "/reassignments", Map.of(), token(before));
        assertEquals(cancelled.get("version"), call("PUT", path + "/cancellation", Map.of(), 200, token(cancelled)).get("version"));
        JsonNode replacement = call("POST", path + "/reassignments", Map.of(), 201, token(cancelled));
        assertNotEquals(before.get("id"), replacement.get("id"));
        assertEquals(1, jdbc.queryForObject("select count(*) from duty_assignment where crew_id=? and status='ACTIVE'", Integer.class, crew));
        assertEquals(2, jdbc.queryForObject("select count(*) from duty_assignment where crew_id=?", Integer.class, crew));
    }

    @Test
    void missingMalformedWrongIdentityAndMassAssignedVersionsCannotWrite() throws Exception {
        String path = "/crew/" + crew;
        JsonNode before = get(path);
        assertEquals("PRECONDITION_REQUIRED", call("PUT", path, crewBody("No header"), 428, null).get("code").asString());
        for (String header : List.of("0", "*", "-1:0", crew + ":-1", crew + ":1,2", crew + ":9223372036854775808")) {
            assertEquals("INVALID_PARAMETER", call("PUT", path, crewBody("Bad header"), 400, header).get("code").asString());
        }
        stale("PUT", path, crewBody("Wrong resource"), (crew + 1) + ":0");
        Map<String, Object> managed = new HashMap<>(crewBody("Managed version"));
        managed.put("version", 0);
        call("PUT", path, managed, 400, token(before));
        JsonNode noOp = call("PUT", path, crewBody("Original pilot"), 200, token(before));
        assertEquals(before.get("version"), noOp.get("version"));
        assertEquals("Original pilot", get(path).get("name").asString());
        assertTrue(call("GET", "/crew?q=" + tag, null, 200, null).get("items").get(0).has("version"));
    }

    @Test
    void simultaneousConditionalEditsCommitExactlyOneWinner() throws Exception {
        String path = "/crew/" + crew;
        JsonNode before = get(path);
        CountDownLatch started = new CountDownLatch(2);
        List<Future<HttpResponse<String>>> pending = new TransactionTemplate(manager).execute(status -> {
            crewRepository.findForUpdate(crew).orElseThrow();
            List<Future<HttpResponse<String>>> requests = new ArrayList<>();
            for (String name : List.of("Administrator A", "Administrator B")) {
                requests.add(workers.submit(() -> {
                    started.countDown();
                    return send("PUT", path, crewBody(name), token(before));
                }));
            }
            try { assertTrue(started.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
            awaitLockWaiters();
            assertTrue(requests.stream().noneMatch(Future::isDone));
            return requests;
        });
        List<HttpResponse<String>> responses = List.of(pending.get(0).get(20, TimeUnit.SECONDS), pending.get(1).get(20, TimeUnit.SECONDS));
        assertEquals(List.of(200, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
        JsonNode winner = json.readTree(responses.stream().filter(r -> r.statusCode() == 200).findFirst().orElseThrow().body());
        JsonNode loser = json.readTree(responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow().body());
        assertEquals("STALE_VERSION", loser.get("code").asString());
        assertEquals(winner.get("name"), get(path).get("name"));
        assertEquals(before.get("version").asLong() + 1, get(path).get("version").asLong());
    }

    @Test
    void jpaVersionAlsoRejectsStaleEntityFlushWithoutServiceParentLock() throws Exception {
        var first = emf.createEntityManager();
        var second = emf.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Crew one = first.find(Crew.class, crew);
            Crew two = second.find(Crew.class, crew);
            one.setName("Committed JPA edit");
            first.getTransaction().commit();
            two.setName("Stale JPA edit");
            assertThrows(OptimisticLockException.class, second::flush);
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close(); second.close();
        }
        assertEquals("Committed JPA edit", get("/crew/" + crew).get("name").asString());
        assertEquals(1, get("/crew/" + crew).get("version").asLong());
    }

    private void awaitLockWaiters() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            jdbc.execute("select pg_stat_clear_snapshot()");
            if (jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%from crew %'", Integer.class) >= 2) return;
            try { Thread.sleep(50); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
        }
        fail("Both independent HTTP edits must wait on the held crew lock");
    }

    private Map<String, Object> crewBody(String name) {
        return Map.of("name", name, "role", "PILOT", "licenseNo", tag, "experienceYears", 3, "contactNo", "9000000000");
    }

    private Map<String, Object> flightBody(JsonNode current) {
        return new HashMap<>(Map.of("flightNumber", current.get("flightNumber").asString(), "sourceAirportId", current.get("sourceAirportId").asLong(),
                "destinationAirportId", current.get("destinationAirportId").asLong(), "aircraftId", plane,
                "departureTime", current.get("departureTime").asString(), "arrivalTime", current.get("arrivalTime").asString()));
    }

    private String token(JsonNode snapshot) {
        return snapshot.get("id").asLong() + ":" + snapshot.get("version").asLong();
    }

    private JsonNode get(String path) throws Exception { return call("GET", path, null, 200, null); }

    private void stale(String method, String path, Object body, String token) throws Exception {
        assertEquals("STALE_VERSION", call(method, path, body, 409, token).get("code").asString());
    }

    private JsonNode call(String method, String path, Object body, int status, String token) throws Exception {
        HttpResponse<String> response = send(method, path, body, token);
        assertEquals(status, response.statusCode(), method + " " + path + ": " + response.body());
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, Object body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (token != null) request.header("X-Expected-Version", token);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
}
