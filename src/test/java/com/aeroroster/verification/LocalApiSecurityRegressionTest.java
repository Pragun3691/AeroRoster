package com.aeroroster.verification;

import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import com.aeroroster.web.api.ApiCorsFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@PostgresTest
@Import(FixedClockConfiguration.class)
@Transactional
class LocalApiSecurityRegressionTest {
    @Autowired WebApplicationContext context;
    @Autowired ApiCorsFilter cors;
    @Autowired JsonMapper json;
    private MockMvc http;

    @BeforeEach
    void initializeHttp() {
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(cors).build();
    }

    @Test
    void matchingForeignHostAndOriginCannotBypassLocalOnlyApiPolicy() throws Exception {
        var response = http.perform(MockMvcRequestBuilders.get("/api/v1/crew")
                .header("Host", "rebind.example").header("Origin", "http://rebind.example")
                .with(request -> {
                    request.setServerName("rebind.example");
                    request.setServerPort(80);
                    return request;
                })).andReturn().getResponse();
        assertEquals(403, response.getStatus());
        assertEquals("HOST_NOT_ALLOWED", json.readTree(response.getContentAsString()).get("code").asString());
    }

    @Test
    void foreignHostIsRejectedWhenSameOriginBrowserGetOmitsOrigin() throws Exception {
        var response = http.perform(MockMvcRequestBuilders.get("/api/v1/crew").header("Host", "rebind.example")
                .with(request -> {
                    request.setServerName("rebind.example");
                    return request;
                })).andReturn().getResponse();
        assertEquals(403, response.getStatus());
        assertEquals("HOST_NOT_ALLOWED", json.readTree(response.getContentAsString()).get("code").asString());
    }

    @Test
    void loopbackHostnamesRemainUsableWithoutOrigin() throws Exception {
        for (String host : java.util.List.of("localhost", "LOCALHOST", "127.0.0.1", "::1", "[::1]")) {
            var response = http.perform(MockMvcRequestBuilders.get("/api/v1/crew")
                    .with(request -> { request.setServerName(host); return request; })).andReturn().getResponse();
            assertEquals(200, response.getStatus(), host);
        }
    }

    @Test
    void duplicateJsonKeysDoNotSilentlyChooseTheLastRole() throws Exception {
        var response = http.perform(MockMvcRequestBuilders.post("/api/v1/crew").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Verification Pilot","role":"COPILOT","role":"PILOT",
                         "licenseNo":"V25-DUP","experienceYears":3,"contactNo":"9000000000"}
                        """)).andReturn().getResponse();
        assertEquals(400, response.getStatus());
        assertEquals("MALFORMED_JSON", json.readTree(response.getContentAsString()).get("code").asString());
    }
}
