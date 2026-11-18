package com.aeroroster.web.api;

import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@PostgresTest
@Import(FixedClockConfiguration.class)
class ApiPaginationBoundaryTest {
    @Autowired WebApplicationContext context;
    @Autowired JsonMapper json;
    private MockMvc http;

    @BeforeEach
    void initialize() {
        http = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void offsetBeyondJpaIntegerRangeIsAClientParameterError() throws Exception {
        for (int page : new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE / 200 + 1}) {
            var response = http.perform(MockMvcRequestBuilders.get("/api/v1/crew")
                    .param("page", Integer.toString(page)).param("size", "200")).andReturn().getResponse();
            assertEquals(400, response.getStatus(), response.getContentAsString());
            var error = json.readTree(response.getContentAsString());
            assertEquals("INVALID_PARAMETER", error.get("code").asString());
            assertEquals("page", error.get("fieldErrors").get(0).get("field").asString());
        }
    }

    @Test
    void largestRepresentableOffsetsReturnEmptyStablePages() throws Exception {
        for (int[] paging : new int[][] {{Integer.MAX_VALUE, 1}, {Integer.MAX_VALUE / 200, 200}}) {
            var response = http.perform(MockMvcRequestBuilders.get("/api/v1/crew")
                    .param("page", Integer.toString(paging[0])).param("size", Integer.toString(paging[1])))
                    .andReturn().getResponse();
            assertEquals(200, response.getStatus(), response.getContentAsString());
            var page = json.readTree(response.getContentAsString());
            assertEquals(0, page.get("items").size());
            assertEquals(paging[0], page.get("page").asInt());
            assertTrue(page.get("last").asBoolean());
        }
    }
}
