package com.rslima.ricash.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ricash.security.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
}
