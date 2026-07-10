package com.rslima.ricash.testsupport;

import com.rslima.ricash.configuration.CorsProperties;
import com.rslima.ricash.configuration.JwtClaimProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Security stubs for web-layer tests that don't need a database: a mocked
 * JwtDecoder plus the configuration property records SecurityConfiguration
 * depends on. Unlike TestRicashApplication, importing this does NOT start a
 * Postgres container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class WebTestConfiguration {

    @Bean
    JwtDecoder jwtDecoder() {
        return mock(JwtDecoder.class);
    }

    @Bean
    JwtClaimProperties jwtClaimProperties() {
        return new JwtClaimProperties("preferred_username", "realm_access/roles", "", "");
    }

    @Bean
    CorsProperties corsProperties() {
        return new CorsProperties(List.of("http://localhost:5173"));
    }

    /**
     * MockMvc post-processor authenticating the request as the given user.
     * Sets both the subject and the preferred_username claim so
     * JwtAuthenticationToken.getName() resolves to userId regardless of which
     * one the converter reads.
     */
    public static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(String userId) {
        return jwt().jwt(builder -> builder.subject(userId).claim("preferred_username", userId));
    }
}
