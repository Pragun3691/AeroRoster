package com.aeroroster.web.api;

import com.aeroroster.persistence.entity.CrewRole;
import com.aeroroster.persistence.repository.*;
import com.aeroroster.service.dto.CrewRequest;
import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@PostgresTest
@Import(FixedClockConfiguration.class)
@Transactional
class ApiIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired ApiCorsFilter cors;
    @Autowired JsonMapper json;
    @Autowired CrewRepository crew;
    @Autowired AircraftRepository aircraft;
    @Autowired AircraftTypeRepository types;
    @Autowired AirportRepository airports;
    @Autowired QualificationRepository qualifications;
    @Autowired JdbcTemplate jdbc;
    private MockMvc http;

    @BeforeEach
    void initializeHttp() {
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(cors).build();
    }

    private JsonNode call(String method, String path, Object body, int status) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path).accept(MediaType.APPLICATION_JSON);
        if (com.aeroroster.support.EditTokens.required(method, path)) {
            var current = http.perform(MockMvcRequestBuilders.get(com.aeroroster.support.EditTokens.resource(path)))
                    .andReturn().getResponse();
            JsonNode snapshot = json.readTree(current.getContentAsString());
            request.header("X-Expected-Version", current.getStatus() == 200 && snapshot.has("version")
                    ? snapshot.get("id").asLong() + ":" + snapshot.get("version").asLong() : "1:0");
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String text ? text : json.writeValueAsString(body));
        }
        MvcResult result = http.perform(request).andReturn();
        assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String content = result.getResponse().getContentAsString();
        return content.isEmpty() ? null : json.readTree(content);
    }

    private long member(String license) {
        return crew.findByLicenseNo(license).orElseThrow().getId();
    }

    private long plane(String registration) {
        return aircraft.findByRegistration(registration).orElseThrow().getId();
    }

    private long type() {
        return types.findByModel("Airbus A320").orElseThrow().getId();
    }

    private long requirement(CrewRole role) {
        return qualifications.findByAircraftTypeIdAndRequiredRole(type(), role).orElseThrow().getId();
    }

    private Map<String, Object> flightBody(String number, long plane, String departure, String arrival) {
        return Map.of("flightNumber", number, "aircraftId", plane,
                "sourceAirportId", airports.findByIataCode("DEL").orElseThrow().getId(),
                "destinationAirportId", airports.findByIataCode("BOM").orElseThrow().getId(),
                "departureTime", departure, "arrivalTime", arrival);
    }

    private JsonNode flight() throws Exception {
        return call("POST", "/api/v1/flights", flightBody("A24-FLIGHT", plane("VT-AR101"),
                "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z"), 201);
    }

    private Map<String, Object> assignment(long flight, String license, CrewRole role) {
        return Map.of("crewId", member(license), "flightId", flight, "assignedRole", role.name());
    }

    @Test
    void crewWorkflowSupportsLocationsPagingUpdateAndActivity() throws Exception {
        CrewRequest body = new CrewRequest(" API Pilot ", CrewRole.PILOT, " a24-pilot ", 3, "9000000000");
        MvcResult created = http.perform(MockMvcRequestBuilders.post("/api/v1/crew").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn();
        assertEquals(201, created.getResponse().getStatus());
        JsonNode member = json.readTree(created.getResponse().getContentAsString());
        long id = member.get("id").asLong();
        assertEquals("/api/v1/crew/" + id, created.getResponse().getHeader("Location"));
        assertEquals("A24-PILOT", member.get("licenseNo").asString());
        assertEquals(id, call("GET", "/api/v1/crew/" + id, null, 200).get("id").asLong());
        JsonNode updated = call("PUT", "/api/v1/crew/" + id, new CrewRequest("Updated Pilot", CrewRole.PILOT, "A24-PILOT", 4, "9000000000"), 200);
        assertEquals(4, updated.get("experienceYears").asInt());
        assertFalse(call("PATCH", "/api/v1/crew/" + id + "/activity", "{\"active\":false}", 200).get("active").asBoolean());
        JsonNode page = call("GET", "/api/v1/crew?q=A24&active=false&role=PILOT&size=1", null, 200);
        assertEquals(1, page.get("totalElements").asLong());
        assertEquals(1, page.get("items").size());
        assertTrue(call("PATCH", "/api/v1/crew/" + id + "/activity", "{\"active\":true}", 200).get("active").asBoolean());
        call("POST", "/api/v1/crew", body, 409);
        assertEquals(1, jdbc.queryForObject("select count(*) from crew where license_no = 'A24-PILOT'", Integer.class));
    }

    @Test
    void validationErrorsHaveStableSafeFields() throws Exception {
        JsonNode error = call("POST", "/api/v1/crew", "{\"name\":\" \",\"role\":\"PILOT\",\"licenseNo\":\"A24-BAD\",\"experienceYears\":-1,\"contactNo\":\"bad\"}", 400);
        assertEquals("VALIDATION_ERROR", error.get("code").asString());
        assertEquals("/api/v1/crew", error.get("path").asString());
        assertEquals("2026-11-18T12:00:00Z", error.get("timestamp").asString());
        assertTrue(error.get("fieldErrors").size() >= 3);
        assertFalse(error.has("stackTrace"));
        assertFalse(error.has("exception"));
        assertEquals(0, jdbc.queryForObject("select count(*) from crew where license_no='A24-BAD'", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{", "null", "[]",
        "{\"name\":\"Pilot\",\"role\":\"pilot\",\"licenseNo\":\"A24-X\",\"experienceYears\":1,\"contactNo\":\"9000000000\"}",
        "{\"name\":\"Pilot\",\"role\":0,\"licenseNo\":\"A24-X\",\"experienceYears\":1,\"contactNo\":\"9000000000\"}",
        "{\"name\":\"Pilot\",\"role\":\"PILOT\",\"licenseNo\":\"A24-X\",\"experienceYears\":\"1\",\"contactNo\":\"9000000000\"}",
        "{\"name\":\"Pilot\",\"role\":\"PILOT\",\"licenseNo\":\"A24-X\",\"experienceYears\":1.5,\"contactNo\":\"9000000000\"}",
        "{\"name\":\"Pilot\",\"role\":\"PILOT\",\"licenseNo\":\"A24-X\",\"experienceYears\":1,\"contactNo\":\"9000000000\",\"active\":false}",
        "{\"name\":\"Pilot\",\"role\":\"PILOT\",\"licenseNo\":\"A24-X\",\"experienceYears\":1,\"contactNo\":\"9000000000\",\"id\":1}",
        "{} {}"
    })
    void malformedAndMassAssignmentRequestsAreRejected(String body) throws Exception {
        assertEquals("MALFORMED_JSON", call("POST", "/api/v1/crew", body, 400).get("code").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidPathIdsReturn400(String id) throws Exception {
        JsonNode error = call("GET", "/api/v1/crew/" + id, null, 400);
        assertTrue(error.get("fieldErrors").size() > 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=201", "?size=abc", "?role=pilot", "?role=NOT_A_ROLE", "?active=yes"})
    void invalidQueryParametersReturn400(String query) throws Exception {
        assertTrue(call("GET", "/api/v1/crew" + query, null, 400).get("fieldErrors").size() > 0);
    }

    @Test
    void missingResourcesAndUnknownRoutesReturn404() throws Exception {
        assertEquals("NOT_FOUND", call("GET", "/api/v1/crew/9223372036854775807", null, 404).get("code").asString());
        assertEquals("NOT_FOUND", call("GET", "/api/v1/not-a-resource", null, 404).get("code").asString());
    }

    @Test
    void referenceEndpointsAndAircraftWorkflow() throws Exception {
        assertEquals(5, call("GET", "/api/v1/airports", null, 200).size());
        assertEquals(1, call("GET", "/api/v1/airports?q=DEL", null, 200).size());
        long airport = airports.findByIataCode("DEL").orElseThrow().getId();
        assertEquals("DEL", call("GET", "/api/v1/airports/" + airport, null, 200).get("iataCode").asString());
        assertEquals(2, call("GET", "/api/v1/aircraft-types", null, 200).size());
        assertEquals(2, call("GET", "/api/v1/aircraft-types/" + type(), null, 200).get("minCabinCrew").asInt());
        assertEquals(3, call("GET", "/api/v1/aircraft-types/" + type() + "/qualifications", null, 200).size());
        assertEquals(6, call("GET", "/api/v1/qualifications", null, 200).size());
        Map<String, Object> body = Map.of("registration", "A24-PLANE", "aircraftTypeId", type());
        long id = call("POST", "/api/v1/aircraft", body, 201).get("id").asLong();
        call("POST", "/api/v1/aircraft", body, 409);
        call("PUT", "/api/v1/aircraft/" + id, Map.of("registration", "A24-EDIT", "aircraftTypeId", type()), 200);
        call("PATCH", "/api/v1/aircraft/" + id + "/activity", "{\"active\":false}", 200);
        assertFalse(call("GET", "/api/v1/aircraft/" + id, null, 200).get("active").asBoolean());
        assertEquals(1, call("GET", "/api/v1/aircraft?q=A24&active=false&size=1", null, 200).get("totalElements").asInt());
        call("POST", "/api/v1/flights", flightBody("A24-INACTIVE", id, "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z"), 409);
        call("POST", "/api/v1/aircraft", Map.of("registration", "A24-MISSING", "aircraftTypeId", Long.MAX_VALUE), 404);
    }

    @Test
    void qualificationGrantExpiryRevocationAndSafety() throws Exception {
        long id = call("POST", "/api/v1/crew", new CrewRequest("API Pilot", CrewRole.PILOT, "A24-QUAL", 1, "9000000000"), 201).get("id").asLong();
        long requirement = requirement(CrewRole.PILOT);
        String base = "/api/v1/crew/" + id + "/qualifications";
        call("POST", base, Map.of("qualificationId", requirement, "validUntil", "2027-12-31"), 201);
        assertEquals(id, call("GET", base + "/" + requirement, null, 200).get("crewId").asLong());
        call("POST", base, Map.of("qualificationId", requirement), 409);
        call("PUT", base + "/" + requirement + "/expiry", "{}", 400);
        assertTrue(call("PUT", base + "/" + requirement + "/expiry", "{\"validUntil\":null}", 200).get("validUntil").isNull());
        call("PUT", base + "/" + requirement + "/expiry", "{\"validUntil\":\"2028-01-01\"}", 200);
        assertEquals(1, call("GET", base, null, 200).size());
        call("DELETE", base + "/" + requirement, null, 204);
        call("GET", base + "/" + requirement, null, 404);
        call("POST", base, Map.of("qualificationId", requirement(CrewRole.COPILOT)), 409);
        call("POST", base, Map.of("qualificationId", Long.MAX_VALUE), 404);
        long seeded = member("DEMO-P-001");
        call("DELETE", "/api/v1/crew/" + seeded + "/qualifications/" + requirement, null, 409);
        call("PUT", "/api/v1/crew/" + seeded + "/qualifications/" + requirement + "/expiry", "{\"validUntil\":\"2026-11-30\"}", 409);
    }

    @Test
    void flightWorkflowUsesOffsetsPagingAndConflictRules() throws Exception {
        long id = call("POST", "/api/v1/flights", flightBody("A24-OFFSET", plane("VT-AR101"), "2026-12-10T10:30:00+05:30", "2026-12-10T12:30:00+05:30"), 201).get("id").asLong();
        JsonNode result = call("GET", "/api/v1/flights/" + id, null, 200);
        assertEquals("2026-12-10T05:00:00Z", result.get("departureTime").asString());
        call("POST", "/api/v1/flights", flightBody("A24-OFFSET", plane("VT-AR102"), "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z"), 409);
        call("POST", "/api/v1/flights", flightBody("A24-OVERLAP", plane("VT-AR101"), "2026-12-10T06:00:00Z", "2026-12-10T08:00:00Z"), 409);
        call("PUT", "/api/v1/flights/" + id, flightBody("A24-EDIT", plane("VT-AR101"), "2026-12-10T06:00:00Z", "2026-12-10T08:00:00Z"), 200);
        assertEquals(1, call("GET", "/api/v1/flights?flightNumber=A24&status=SCHEDULED&size=1", null, 200).get("totalElements").asInt());
        assertEquals(1, call("GET", "/api/v1/aircraft/" + plane("VT-AR101") + "/flights?size=1", null, 200).get("items").size());
        call("PATCH", "/api/v1/flights/" + id + "/status", "{\"status\":\"COMPLETED\"}", 409);
        call("PUT", "/api/v1/flights/" + id + "/cancellation", null, 200);
        call("PATCH", "/api/v1/flights/" + id + "/status", "{\"status\":\"SCHEDULED\"}", 409);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-12-10T05:00:00", "not-a-date", "2026-02-30T05:00:00Z"})
    void malformedTimestampStringsReturn400(String departure) throws Exception {
        call("POST", "/api/v1/flights", flightBody("A24-TIME", plane("VT-AR101"), departure, "2026-12-10T07:00:00Z"), 400);
        assertEquals(0, jdbc.queryForObject("select count(*) from flight where flight_number='A24-TIME'", Integer.class));
    }

    @Test
    void numericAndArrayTimestampsAndSubMicrosecondWritesAreRejected() throws Exception {
        var body = new java.util.HashMap<>(flightBody("A24-TIME", plane("VT-AR101"), "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z"));
        body.put("departureTime", 1796880000L);
        call("POST", "/api/v1/flights", body, 400);
        body.put("departureTime", List.of(2026, 12, 10));
        call("POST", "/api/v1/flights", body, 400);
        body.put("departureTime", "2026-12-10T05:00:00.000000001Z");
        call("POST", "/api/v1/flights", body, 400);
        call("GET", "/api/v1/flights?departureFrom=invalid", null, 400);
    }

    @Test
    void assignmentWorkflowReturnsReasonCodesCoverageAndHistory() throws Exception {
        long flight = flight().get("id").asLong();
        Map<String, Object> pilot = assignment(flight, "DEMO-P-002", CrewRole.PILOT);
        var managedFields = new java.util.HashMap<>(pilot);
        managedFields.put("dutyStartTime", "2026-12-10T05:00:00Z");
        call("POST", "/api/v1/assignments", managedFields, 400);
        JsonNode preview = call("POST", "/api/v1/assignments/eligibility", pilot, 200);
        assertTrue(preview.get("eligible").asBoolean());
        assertEquals("PT10H", preview.get("policy").get("maximumRolling24Hours").asString());
        long assignment = call("POST", "/api/v1/assignments", pilot, 201).get("id").asLong();
        assertEquals("ACTIVE", call("GET", "/api/v1/assignments/" + assignment, null, 200).get("status").asString());
        JsonNode rejected = call("POST", "/api/v1/assignments", pilot, 409);
        assertEquals("ASSIGNMENT_REJECTED", rejected.get("code").asString());
        assertTrue(rejected.get("eligibility").get("reasons").toString().contains("DUPLICATE_ACTIVE_ASSIGNMENT"));
        assertTrue(rejected.get("eligibility").get("reasons").get(0).has("explanation"));
        assertFalse(call("GET", "/api/v1/flights/" + flight + "/coverage", null, 200).get("minimumStaffingSatisfied").asBoolean());
        for (var entry : Map.of("DEMO-C-001", CrewRole.COPILOT, "DEMO-F-001", CrewRole.CABIN_CREW, "DEMO-F-002", CrewRole.CABIN_CREW).entrySet()) {
            call("POST", "/api/v1/assignments", assignment(flight, entry.getKey(), entry.getValue()), 201);
        }
        assertTrue(call("GET", "/api/v1/flights/" + flight + "/coverage", null, 200).get("minimumStaffingSatisfied").asBoolean());
        call("PUT", "/api/v1/flights/" + flight + "/cancellation", null, 409);
        call("PUT", "/api/v1/assignments/" + assignment + "/cancellation", "{\"crewId\":1}", 400);
        assertEquals("ACTIVE", call("GET", "/api/v1/assignments/" + assignment, null, 200).get("status").asString());
        call("PUT", "/api/v1/assignments/" + assignment + "/cancellation", null, 200);
        long replacement = call("POST", "/api/v1/assignments/" + assignment + "/reassignments", null, 201).get("id").asLong();
        assertNotEquals(assignment, replacement);
        assertEquals("CANCELLED", call("GET", "/api/v1/assignments/" + assignment, null, 200).get("status").asString());
        assertEquals(5, call("GET", "/api/v1/flights/" + flight + "/assignments?activeOnly=false&size=1", null, 200).get("totalElements").asInt());
        assertEquals(4, call("GET", "/api/v1/flights/" + flight + "/assignments", null, 200).get("totalElements").asInt());
        assertEquals(2, call("GET", "/api/v1/crew/" + member("DEMO-P-002") + "/assignments", null, 200).get("totalElements").asInt());
        assertEquals(1, call("GET", "/api/v1/crew/" + member("DEMO-P-002") + "/assignments?activeOnly=true", null, 200).get("totalElements").asInt());
        assertEquals(4, jdbc.queryForObject("select count(*) from duty_assignment where flight_id=? and status='ACTIVE'", Integer.class, flight));
    }

    @Test
    void eligibilityPreviewDoesNotAuthorizeLaterAssignment() throws Exception {
        long flight = flight().get("id").asLong();
        var request = assignment(flight, "DEMO-P-002", CrewRole.PILOT);
        assertTrue(call("POST", "/api/v1/assignments/eligibility", request, 200).get("eligible").asBoolean());
        call("PATCH", "/api/v1/crew/" + member("DEMO-P-002") + "/activity", "{\"active\":false}", 200);
        JsonNode error = call("POST", "/api/v1/assignments", request, 409);
        assertTrue(error.get("eligibility").get("reasons").toString().contains("CREW_INACTIVE"));
        assertEquals(0, jdbc.queryForObject("select count(*) from duty_assignment where flight_id=?", Integer.class, flight));
    }

    @Test
    void unavailabilityWorkflowProtectsActiveDutiesAndOwnership() throws Exception {
        long member = member("DEMO-P-002");
        String base = "/api/v1/crew/" + member + "/unavailability";
        var body = Map.of("startTime", "2026-12-10T04:00:00Z", "endTime", "2026-12-10T08:00:00Z", "reason", "Training");
        long period = call("POST", base, body, 201).get("id").asLong();
        assertEquals("Training", call("GET", base + "/" + period, null, 200).get("reason").asString());
        call("PUT", base + "/" + period, Map.of("startTime", "2026-12-10T04:00:00Z", "endTime", "2026-12-10T08:00:00Z", "reason", "Updated"), 200);
        assertEquals(1, call("GET", base + "?size=1", null, 200).get("totalElements").asInt());
        assertTrue(call("GET", base + "/conflicts?start=2026-12-10T05:00:00Z&end=2026-12-10T06:00:00Z", null, 200).get("conflicts").asBoolean());
        call("POST", base, body, 409);
        call("DELETE", "/api/v1/crew/" + member("DEMO-P-001") + "/unavailability/" + period, null, 404);
        long flight = flight().get("id").asLong();
        assertTrue(call("POST", "/api/v1/assignments/eligibility", assignment(flight, "DEMO-P-002", CrewRole.PILOT), 200).get("reasons").toString().contains("CREW_UNAVAILABLE"));
        call("DELETE", base + "/" + period, null, 204);
        call("POST", "/api/v1/assignments", assignment(flight, "DEMO-P-002", CrewRole.PILOT), 201);
        call("POST", base, body, 409);
        assertEquals(0, call("GET", base, null, 200).get("totalElements").asInt());
    }

    @Test
    void corsAndHttpProtocolErrorsUseSafeResponses() throws Exception {
        MvcResult same = http.perform(MockMvcRequestBuilders.get("/api/v1/crew").header("Origin", "http://localhost")).andReturn();
        assertEquals(200, same.getResponse().getStatus());
        assertNull(same.getResponse().getHeader("Access-Control-Allow-Origin"));
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.OPTIONS)) {
            MvcResult foreign = http.perform(MockMvcRequestBuilders.request(method, "/api/v1/crew").header("Origin", "https://foreign.example")
                    .header("Access-Control-Request-Method", "POST")).andReturn();
            assertEquals(403, foreign.getResponse().getStatus());
            assertEquals("ORIGIN_NOT_ALLOWED", json.readTree(foreign.getResponse().getContentAsString()).get("code").asString());
            assertNull(foreign.getResponse().getHeader("Access-Control-Allow-Origin"));
        }
        assertEquals("METHOD_NOT_ALLOWED", call("DELETE", "/api/v1/crew/1", null, 405).get("code").asString());
        MvcResult unsupported = http.perform(MockMvcRequestBuilders.post("/api/v1/crew").contentType(MediaType.TEXT_PLAIN).content("body")).andReturn();
        assertEquals(415, unsupported.getResponse().getStatus());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", json.readTree(unsupported.getResponse().getContentAsString()).get("code").asString());
        MvcResult unacceptable = http.perform(MockMvcRequestBuilders.get("/api/v1/crew").accept(MediaType.APPLICATION_XML)).andReturn();
        assertEquals(406, unacceptable.getResponse().getStatus());
        assertEquals("NOT_ACCEPTABLE", json.readTree(unacceptable.getResponse().getContentAsString()).get("code").asString());
        MvcResult foreignWrite = http.perform(MockMvcRequestBuilders.post("/api/v1/crew").header("Origin", "https://foreign.example")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        new CrewRequest("Foreign request", CrewRole.PILOT, "A24-FOREIGN", 1, "9000000000")))).andReturn();
        assertEquals(403, foreignWrite.getResponse().getStatus());
        assertEquals(0, jdbc.queryForObject("select count(*) from crew where license_no='A24-FOREIGN'", Integer.class));
    }

    @Test
    void offsetQueryFiltersAndInvalidReferencesAreHandled() throws Exception {
        flight();
        MvcResult result = http.perform(MockMvcRequestBuilders.get("/api/v1/flights")
                .param("flightNumber", "A24").param("departureFrom", "2026-12-10T10:30:00+05:30")
                .param("departureUntil", "2026-12-10T10:30:01+05:30")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertEquals(1, json.readTree(result.getResponse().getContentAsString()).get("totalElements").asInt());
        var invalid = new java.util.HashMap<>(flightBody("A24-BAD-REF", plane("VT-AR101"), "2026-12-11T05:00:00Z", "2026-12-11T07:00:00Z"));
        invalid.put("sourceAirportId", Long.MAX_VALUE);
        call("POST", "/api/v1/flights", invalid, 404);
        assertEquals(0, jdbc.queryForObject("select count(*) from flight where flight_number='A24-BAD-REF'", Integer.class));
    }
}
