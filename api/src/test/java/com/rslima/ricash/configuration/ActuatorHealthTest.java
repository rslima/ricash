package com.rslima.ricash.configuration;

import com.rslima.ricash.TestRicashApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The platform health check and the liveness/readiness probes are reachable
 * without a token; nothing else under /actuator is.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRicashApplication.class)
class ActuatorHealthTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void livenessProbe_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void readinessProbe_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void healthComponents_stayBehindAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().isUnauthorized());
    }
}
