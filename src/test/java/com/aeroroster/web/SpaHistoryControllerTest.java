package com.aeroroster.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpaHistoryControllerTest {
    private MockMvc http;

    @BeforeEach
    void initializeHttp() {
        http = MockMvcBuilders.standaloneSetup(new HomeController(), new SpaHistoryController()).build();
    }

    @Test
    void rootAndKnownBrowserRoutesForwardToTheSpaEntryPoint() throws Exception {
        for (String route : new String[] {"/", "/dashboard", "/crew", "/crew/101", "/aircraft", "/aircraft/201",
                "/flights", "/flights/301", "/flights/301/assign", "/qualifications", "/qualifications/501", "/assignments", "/assignments/701",
                "/unavailability", "/unavailability/801"}) {
            http.perform(MockMvcRequestBuilders.get(route)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void apiAndAssetLikePathsAreNotSpaFallbackRoutes() throws Exception {
        http.perform(MockMvcRequestBuilders.get("/api/v1/not-a-route")).andExpect(status().isNotFound());
        http.perform(MockMvcRequestBuilders.get("/assets/missing.js")).andExpect(status().isNotFound());
        http.perform(MockMvcRequestBuilders.get("/css/missing.css")).andExpect(status().isNotFound());
        http.perform(MockMvcRequestBuilders.get("/images/missing.png")).andExpect(status().isNotFound());
    }
}
