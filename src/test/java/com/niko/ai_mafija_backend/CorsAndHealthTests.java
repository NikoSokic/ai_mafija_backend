package com.niko.ai_mafija_backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Health check i CORS s više domena (APP_CORS_ALLOWED_ORIGINS odvojeno zarezom) za REST i /ws (SockJS). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.cors.allowed-origins=https://a.example.com, https://b.example.com")
class CorsAndHealthTests {

    private static final String ORIGIN_A = "https://a.example.com";
    private static final String ORIGIN_B = "https://b.example.com";
    private static final String OTHER = "https://evil.example.com";

    @Autowired private MockMvc mvc;

    @Test
    void healthReturnsOk() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void restCorsAllowsEveryListedOrigin() throws Exception {
        for (String origin : new String[]{ORIGIN_A, ORIGIN_B}) {
            mvc.perform(options("/api/health")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
    }

    @Test
    void restCorsRejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/api/health")
                        .header("Origin", OTHER)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void webSocketAllowsEveryListedOrigin() throws Exception {
        for (String origin : new String[]{ORIGIN_A, ORIGIN_B}) {
            mvc.perform(get("/ws/info").header("Origin", origin))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void webSocketRejectsUnknownOrigin() throws Exception {
        mvc.perform(get("/ws/info").header("Origin", OTHER))
                .andExpect(status().isForbidden());
    }
}
