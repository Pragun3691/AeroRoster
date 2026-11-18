package com.aeroroster.web.api;

import com.aeroroster.domain.DutyWindow;
import com.aeroroster.domain.scheduling.*;
import com.aeroroster.persistence.entity.*;
import com.aeroroster.service.dto.*;
import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import com.aeroroster.web.api.dto.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@PostgresTest
@Import(FixedClockConfiguration.class)
class OpenApiContractTest {
    @Autowired JsonMapper json;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    private JsonNode spec;

    @BeforeEach
    void readSpecification() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/static/api/v1/openapi.json")) {
            assertNotNull(input);
            spec = json.readTree(input);
        }
    }

    @Test
    void everyControllerMethodAndPathHasExactlyOneDocumentedOperation() throws Exception {
        int actual = 0;
        String docs = Files.readString(Path.of("docs/09_PHASE24_REST_API.md"));
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getPackageName().equals("com.aeroroster.web.api")) continue;
            for (String fullPath : entry.getKey().getPatternValues()) {
                for (var method : entry.getKey().getMethodsCondition().getMethods()) {
                    String path = fullPath.substring("/api/v1".length());
                    assertTrue(spec.get("paths").has(path), method + " " + path);
                    assertTrue(spec.get("paths").get(path).has(method.name().toLowerCase()), method + " " + path);
                    assertTrue(docs.contains(method.name() + " " + fullPath), "Endpoint missing from handoff table");
                    actual++;
                }
            }
        }
        int documented = 0;
        for (JsonNode path : spec.get("paths")) documented += path.size();
        assertEquals(actual, documented);
        assertEquals(42, actual);
    }

    @Test
    void documentedRecordFieldsMatchActualJsonContracts() {
        for (Class<?> type : List.of(CrewRequest.class, AircraftRequest.class, FlightRequest.class,
                AssignmentRequest.class, QualificationGrantRequest.class, ExpiryUpdateRequest.class,
                ActivityRequest.class, FlightStatusRequest.class, UnavailabilityRequest.class, EmptyRequest.class,
                CrewView.class, AircraftView.class, AirportView.class, AircraftTypeView.class,
                QualificationView.class, CrewQualificationView.class, FlightView.class, AssignmentView.class,
                UnavailabilityView.class, UnavailabilityConflictView.class, EligibilityDecision.class,
                FlightCrewCoverage.class, RoleCoverage.class, DutyWindow.class, DutyLoad.class,
                SchedulingPolicy.class, SchedulingReason.class, ApiError.class, ApiFieldError.class, PageResponse.class)) {
            JsonNode properties = spec.get("components").get("schemas").get(type.getSimpleName()).get("properties");
            assertEquals(type.getRecordComponents().length, properties.size(), type.getSimpleName());
            for (var field : type.getRecordComponents()) {
                assertTrue(properties.has(field.getName()), type.getSimpleName() + "." + field.getName());
                assertFalse(field.getType().isAnnotationPresent(jakarta.persistence.Entity.class));
                if (field.getType() == int.class) {
                    assertEquals("int32", properties.get(field.getName()).get("format").asString(),
                            type.getSimpleName() + "." + field.getName());
                }
            }
        }
    }

    @Test
    void enumValuesAndAllSchemaReferencesResolve() {
        for (Class<? extends Enum<?>> type : List.of(CrewRole.class, FlightStatus.class,
                AssignmentStatus.class, SchedulingReasonCode.class)) {
            JsonNode values = spec.get("components").get("schemas").get(type.getSimpleName()).get("enum");
            assertEquals(type.getEnumConstants().length, values.size());
            for (int index = 0; index < values.size(); index++) {
                assertEquals(type.getEnumConstants()[index].name(), values.get(index).asString());
            }
        }
        verifyReferences(spec);
    }

    private void verifyReferences(JsonNode node) {
        if (node.has("$ref")) {
            String reference = node.get("$ref").asString();
            assertTrue(reference.startsWith("#/"));
            assertFalse(spec.at(reference.substring(1)).isMissingNode(), reference);
        }
        node.forEach(this::verifyReferences);
    }
}
