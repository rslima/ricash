package com.rslima.ricash.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ricash.security.jwt")
public record JwtClaimProperties(
        String principalClaimName,
        String authoritiesClaimName,
        String authorityPrefix,
        String audience
) {
}
