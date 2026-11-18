package com.aeroroster.web.api;

import com.aeroroster.service.CrewService;
import com.aeroroster.service.DutyAssignmentService;
import com.aeroroster.service.exception.PersistenceFailureException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiControllerTest {
    private CrewService crew;
    private MockMvc http;
    private JsonMapper json;

    @BeforeEach
    void initializeController() {
        crew = mock(CrewService.class);
        var builder = JsonMapper.builder();
        new ApiJsonConfiguration().strictApiJson().customize(builder);
        json = builder.build();
        var clock = Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC);
        http = MockMvcBuilders.standaloneSetup(new CrewController(crew, mock(DutyAssignmentService.class)))
                .setControllerAdvice(new ApiExceptionHandler(new ApiErrors(clock)))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json)).build();
    }

    @Test
    void unexpectedFailureDoesNotLeakInternalMessageOrStack() throws Exception {
        when(crew.get(1L)).thenThrow(new IllegalStateException("internal query details"));
        var response = http.perform(MockMvcRequestBuilders.get("/api/v1/crew/1")).andReturn().getResponse();
        assertEquals(500, response.getStatus());
        assertFalse(response.getContentAsString().contains("internal query details"));
        var error = json.readTree(response.getContentAsString());
        assertEquals("INTERNAL_ERROR", error.get("code").asString());
        assertEquals("The request could not be completed", error.get("message").asString());
        assertFalse(error.has("stackTrace"));
    }

    @Test
    void persistenceFailureReceivesSameSafe500Contract() throws Exception {
        when(crew.get(1L)).thenThrow(new PersistenceFailureException("database-specific diagnostic"));
        var response = http.perform(MockMvcRequestBuilders.get("/api/v1/crew/1")).andReturn().getResponse();
        assertEquals(500, response.getStatus());
        assertFalse(response.getContentAsString().contains("database-specific diagnostic"));
        assertEquals("INTERNAL_ERROR", json.readTree(response.getContentAsString()).get("code").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"active\":null}", "{\"active\":\"false\"}", "{\"active\":false,\"role\":\"PILOT\"}"})
    void activityRequiresAnExplicitBooleanAndCannotMassAssign(String body) throws Exception {
        var response = http.perform(MockMvcRequestBuilders.patch("/api/v1/crew/1/activity")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        assertEquals(400, response.getStatus());
        verifyNoInteractions(crew);
    }
}
